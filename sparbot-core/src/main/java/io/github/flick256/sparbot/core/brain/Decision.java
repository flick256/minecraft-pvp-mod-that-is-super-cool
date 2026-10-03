package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.math.Rng;

/**
 * A yes/no choice re-rolled only every {@code window} ticks, so a skill probability means "how often
 * this player tends to do X" without flickering between choices every tick.
 */
final class Decision {
	private boolean value;
	private int ticksLeft;

	boolean get(Rng rng, double probability, int window) {
		if (ticksLeft-- <= 0) {
			value = rng.chance(probability);
			ticksLeft = window;
		}
		return value;
	}

	void reset() {
		ticksLeft = 0;
		value = false;
	}
}
