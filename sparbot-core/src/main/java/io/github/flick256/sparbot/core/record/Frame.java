package io.github.flick256.sparbot.core.record;

import java.util.Arrays;
import java.util.Objects;

/** One tick of a recording; {@code samples[i]} is fighter i, or null if they weren't there (dead, logged out). */
public record Frame(long tick, Sample[] samples) {
	@Override
	public boolean equals(Object o) {
		return o instanceof Frame f && f.tick == tick && Arrays.equals(f.samples, samples);
	}

	@Override
	public int hashCode() {
		return Objects.hash(tick, Arrays.hashCode(samples));
	}
}
