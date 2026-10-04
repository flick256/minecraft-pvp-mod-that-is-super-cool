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
	private static final int STUN_WINDOW = 8;
	/** An axe disables a shield for 5 s (the axe's disable_blocking_for_seconds). */
	private static final int SHIELD_DISABLE_TICKS = 100;

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
		SkillProfile.Technique tech = c.profile.technique();
		DuelMemory m = c.memory;

		// Aim at the chest of the opponent as currently tracked, leading by a skill-dependent fraction
		// of how far it moves during the tracking delay (+1 tick for the time until the click lands).
		TargetState aimAt = c.seen();
		Vec3 lead = aimAt.velocity().scale(c.profile.aim().trackingLead() * (c.trackingDelayTicks + 1));
		Vec3 goal = aimAt.chest().add(lead);
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
			m.strafeActive = c.rng.chance(tech.strafeSkill());
		}
		if (distance < 5.0 && m.strafeActive) {
			strafe = m.strafeDirection;
		}

		// Spacing: good players back off slightly when pressed too close instead of face-hugging.
		if (!c.mistake(Mistake.OVERCHASE) && distance < 1.2 && c.rng.chance(tech.spacingSkill() * 0.5)) {
			forward = 0;
			sprint = false;
		}

		// React to our own landed hit (perceived via the target's hurt animation starting).
		boolean targetJustHurt = target.hurtTime() > m.lastTargetHurtTime && m.ticksSinceOwnClick < 6;
		if (targetJustHurt) {
			if (c.rng.chance(tech.wTapSkill())) {
				m.wTapTicks = c.rng.nextInt(2, 4);
			} else if (c.rng.chance(tech.sTapSkill())) {
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

		// Jump reset: jump the moment knockback lands to cancel part of it.
		boolean selfJustHurt = self.hurtTime() > m.lastSelfHurtTime;
		if (selfJustHurt && self.onGround() && c.rng.chance(tech.jumpResetSkill())) {
			jump = true;
		}

		// Crit attempt: drop sprint, jump, and hit on the way down with a full charge.
		updateCrit(c, distance, judgedReach);
		if (m.critPhase == DuelMemory.CritPhase.WINDUP) {
			forward = 0;
			sprint = false;
			jump = true;
		} else if (m.critPhase == DuelMemory.CritPhase.AIRBORNE) {
			sprint = false;
		}

		// Walk over 1-block obstacles like a player would.
		if (self.horizontalCollision() && self.onGround() && forward > 0) {
			jump = true;
		}

		// Weapon: the best sword, or an axe while the opponent holds a shield up (an axe hit disables a
		// raised shield for 5 s in 26.2). Switching takes the profile's hotbar time and resets the charge.
		InventoryState inv = self.inventory();
		TargetState seen = c.seen();
		if (m.shieldDisabledTicks > 0) {
			m.shieldDisabledTicks--;
		}
		// A shield our axe just disabled can't block, whatever it looks like.
		boolean shieldUp = seen != null && seen.blocking() && m.shieldDisabledTicks == 0;
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
		int press = stun && m.stunPlanned ? c.memory.hands.requestPlanned(c, weaponSlot) : c.memory.hands.request(c, weaponSlot);
		boolean armed = weaponSlot < 0 || inv.selectedSlot() == weaponSlot;

		// Facing a raised shield without an axe, a skilled player circles to its side instead of
		// wasting swings on it (a shield blocks hits within 90 degrees of where its holder faces).
		boolean inFrontOfShield = shieldUp && !m.axeMode && inFrontOf(seen, self.position());
		boolean holdFire = inFrontOfShield && c.rng.chance(c.profile.items().shieldSkill());
		if (inFrontOfShield) {
			strafe = m.strafeDirection;
		}

		boolean attack = armed && !inv.usingItem() && !holdFire && wantsToClick(c, distance, judgedReach);
		if (m.axeMode && armed && !inv.usingItem() && c.crosshairOnTarget(judgedReach + 0.5) && distance <= judgedReach
			&& self.attackStrength() >= AXE_MIN_CHARGE) {
			// Disabling the shield only needs the hit to land, not a full charge.
			attack = true;
			// Shield stun: the axe hit disables the shield for 5 s, so the next hit can't be blocked.
			// A skilled player has the follow-up ready: switch straight back and hit at once (any charge
			// still knocks them up, and a mace smash bonus isn't scaled by charge at all).
			m.axeMode = false;
			m.shieldDisabledTicks = SHIELD_DISABLE_TICKS;
			m.stunTicks = STUN_WINDOW;
			m.stunPlanned = c.rng.chance(c.profile.items().axeSkill());
		} else if (stun) {
			m.stunTicks--;
			if (armed && !inv.usingItem() && distance <= judgedReach && c.crosshairOnTarget(judgedReach + 0.5)) {
				attack = true;
				m.stunTicks = 0;
			}
		}

		// Block-hitting: with a shield in the offhand, raise it while the sword recharges and lower it in
		// time to swing (clicks are ignored while an item is in use, as in vanilla).
		boolean use = false;
		boolean shieldReady = inv.offhand().is(ItemKind.SHIELD) && !inv.offhand().onCooldown();
		boolean axeThreat = seen != null && seen.mainHand() == ItemKind.AXE && c.rng.chance(c.profile.items().shieldSkill());
		if (shieldReady && armed && !attack && !axeThreat && m.critPhase == DuelMemory.CritPhase.NONE && distance <= judgedReach + 1.0
			&& self.attackStrength() < m.cooldownThreshold - BLOCK_HIT_RELEASE_MARGIN
			&& m.blockHitDecision.get(c.rng, c.profile.items().shieldSkill(), 40)) {
			use = true;
			sprint = false;
		}
		if (attack) {
			m.ticksSinceOwnClick = 0;
			m.reachError = c.profile.reach().rangeErrorBlocks().sample(c.rng);
			m.cooldownThreshold = sampleCooldownThreshold(c);
		}

		Inputs inputs = new Inputs(look[0], look[1], forward, strafe, jump, false, sprint, attack, use, press);
		// Out of sight (behind a wall): find a way round to them.
		return target.visible() ? Movement.guardEdges(c, inputs) : Movement.navigate(c, inputs);
	}

	private void updateCrit(BrainContext c, double distance, double judgedReach) {
		DuelMemory m = c.memory;
		SelfState self = c.self;
		switch (m.critPhase) {
			case NONE -> {
				boolean nearlyCharged = self.attackStrength() >= 0.6;
				boolean closeEnough = distance <= judgedReach + 1.2;
				if (self.onGround() && nearlyCharged && closeEnough && !self.inWater() && c.rng.chance(c.profile.technique().critSkill() * 0.15)) {
					m.critPhase = DuelMemory.CritPhase.WINDUP;
					m.critTicks = 0;
				}
			}
			case WINDUP -> {
				if (!self.onGround()) {
					m.critPhase = DuelMemory.CritPhase.AIRBORNE;
				} else if (++m.critTicks > 3) {
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

	private boolean wantsToClick(BrainContext c, double distance, double judgedReach) {
		SelfState self = c.self;
		DuelMemory m = c.memory;
		if (!self.holdingMeleeWeapon()) {
			return false;
		}
		boolean aimed = c.crosshairOnTarget(judgedReach + 0.5);
		boolean inReach = distance <= judgedReach;
		boolean charged = self.attackStrength() >= m.cooldownThreshold;
		if (m.critPhase == DuelMemory.CritPhase.WINDUP) {
			return false;
		}
		if (m.critPhase == DuelMemory.CritPhase.AIRBORNE) {
			// Wait for the fall: vanilla only crits when fallDistance > 0 and charge > 0.9.
			boolean falling = self.velocity().y() < 0 && self.fallDistance() > 0;
			return falling && self.attackStrength() > FULL_CHARGE && aimed && inReach;
		}
		if (c.mistake(Mistake.WHIFF) && charged && distance < judgedReach + 2) {
			return true;
		}
		return aimed && inReach && charged;
	}

	/** Whether {@code point} is within the 90-degree half-angle a target's shield covers. */
	static boolean inFrontOf(TargetState t, Vec3 point) {
		float toPoint = Angles.yawTowards(t.position(), point);
		return Angles.yawDistance(toPoint, t.yaw()) <= 90.0F;
	}

	private static double sampleCooldownThreshold(BrainContext c) {
		double discipline = c.profile.clicking().cooldownDiscipline();
		double threshold = 0.3 + (FULL_CHARGE + 0.05 - 0.3) * discipline + c.rng.nextGaussian() * 0.05;
		return Math.max(0.2, Math.min(1.0, threshold));
	}
}
