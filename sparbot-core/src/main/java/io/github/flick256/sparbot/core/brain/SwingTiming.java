package io.github.flick256.sparbot.core.brain;

/** Motor timing after a swing window opens; sampled once, never rerolled each tick. */
final class SwingTiming {
	private SwingTiming() {
	}

	static boolean ready(BrainContext c, boolean eligible) {
		if (!eligible) {
			c.memory.swingReadyAt = -1;
			return false;
		}
		if (c.memory.swingReadyAt < 0) {
			int maxDelay = 2 + (int) Math.round(2 * (1 - c.profile.clicking().cooldownDiscipline()));
			c.memory.swingReadyAt = c.observation.tick() + c.rng.nextInt(0, maxDelay);
		}
		return c.observation.tick() >= c.memory.swingReadyAt;
	}

	static void clicked(BrainContext c) {
		c.memory.swingReadyAt = -1;
		c.memory.ticksSinceOwnClick = 0;
		c.memory.reachError = c.profile.reach().rangeErrorBlocks().sample(c.rng);
		c.memory.cooldownThreshold = EngageTactic.sampleCooldownThreshold(c);
	}
}
