package io.github.flick256.sparbot.core.sense;

/**
 * One tick of perception handed to a {@link io.github.flick256.sparbot.core.brain.Policy}.
 * {@code target} is null when the bot has no opponent within its awareness.
 */
public record Observation(long tick, SelfState self, TargetState target, Surroundings surroundings) {
	public Observation(long tick, SelfState self, TargetState target) {
		this(tick, self, target, Surroundings.EMPTY);
	}

	public boolean hasTarget() {
		return target != null;
	}
}
