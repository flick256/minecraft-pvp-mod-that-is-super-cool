package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.math.Vec3;

/** State the duel brain carries between ticks. Reset on respawn / new round. */
public final class DuelMemory {
	final Hands hands = new Hands();
	final Decision axeDecision = new Decision();
	final Decision blockHitDecision = new Decision();
	/** Whether the bot holds its swings while in front of a raised shield (decided per window). */
	final Decision respectShieldDecision = new Decision();
	/** Whether the bot stops strafing to take its shots (decided per window from bow skill). */
	final Decision plantsFeet = new Decision();
	// Sword plan (see SwordPlan).
	final Decision readDecision = new Decision();
	final Decision comboDecision = new Decision();
	final Decision feintDecision = new Decision();
	final Decision critDecision = new Decision();
	long observedTick = Long.MIN_VALUE;
	/** Reading the opponent's swings this window. */
	boolean reading;
	float lastStrength;
	/** How much the bot's attack charge grows per tick with the weapon in hand (measured). */
	double chargeRate = 0.08;
	int lastOpponentSinceSwing = io.github.flick256.sparbot.core.sense.TargetState.NO_SWING;
	/** Ticks left to see whether the opponent's swing connected. */
	int whiffCheck;
	/** Ticks left to rush in on an opponent whose swing missed. */
	int punishTicks;
	int feintTicks;
	int feintCooldown;
	/** Ticks since the bot last saw its own hit land. */
	int sinceOwnHit = Integer.MAX_VALUE / 2;
	boolean axeMode;
	int axeModeTicks;
	/** Ticks left to see whether the last axe swing brought the opponent's shield down. */
	int axeConfirmTicks;
	/** Ticks left to follow an axe hit on a raised shield (a shield stun) with the next weapon. */
	int stunTicks;
	/** The follow-up was planned before the axe hit, so the weapon switch is a single key press. */
	boolean stunPlanned;
	/** The opponent's shield was disabled by our axe this long ago counts down from 100 (5 s): no point axing it again. */
	int shieldDisabledTicks;
	/** How far the bot actually moved last tick (what a thrown or shot projectile inherits). */
	Vec3 selfMotion = Vec3.ZERO;
	Vec3 lastSelfPosition;
	/** Ticks spent pressing to move without moving (see Unstuck). */
	int stuckTicks;
	/** Ticks spent walking into a wall too high to jump; past a few, the bot sidesteps along it. */
	int blockedTicks;
	/** Ticks of sidestepping left after the wall ends, to clear its corner. */
	int detourTicks;
	int detourStrafe = 1;
	/** When the bot last finished putting up a wall to heal behind (the heal tactic eats right after). */
	long walledAt = Long.MIN_VALUE / 2;
	Mistake mistake = Mistake.NONE;
	int ticksUntilMistakeRoll;
	int strafeDirection = 1;
	int ticksUntilStrafeSwitch;
	boolean strafeActive;
	int wTapTicks;
	int sTapTicks;
	CritPhase critPhase = CritPhase.NONE;
	int critTicks;
	/** Reach misjudgement (blocks) for the next click; resampled after every click. */
	double reachError;
	/** Attack charge the bot waits for before its next click; resampled after every click. */
	double cooldownThreshold = 0.9;
	int lastSelfHurtTime;
	int lastTargetHurtTime;
	int ticksSinceOwnClick = Integer.MAX_VALUE / 2;
	int retreatTicks;
	int retreatCooldown;
	int reactionDelayTicks;
	int trackingDelayTicks;
	int ticksUntilReactionResample;

	enum CritPhase {
		NONE,
		/** Released forward to drop sprint, about to jump. */
		WINDUP,
		AIRBORNE
	}

	public String critPhaseName() {
		return critPhase.name();
	}

	public Mistake mistake() {
		return mistake;
	}
}
