package io.github.flick256.sparbot.core.profile;

import io.github.flick256.sparbot.core.math.Rng;

/** A clamped normal distribution, used for every "human variance" parameter in a skill profile. */
public record Distribution(double mean, double stdDev, double min, double max) {
	public static Distribution fixed(double value) {
		return new Distribution(value, 0, value, value);
	}

	public double sample(Rng rng) {
		double value = stdDev <= 0 ? mean : rng.gaussian(mean, stdDev);
		return Math.max(min, Math.min(max, value));
	}

	void validate(String name, double lowerBound, double upperBound, java.util.List<String> errors) {
		if (!(min <= mean && mean <= max)) {
			errors.add(name + ": mean " + mean + " must lie within [min " + min + ", max " + max + "]");
		}
		if (stdDev < 0) {
			errors.add(name + ": stdDev must be >= 0");
		}
		if (min < lowerBound || max > upperBound) {
			errors.add(name + ": range [" + min + ", " + max + "] must lie within [" + lowerBound + ", " + upperBound + "]");
		}
	}
}
