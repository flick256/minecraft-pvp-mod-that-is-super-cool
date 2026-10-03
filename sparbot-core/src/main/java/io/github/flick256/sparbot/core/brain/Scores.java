package io.github.flick256.sparbot.core.brain;

/**
 * Utility score bands shared by the tactics. The active tactic gets a +0.05 hysteresis bonus, so a
 * tactic meant to take over from melee must beat {@link #MELEE} by more than that.
 */
final class Scores {
	/** Plain melee engagement with a visible opponent. */
	static final double MELEE = 0.6;
	/** Situational tactics (bow, rod, shield guard, pearl) that should replace melee when chosen. */
	static final double SPECIALIST = 0.7;

	private Scores() {
	}
}
