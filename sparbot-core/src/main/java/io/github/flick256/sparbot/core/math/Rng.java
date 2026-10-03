package io.github.flick256.sparbot.core.math;

import java.util.SplittableRandom;

/** Seedable randomness so bot behaviour is reproducible in tests and simulations. */
public final class Rng {
	private final SplittableRandom random;
	private double spareGaussian;
	private boolean hasSpare;

	public Rng(long seed) {
		this.random = new SplittableRandom(seed);
	}

	public double nextDouble() {
		return random.nextDouble();
	}

	public boolean chance(double probability) {
		return probability > 0 && random.nextDouble() < probability;
	}

	public int nextInt(int minInclusive, int maxInclusive) {
		return random.nextInt(minInclusive, maxInclusive + 1);
	}

	/** Standard normal sample (Marsaglia polar method). */
	public double nextGaussian() {
		if (hasSpare) {
			hasSpare = false;
			return spareGaussian;
		}
		double u;
		double v;
		double s;
		do {
			u = random.nextDouble() * 2 - 1;
			v = random.nextDouble() * 2 - 1;
			s = u * u + v * v;
		} while (s >= 1 || s == 0);
		double mul = Math.sqrt(-2.0 * Math.log(s) / s);
		spareGaussian = v * mul;
		hasSpare = true;
		return u * mul;
	}

	public double gaussian(double mean, double stdDev) {
		return mean + nextGaussian() * stdDev;
	}

	public Rng fork() {
		return new Rng(random.nextLong());
	}
}
