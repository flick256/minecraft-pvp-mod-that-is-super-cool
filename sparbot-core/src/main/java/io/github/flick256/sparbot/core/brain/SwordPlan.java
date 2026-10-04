package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * The thinking half of a sword fight in the attack-cooldown era: when to be inside the opponent's
 * reach and when not. Every hit resets the attacker's charge, so the fight is a game of timing and
 * distance:
 * <ul>
 *   <li>Ready to hit: commit, sprint in and hit (a sprinting full-charge hit knocks them back).</li>
 *   <li>Recharging after landing a hit (combo): W-tap or S-tap so the opponent flies to the edge of
 *       reach and the next hit lands there at full charge, out of reach of a trade.</li>
 *   <li>Recharging while the opponent is charged (read from how long ago their arm last swung): stay
 *       just outside their reach, dodge sideways, and sometimes step in and straight back out (a feint)
 *       to draw a swing.</li>
 *   <li>The opponent swung and missed (their arm swung and nothing hit the bot): rush in while they
 *       recharge (whiff punish).</li>
 *   <li>Crits are jumped for only when the charge will be full on the way down and the opponent can't
 *       punish the slow, unsprinted approach.</li>
 * </ul>
 * Each part is used as often as the profile's reading, feint, combo and crit skills say.
 */
final class SwordPlan {
	/** Vanilla entity interaction range: the opponent's reach. */
	private static final double OPPONENT_REACH = 3.0;
	/** Charged enough to fear: a swing above 0.9 charge is a full hit. */
	private static final double DANGEROUS_CHARGE = 0.85;
	/** After landing a hit, this many ticks count as a combo. */
	private static final int COMBO_TICKS = 15;
	/** A swing that hasn't hurt the bot within this many ticks missed. */
	private static final int WHIFF_TICKS = 2;
	private static final int PUNISH_TICKS = 8;
	private static final int FEINT_TICKS = 6;
	private static final int FEINT_COOLDOWN = 30;

	/** The movement keys the plan settles on (starts as the engage tactic's own choice). */
	static final class Move {
		int forward;
		int strafe;
		boolean sprint;
		/** Whether the plan took charge of the movement this tick. */
		boolean planned;

		Move(int forward, int strafe, boolean sprint) {
			this.forward = forward;
			this.strafe = strafe;
			this.sprint = sprint;
		}
	}

	private SwordPlan() {
	}

	/** Ticks until the bot's charge reaches the threshold it clicks at. */
	static int ticksToReady(BrainContext c) {
		DuelMemory m = c.memory;
		double s = c.self.attackStrength();
		return s >= m.cooldownThreshold ? 0 : (int) Math.ceil((m.cooldownThreshold - s) / Math.max(0.01, m.chargeRate));
	}

	/** How charged the opponent probably is: 1 unless the bot is reading their swings. */
	static double opponentCharge(BrainContext c) {
		TargetState t = c.target;
		if (t == null || !c.memory.reading || t.ticksSinceSwing() >= TargetState.NO_SWING) {
			return 1.0;
		}
		double chargeTicks = t.mainHand() == ItemKind.AXE ? 20.0 : t.mainHand() == ItemKind.MACE ? 33.3 : 12.5;
		return Math.min(1.0, (t.ticksSinceSwing() + 0.5) / chargeTicks);
	}

	/** Called once per tick before {@link #plan}: what the bot learns this tick about charge and swings. */
	static void observe(BrainContext c) {
		SelfState self = c.self;
		DuelMemory m = c.memory;
		if (m.observedTick == c.observation.tick()) {
			return; // once per tick
		}
		m.observedTick = c.observation.tick();
		float s = self.attackStrength();
		// A plausible one-tick rise (the fastest item, an empty hand, charges 0.2 a tick); not the first
		// sample or a jump after switching items.
		float rise = s - m.lastStrength;
		if (rise > 0 && rise <= 0.21F && s < 1.0F && m.lastStrength > 0) {
			m.chargeRate = rise;
		}
		m.lastStrength = s;
		m.reading = m.readDecision.get(c.rng, c.skill(Technique.READING), 40);
		if (m.sinceOwnHit < Integer.MAX_VALUE / 2) {
			m.sinceOwnHit++;
		}
		if (m.feintCooldown > 0) {
			m.feintCooldown--;
		}
		TargetState t = c.target;
		if (t == null) {
			return;
		}
		boolean newSwing = t.ticksSinceSwing() < m.lastOpponentSinceSwing;
		m.lastOpponentSinceSwing = t.ticksSinceSwing();
		boolean hurt = self.hurtTime() > m.lastSelfHurtTime;
		if (m.reading && newSwing) {
			m.whiffCheck = WHIFF_TICKS;
		} else if (m.whiffCheck > 0) {
			if (hurt) {
				m.whiffCheck = 0; // it connected
			} else if (--m.whiffCheck == 0) {
				m.punishTicks = PUNISH_TICKS; // it missed: their charge is gone
			}
		}
	}

