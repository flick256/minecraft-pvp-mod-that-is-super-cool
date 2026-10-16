package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.math.Vec3;

/** State the duel brain carries between ticks. Reset on respawn / new round. */
public final class DuelMemory {
	final Hands hands = new Hands();
	final Decision axeDecision = new Decision();
	final Decision blockHitDecision = new Decision();
	final Decision avoidAxeDecision = new Decision();
	/** Whether the bot holds its swings while in front of a raised shield (decided per window). */
	final Decision respectShieldDecision = new Decision();
	/** Shield counter: walking in behind the shield for the opponent to swing into (see EngageTactic). */
	final Decision shieldCounterDecision = new Decision();
	int shieldCounterTicks;
	long shieldCounterEndedAt = Long.MIN_VALUE / 2;
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
	/** Where the bot last put its own TNT minecart, and when (so it doesn't knock out its own cart). */
	Vec3 ownCart;
	long ownCartAt = Long.MIN_VALUE / 2;
	/** The height of the ground the opponent last stood on (NaN until seen standing). */
	double targetGroundY = Double.NaN;
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
	/**
	 * Whether this jump waits for the fall before clicking (a crit) or clicks the moment the charge is back,
	 * on the way up (no crit): a practised player waits, a novice jumps and clicks whenever.
	 */
	boolean critPatient;
	/** Reach misjudgement (blocks) for the next click; resampled after every click. */
	double reachError;
	/** Attack charge the bot waits for before its next click; resampled after every click. */
	double cooldownThreshold = 0.9;
	/** A hand can be a little late to a swing window, even with a fully charged weapon. */
	long swingReadyAt = -1;
	int lastSelfHurtTime;
	int lastTargetHurtTime;
	int ticksSinceOwnClick = Integer.MAX_VALUE / 2;
	int retreatTicks;
	int retreatCooldown;
	/**
	 * Where on the opponent's body the bot's aim actually sits, relative to their chest (blocks: across,
	 * up): a hand never holds a crosshair perfectly still on a moving target, so the aim point wanders over
	 * the body, further for a shakier hand (see {@link #wanderAim}).
	 */
	double aimWanderAcross;
	double aimWanderUp;
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

	/**
	 * One tick of the aim point's wander: a slow random walk pulled back to the chest (an Ornstein-Uhlenbeck
	 * process), whose spread grows with the profile's hand jitter: about 0.17 blocks for a pro, 0.3 for an
	 * intermediate player, 0.5 for a beginner (a player is 0.6 wide and 1.8 tall).
	 */
	void wanderAim(io.github.flick256.sparbot.core.math.Rng rng, io.github.flick256.sparbot.core.profile.SkillProfile.Aim aim) {
		double sigma = 0.04 + 0.085 * aim.jitterDeg();
		aimWanderAcross += -0.15 * aimWanderAcross + rng.nextGaussian() * sigma;
		aimWanderUp += -0.15 * aimWanderUp + rng.nextGaussian() * sigma * 1.3;
		aimWanderAcross = Math.max(-0.6, Math.min(0.6, aimWanderAcross));
		aimWanderUp = Math.max(-0.9, Math.min(0.7, aimWanderUp));
	}
}
