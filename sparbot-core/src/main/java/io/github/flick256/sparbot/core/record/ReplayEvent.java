package io.github.flick256.sparbot.core.record;

/** A fact observed during a fight. Text is deliberately descriptive rather than simulated. */
public record ReplayEvent(int tick, String kind, String text) {
	public ReplayEvent {
		if (tick < 0) throw new IllegalArgumentException("event tick must be nonnegative");
		if (kind == null || kind.isBlank() || kind.length() > 32) throw new IllegalArgumentException("invalid event kind");
		if (text == null || text.length() > 256) throw new IllegalArgumentException("invalid event text");
	}
}
