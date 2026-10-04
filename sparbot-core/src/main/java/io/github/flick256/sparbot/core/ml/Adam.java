package io.github.flick256.sparbot.core.ml;

/** The Adam optimiser (Kingma and Ba): per-weight step sizes from running gradient averages. */
final class Adam {
	private static final double BETA1 = 0.9;
	private static final double BETA2 = 0.999;
	private static final double EPSILON = 1e-8;

	private final double[] m;
	private final double[] v;
	private final double learningRate;
	private int t;

	Adam(int size, double learningRate) {
		this.m = new double[size];
		this.v = new double[size];
		this.learningRate = learningRate;
	}

	/** One step against the gradient (minimising). */
	void descend(double[] params, double[] gradient) {
		step(params, gradient, -1);
	}

	/** One step along the gradient (maximising). */
	void ascend(double[] params, double[] gradient) {
		step(params, gradient, 1);
	}

	private void step(double[] params, double[] g, int sign) {
		t++;
		double c1 = 1 - Math.pow(BETA1, t);
		double c2 = 1 - Math.pow(BETA2, t);
		for (int i = 0; i < params.length; i++) {
			m[i] = BETA1 * m[i] + (1 - BETA1) * g[i];
			v[i] = BETA2 * v[i] + (1 - BETA2) * g[i] * g[i];
			params[i] += sign * learningRate * (m[i] / c1) / (Math.sqrt(v[i] / c2) + EPSILON);
		}
	}
}
