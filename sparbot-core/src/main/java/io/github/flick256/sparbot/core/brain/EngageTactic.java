package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Melee duel behaviour for 26.2 combat (attack cooldown era):
 * close distance while sprinting, track the target, click when the charge reaches the bot's
 * threshold and the crosshair is on the target, strafe, and use crits, W-taps, S-taps and jump resets
 * with probabilities taken from the skill profile.
 *
 * <p>Vanilla facts this relies on (verified in 26.2 Player#attack): a crit needs attack charge
 * above 0.9, falling, not on ground and <b>not sprinting</b>; a sprinting full-charge hit adds +0.5
 * knockback and then stops the attacker's sprint.
 */
public final class EngageTactic implements Tactic {
	private static final double FULL_CHARGE = 0.9;
	/** Lower the block-hit shield this much attack charge before the planned swing. */
	private static final double BLOCK_HIT_RELEASE_MARGIN = 0.2;
	private static final double AXE_MIN_CHARGE = 0.25;
	/** How long after a shield stun the bot still goes for the immediate follow-up hit. */
	static final int STUN_WINDOW = 8;
	/** Ticks a raised shield takes before it blocks. */
	private static final int SHIELD_DELAY_TICKS = 5;
	/** A shield counter that draws no swing in this long is dropped (they read it), and not tried again for a while. */
	private static final int SHIELD_COUNTER_MAX = 20;
	private static final int SHIELD_COUNTER_COOLDOWN = 30;
	/** Ticks to see the opponent's shield come down after an axe swing (perception lags a tick or two). */
	static final int AXE_CONFIRM_TICKS = 4;

	@Override
	public String name() {
		return "engage";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		if (c.target == null || t == null || t.ticksSinceSeen() > 100) {
			return 0;
		}
		return t.visible() ? Scores.MELEE : Scores.MELEE - 0.2;
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		// Events (our hit landing) are noticed one reaction time late.
		TargetState target = c.target;
		DuelMemory m = c.memory;

		// Aim at the chest of the opponent as the bot sees them. A click lands on the body as the bot's client shows
		// it (see BrainContext#handLeadTicks), so the aim only leads by the hand's own lag, as much as skill manages.
		TargetState aimAt = c.seen();
		Vec3 lead = aimAt.velocity().scale(c.profile.aim().trackingLead() * BrainContext.HAND_LEAD_TICKS);
		Vec3 goal = c.wandered(SwightPlan.meleeAim(c, aimAt, lead));
		float goalYaw = Angles.yawTowards(self.eyePosition(), goal);
		float goalPitch = Angles.pitchTowards(self.eyePosition(), goal);
		float[] look = c.aim.step(self.yaw(), self.pitch(), goalYaw, goalPitch);

		double distance = c.targetDistance();
		double judgedReach = self.attackReach() + m.reachError;

		int forward = 1;
		int strafe = 0;
		boolean sprint = true;
		boolean jump = false;

		// Strafing: each strafe window (8-25 ticks) the bot decides whether to strafe, and which way.
		if (--m.ticksUntilStrafeSwitch <= 0) {
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 25);
			m.strafeActive = c.rng.chance(c.skill(Technique.STRAFE));
		}
		// Strafe between hits; a practised player steadies for the swing itself (a sidestep drags the aim off).
		boolean swingComing = SwordPlan.ticksToReady(c) <= 2 && distance <= self.attackReach() + 0.8;
		// A strafe is for the recharge, close in: sidestepping their swing while the bot can't swing itself. Walking
		// in sideways only slows the approach and drags its own aim (in the simulator a pro that strafes all the
		// time loses to one that doesn't; one that strafes like this doesn't).
		boolean recharging = SwordPlan.ticksToReady(c) > 3 && distance < 4.0;
		TargetState swinger = c.seen();
		if (recharging && swinger != null && swinger.ticksSinceSwing() == 0 && c.rng.chance(c.skill(Technique.STRAFE) * 0.5)) {
			// Their arm just swung: a practised player changes sides so the next one has to be re-aimed.
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 25);
		}
		if (m.strafeActive && recharging && !(swingComing && c.rng.chance(c.skill(Technique.SPACING)))) {
			strafe = m.strafeDirection;
		}

		// Spacing: good players back off slightly when pressed too close instead of face-hugging.
		if (!c.mistake(Mistake.OVERCHASE) && distance < 1.2 && c.rng.chance(c.skill(Technique.SPACING) * 0.5)) {
			forward = 0;
			sprint = false;
		}

		// React to our own landed hit (perceived via the target's hurt animation starting).
		// (The red flash shows up a reaction time after the click, so that is how far back the click may be.)
		boolean targetJustHurt = target.hurtTime() > m.lastTargetHurtTime && m.ticksSinceOwnClick < m.reactionDelayTicks + 4;
		if (targetJustHurt) {
			m.sinceOwnHit = 0;
		}
		SwordPlan.observe(c);
		if (targetJustHurt) {
			if (c.rng.chance(c.skill(Technique.W_TAP))) {
				m.wTapTicks = c.rng.nextInt(2, 4);
			} else if (c.rng.chance(c.skill(Technique.S_TAP))) {
				m.sTapTicks = c.rng.nextInt(2, 3);
			}
		}
		if (m.wTapTicks > 0) {
			m.wTapTicks--;
			forward = 0;
			sprint = false;
		} else if (m.sTapTicks > 0) {
			m.sTapTicks--;
			forward = -1;
			sprint = false;
		}

		// Timing and distance: combos, staying out of a charged opponent's reach, feints, whiff punishes.
		SwordPlan.Move mv = new SwordPlan.Move(forward, strafe, sprint);
		// (Not during a shield stun: the follow-up hit is the plan.)
		if (!c.mistake(Mistake.OVERCHASE) && m.stunTicks == 0) {
			SwordPlan.plan(c, distance, judgedReach, mv);
		}
		if (mv.planned) {
			forward = mv.forward;
			strafe = mv.strafe;
			sprint = mv.sprint;
			m.wTapTicks = 0;
			m.sTapTicks = 0;
		}

		// Jump reset: jump the moment knockback lands to cancel part of it.
		boolean selfJustHurt = self.hurtTime() > m.lastSelfHurtTime;
		// Hit while strafing: a player breaks the combo by switching sides, so the next hit has to be re-aimed.
		if (selfJustHurt && m.strafeActive && c.rng.chance(c.skill(Technique.STRAFE))) {
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 20);
			strafe = distance < 5.0 ? m.strafeDirection : 0;
		}
		if (selfJustHurt && self.onGround() && c.rng.chance(c.skill(Technique.JUMP_RESET))) {
			jump = true;
		}

		// Crit attempt: drop sprint, jump, and hit on the way down with a full charge.
		updateCrit(c, distance, judgedReach);
		if (m.critPhase == DuelMemory.CritPhase.WINDUP) {
			// Let go of W for a tick (that is what ends a sprint: a crit can't be a sprint hit), then jump walking
			// forward, so the jump carries in to the hit instead of stopping dead.
			sprint = false;
			if (m.critTicks == 0) {
				forward = 0;
			} else {
				forward = distance > judgedReach - 0.5 ? 1 : 0;
				jump = true;
			}
		} else if (m.critPhase == DuelMemory.CritPhase.AIRBORNE) {
			// In the air: keep drifting in to come down within reach (backing off mid-jump wastes the crit).
			sprint = false;
			forward = distance > judgedReach - 0.3 ? 1 : 0;
		}

		// Walk over 1-block obstacles like a player would.
		if (self.horizontalCollision() && self.onGround() && forward > 0) {
			jump = true;
		}

		// Weapon: the best sword, or an axe while the opponent holds a shield up (an axe hit disables a
		// raised shield for 5 s in 26.2). Switching takes the profile's hotbar time and resets the charge.
		InventoryState inv = self.inventory();
		// Tracking is for aiming. Recognising a shield or a weapon switch needs reaction time.
		TargetState seen = c.target;
		// A shield can also come down voluntarily: never ignore a visibly raised shield based
		// on an assumed five-second disable timer.
		boolean shieldUp = seen != null && seen.blocking();
		if (shieldUp) {
			m.stunTicks = 0;
		}
		// The axe swing may have missed: only a shield that actually came down was disabled.
		if (m.axeConfirmTicks > 0) {
			m.axeConfirmTicks--;
			if (seen != null && !seen.blocking() && c.observation.tick() - decisionDelay(c) >= m.axeClickAt) {
				m.axeConfirmTicks = 0;
				// Shield stun: the shield is down for 5 s, so the next hit can't be blocked. A skilled player
				// has the follow-up ready: switch straight back and hit at once (any charge still knocks them
				// up, and a mace smash bonus isn't scaled by charge at all).
				m.stunPlanned = c.rng.chance(c.skill(Technique.SHIELD_STUN));
				m.stunTicks = m.stunPlanned ? STUN_WINDOW : 0;
				m.axeMode = false;
				shieldUp = false;
			} else if (m.axeConfirmTicks == 0) {
				m.axeMode = shieldUp && axeSlotOf(self.inventory()) >= 0; // missed: go again
			}
		}
		int axeSlot = inv.bestHotbarWeapon(ItemKind.AXE);
		if (shieldUp && axeSlot >= 0 && !m.axeMode && m.axeDecision.get(c.rng, c.profile.items().axeSkill(), 30)) {
			m.axeMode = true;
			m.axeModeTicks = 0;
		}
		if (m.axeMode && (axeSlot < 0 || ++m.axeModeTicks > 10 && !shieldUp)) {
			m.axeMode = false;
		}
		int weaponSlot = m.axeMode ? axeSlot : Hands.preferredMeleeSlot(inv);
		boolean stun = m.stunTicks > 0;
		int press = stun && m.stunPlanned && !"swight".equals(c.profile.id()) ? c.memory.hands.requestPlanned(c, weaponSlot) : c.memory.hands.request(c, weaponSlot);
		boolean armed = weaponSlot < 0 || inv.selectedSlot() == weaponSlot;

		// Wait for the axe result from a usable distance. Continuing to run forward during
		// perception delay can put our eyes inside/past the opponent and lose the follow-up aim.
		if ((m.axeConfirmTicks > 0 || stun) && distance < judgedReach - 0.4) {
			forward = distance < 1.4 ? -1 : 0;
			strafe = 0;
			sprint = false;
		}

		// Facing a raised shield without an axe, a skilled player circles to its side instead of
		// wasting swings on it (a shield blocks hits within 90 degrees of where its holder faces).
		boolean inFrontOfShield = shieldUp && !m.axeMode && inFrontOf(seen, self.position());
		// (Decided per window, not per tick: a skilled player doesn't keep tapping a raised shield.)
		boolean holdFire = inFrontOfShield && m.respectShieldDecision.get(c.rng, c.profile.items().shieldSkill(), 20);
		if (inFrontOfShield) {
			strafe = m.strafeDirection;
		}

		boolean attack = armed && !inv.usingItem() && !holdFire && wantsToClick(c, distance, judgedReach);
		boolean disablingAxe = false;
		if (m.axeMode && m.axeConfirmTicks == 0 && armed && !inv.usingItem() && c.crosshairOnTarget(judgedReach + 0.5) && distance <= judgedReach
			&& self.attackStrength() >= AXE_MIN_CHARGE) {
			// Disabling the shield only needs the hit to land, not a full charge. Whether it did shows in the
			// next few ticks (see above).
			attack = true;
			disablingAxe = true;
		} else if (stun) {
			m.stunTicks--;
			// The second click of a stun: straight away with the axe still in hand (its knockback is the point,
			// and the switch would only cost time), or with the next weapon once it is in hand.
			boolean axeInHand = axeSlot >= 0 && inv.selectedSlot() == axeSlot && m.stunPlanned;
			if ((armed || axeInHand) && !inv.usingItem() && distance <= judgedReach && c.crosshairOnTarget(judgedReach + 0.5)) {
				attack = true;
				m.stunTicks = 0;
			}
		}

		// Block-hitting: with a shield in the offhand, raise it while the sword recharges and lower it in
		// time to swing (clicks are ignored while an item is in use, as in vanilla).
		boolean use = false;
		boolean shieldReady = inv.offhand().is(ItemKind.SHIELD) && !inv.offhand().onCooldown();
		boolean axeThreat = seen != null && seen.mainHand() == ItemKind.AXE
			&& m.avoidAxeDecision.get(c.rng, c.profile.items().shieldSkill(), 20);
		// A shield only blocks 0.25 s (5 ticks) after it goes up (BlocksAttacks block_delay_seconds), so it
		// is only worth raising with longer than that to wait; a player reading swings only raises it
		// while the opponent is charged (nothing to block otherwise).
		boolean worthBlocking = SwordPlan.ticksToReady(c) >= SHIELD_DELAY_TICKS + 2 && (!m.reading || SwordPlan.opponentCharge(c) >= 0.85);
		if (shieldReady && armed && !attack && !axeThreat && !stun && !m.axeMode && m.critPhase == DuelMemory.CritPhase.NONE && distance <= judgedReach + 1.0
			&& self.attackStrength() < m.cooldownThreshold - BLOCK_HIT_RELEASE_MARGIN && worthBlocking
			&& m.blockHitDecision.get(c.rng, c.skill(Technique.BLOCK_HIT), 40)) {
			use = true;
			sprint = false;
		}
		int counter = shieldCounter(c, armed && shieldReady, axeThreat || stun || m.axeMode || m.critPhase != DuelMemory.CritPhase.NONE, distance, judgedReach);
		if (counter != NO_COUNTER) {
			attack = false;
			use = true;
			sprint = false;
			forward = counter;
		}
		// A prepared stun is already a planned double click; ordinary swings have motor variance.
		attack = stun ? attack : SwingTiming.ready(c, attack);
		if (attack) {
			if (disablingAxe) {
				int delay = decisionDelay(c);
				m.axeClickAt = c.observation.tick();
				m.axeConfirmTicks = Math.max(AXE_CONFIRM_TICKS, delay + 2);
			}
			SwingTiming.clicked(c);
		}

		Inputs inputs = new Inputs(look[0], look[1], forward, strafe, jump, false, sprint, attack, use, press);
		// Out of sight (behind a wall): find a way round to them.
		return target.visible() ? Movement.guardEdges(c, inputs) : Movement.navigate(c, inputs);
	}

	/** Current delay, including focus: it may change while an axe confirmation is pending. */
	private static int decisionDelay(BrainContext c) {
		return c.memory.reactionDelayTicks + Math.max(0, c.trackingDelayTicks - c.memory.trackingDelayTicks);
	}

	private void updateCrit(BrainContext c, double distance, double judgedReach) {
		DuelMemory m = c.memory;
		SelfState self = c.self;
		switch (m.critPhase) {
			case NONE -> {
				if (self.onGround() && !self.inWater() && SwordPlan.critOpportunity(c, distance, judgedReach)) {
					m.critPhase = DuelMemory.CritPhase.WINDUP;
					m.critTicks = 0;
					m.critPatient = c.rng.chance(c.skill(Technique.CRITS));
				}
			}
			case WINDUP -> {
				if (!self.onGround()) {
					m.critPhase = DuelMemory.CritPhase.AIRBORNE;
				} else if (++m.critTicks > 4) {
					m.critPhase = DuelMemory.CritPhase.NONE;
				}
			}
			case AIRBORNE -> {
				if (self.onGround()) {
					m.critPhase = DuelMemory.CritPhase.NONE;
				}
			}
		}
	}

	/** {@link #shieldCounter} when the bot isn't shield-countering. */
	static final int NO_COUNTER = -2;

	/**
	 * Shield counter: a charged opponent with a sword about to come into reach. A shield player walks in
	 * behind the raised shield instead of trading: the opponent's swing lands on the shield (and still
	 * costs them their charge), then the shield comes down and the bot's hit goes in while they recharge.
	 * It is dropped as soon as they swing, after a second without a swing (they read it), or for an axe.
	 * In the simulator a pro with it beats a pro without block-hitting 71% of the time (54% with block-hitting
	 * alone).
	 *
	 * @param ready a weapon in hand and a usable shield in the offhand
	 * @param busy something else has the hands (an axe threat, a stun, a crit)
	 * @return the forward key while the shield is up, or {@link #NO_COUNTER}
	 */
	static int shieldCounter(BrainContext c, boolean ready, boolean busy, double distance, double judgedReach) {
		DuelMemory m = c.memory;
		TargetState seen = c.target;
		if (m.shieldCounterTicks > 0) {
			m.shieldCounterTicks++;
			boolean swung = seen != null && seen.ticksSinceSwing() < m.shieldCounterTicks;
			if (swung || m.shieldCounterTicks > SHIELD_COUNTER_MAX || !ready || busy || distance > judgedReach + 2.5) {
				m.shieldCounterTicks = 0;
				m.shieldCounterEndedAt = c.observation.tick();
				return NO_COUNTER;
			}
			return distance > judgedReach - 0.6 ? 1 : 0;
		}
		boolean opponentMelee = seen != null && (seen.mainHand() == ItemKind.SWORD || seen.mainHand() == ItemKind.MACE || seen.mainHand() == ItemKind.TRIDENT);
		if (ready && !busy && opponentMelee && c.allows(Technique.BLOCK_HIT) && c.observation.tick() - m.shieldCounterEndedAt > SHIELD_COUNTER_COOLDOWN
			&& distance > judgedReach && distance <= judgedReach + 1.5 && SwordPlan.opponentCharge(c) >= 0.9
			&& m.shieldCounterDecision.get(c.rng, c.skill(Technique.BLOCK_HIT), 30)) {
			m.shieldCounterTicks = 1;
			return 1;
		}
		return NO_COUNTER;
	}

	private boolean wantsToClick(BrainContext c, double distance, double judgedReach) {
		SelfState self = c.self;
		DuelMemory m = c.memory;
		if (!self.holdingMeleeWeapon()) {
			return false;
		}
		boolean aimed = c.clickHitsTarget(judgedReach + 0.5);
		boolean inReach = distance <= judgedReach;
		boolean charged = self.attackStrength() >= m.cooldownThreshold;
		if (m.critPhase == DuelMemory.CritPhase.WINDUP) {
			return false;
		}
		if (m.critPhase == DuelMemory.CritPhase.AIRBORNE) {
			if (!m.critPatient) {
				// An unpractised jump: the click goes in as soon as the charge is back, falling or not.
				return aimed && inReach && charged;
			}
			// Wait for the fall: vanilla only crits when fallDistance > 0 and charge > 0.9.
			boolean falling = self.velocity().y() < 0 && self.fallDistance() > 0;
			return falling && self.attackStrength() > FULL_CHARGE && aimed && inReach;
		}
		if (c.mistake(Mistake.WHIFF) && charged && distance < judgedReach + 2) {
			return true;
		}
		return aimed && inReach && charged;
	}

	private static int axeSlotOf(InventoryState inv) {
		return inv.bestHotbarWeapon(ItemKind.AXE);
	}

	/** Whether {@code point} is within the 90-degree half-angle a target's shield covers. */
	static boolean inFrontOf(TargetState t, Vec3 point) {
		float toPoint = Angles.yawTowards(t.position(), point);
		return Angles.yawDistance(toPoint, t.yaw()) <= 90.0F;
	}

	static double sampleCooldownThreshold(BrainContext c) {
		double discipline = c.profile.clicking().cooldownDiscipline();
		double threshold = 0.3 + (FULL_CHARGE + 0.05 - 0.3) * discipline + c.rng.nextGaussian() * 0.05;
		return Math.max(0.2, Math.min(1.0, threshold));
	}
}
