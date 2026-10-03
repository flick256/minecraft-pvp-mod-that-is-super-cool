package io.github.flick256.sparbot.core.brain;

/** State the duel brain carries between ticks. Reset on respawn / new round. */
public final class DuelMemory {
	final Hands hands = new Hands();
	final Decision axeDecision = new Decision();
	final Decision blockHitDecision = new Decision();
	boolean axeMode;
	int axeModeTicks;
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