	/** Adjusts the movement for the situation (see the class comment). */
	static void plan(BrainContext c, double distance, double reach, Move mv) {
		DuelMemory m = c.memory;
		int ready = ticksToReady(c);
		if (ready <= 1) {
			// Ready: go in and hit.
			return;
		}
		if (c.opponentHelpless() && c.rng.chance(c.skill(Technique.READING))) {
			// Eating, or stuck in a web out of reach: they can't hit back. Get in position for the next hit.
			set(mv, distance > reach - 0.4 ? 1 : 0, mv.strafe, distance > reach + 1.0);
			return;
		}
		if (m.punishTicks > 0) {
			m.punishTicks--;
			set(mv, 1, mv.strafe, true);
			return;
		}
		// There is no hit stun: chasing a charged opponent just walks into their hit. A player who reads
		// swings only chases while the opponent is recharging too.
		// A raised shield can't attack: nothing to stay away from.
		boolean opponentCharged = m.reading && opponentCharge(c) >= DANGEROUS_CHARGE && (c.target == null || !c.target.blocking());
		boolean comboing = m.sinceOwnHit < COMBO_TICKS && !opponentCharged && m.comboDecision.get(c.rng, c.skill(Technique.COMBOS), 30);
		if (comboing) {
			// Let them fly to the edge of reach, so the next hit lands there at full charge.
			double want = reach - 0.3;
			if (distance < want - 0.7) {
				set(mv, -1, mv.strafe, false);
			} else if (distance < want) {
				set(mv, 0, mv.strafe, false);
			} else {
				set(mv, 1, mv.strafe, true);
			}
			return;
		}
		if (!opponentCharged) {
			return;
		}
		// They can hit and the bot can't: don't stand in their reach.
		if (distance < OPPONENT_REACH + 0.3) {
			set(mv, -1, m.strafeDirection, false);
			return;
		}
		if (m.feintTicks == 0 && m.feintCooldown == 0 && distance < OPPONENT_REACH + 1.2 && m.feintDecision.get(c.rng, c.skill(Technique.FEINTS), 20)) {
			m.feintTicks = FEINT_TICKS;
			m.feintCooldown = FEINT_COOLDOWN;
		}
		if (m.feintTicks > 0) {
			// In for half the feint, then straight back out.
			m.feintTicks--;
			boolean in = m.feintTicks >= FEINT_TICKS / 2;
			set(mv, in ? 1 : -1, mv.strafe, in);
			return;
		}
		// Hover at the edge of their reach until the charge is back.
		set(mv, distance > OPPONENT_REACH + 0.8 ? 1 : 0, mv.strafe, false);
	}

	/**
	 * Whether now is a good moment to jump for a crit (vanilla crits need a full charge while falling and
	 * not sprinting, and do 1.5x damage):
	 * <ul>
	 *   <li>charged and a jump away: jump in and land the hit on the way down, so a trade goes the bot's way;</li>
	 *   <li>recharging up close: jump so the charge is full on the way down (about 6 ticks after the jump).</li>
	 * </ul>
	 * A player reading the opponent only does the slow, unsprinted second kind when they can't punish it.
	 */
	static boolean critOpportunity(BrainContext c, double distance, double reach) {
		int ready = ticksToReady(c);
		double skill = c.skill(Technique.CRITS);
		boolean approach = ready == 0 && distance > reach + 0.4 && distance < reach + 2.0;
		// A practised player times the jump; a novice jumps whenever.
		boolean timed = skill >= 0.5 ? ready >= 3 && ready <= 7 : c.self.attackStrength() >= 0.3;
		boolean safe = !c.memory.reading || opponentCharge(c) < 0.7 || c.target != null && c.target.hurtTime() > 0;
		boolean upClose = timed && safe && distance <= reach + 1.3;
		return (approach || upClose) && c.memory.critDecision.get(c.rng, skill, 20);
	}

	private static void set(Move mv, int forward, int strafe, boolean sprint) {
		mv.forward = forward;
		mv.strafe = strafe;
		mv.sprint = sprint;
		mv.planned = true;
	}
}
