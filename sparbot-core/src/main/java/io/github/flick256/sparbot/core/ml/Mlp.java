package io.github.flick256.sparbot.core.ml;

import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Random;

/**
 * A small fully connected network (tanh hidden layers, linear outputs) with its weights in one flat
 * array, so evolution strategies can perturb them directly and gradient descent can update them. Tiny
 * on purpose: a few thousand weights evaluate in a microsecond and train on a laptop CPU.
 */
public final class Mlp {
	private static final Gson GSON = new Gson();

	private final int[] sizes;
	private final double[] params;

	public Mlp(int... sizes) {
		this.sizes = sizes.clone();
		this.params = new double[count(sizes)];
	}

	private Mlp(int[] sizes, double[] params) {
		this.sizes = sizes.clone();
		this.params = params.clone();
		if (params.length != count(sizes)) {
			throw new IllegalArgumentException("expected " + count(sizes) + " weights, got " + params.length);
		}
	}

	private static int count(int[] sizes) {
		int n = 0;
		for (int l = 0; l + 1 < sizes.length; l++) {
			n += sizes[l] * sizes[l + 1] + sizes[l + 1];
		}
		return n;
	}

	/** Random weights scaled for tanh (Xavier), zero biases. */
	public static Mlp random(Random random, int... sizes) {
		Mlp net = new Mlp(sizes);
		int p = 0;
		for (int l = 0; l + 1 < sizes.length; l++) {
			double scale = Math.sqrt(1.0 / sizes[l]);
			for (int i = 0; i < sizes[l] * sizes[l + 1]; i++) {
				net.params[p++] = random.nextGaussian() * scale;
			}
			p += sizes[l + 1];
		}
		return net;
	}

	public int inputs() {
		return sizes[0];
	}

	public int outputs() {
		return sizes[sizes.length - 1];
	}

	public int size() {
		return params.length;
	}

	/** The weights themselves (not a copy): changing them changes the network. */
	public double[] params() {
		return params;
	}

	public Mlp copy() {
		return new Mlp(sizes, params);
	}

	public Mlp withParams(double[] newParams) {
		return new Mlp(sizes, newParams);
	}

	public double[] forward(double[] input) {
		return forward(input, null);
	}

	/** Forward pass; if {@code activations} is given, each layer's outputs are stored in it (for backprop). */
	double[] forward(double[] input, double[][] activations) {
		double[] a = input;
		if (activations != null) {
			activations[0] = input;
		}
		int p = 0;
		for (int l = 0; l + 1 < sizes.length; l++) {
			int in = sizes[l];
			int out = sizes[l + 1];
			double[] next = new double[out];
			int biases = p + in * out;
			for (int j = 0; j < out; j++) {
				double sum = params[biases + j];
				int row = p + j * in;
				for (int i = 0; i < in; i++) {
					sum += params[row + i] * a[i];
				}
				next[j] = l + 2 < sizes.length ? Math.tanh(sum) : sum;
			}
			p = biases + out;
			a = next;
			if (activations != null) {
				activations[l + 1] = a;
			}
		}
		return a;
	}

	/**
	 * Backpropagates {@code outputGradient} (d loss / d output) for one example and adds the weight
	 * gradient to {@code gradient}.
	 */
	void backward(double[] input, double[] outputGradient, double[] gradient) {
		double[][] acts = new double[sizes.length][];
		forward(input, acts);
		int[] offsets = new int[sizes.length - 1];
		int p = 0;
		for (int l = 0; l + 1 < sizes.length; l++) {
			offsets[l] = p;
			p += sizes[l] * sizes[l + 1] + sizes[l + 1];
		}
		double[] delta = outputGradient.clone();
		for (int l = sizes.length - 2; l >= 0; l--) {
			int in = sizes[l];
			int out = sizes[l + 1];
			int w = offsets[l];
			int b = w + in * out;
			double[] prev = acts[l];
			double[] prevDelta = new double[in];
			for (int j = 0; j < out; j++) {
				double d = delta[j];
				gradient[b + j] += d;
				int row = w + j * in;
				for (int i = 0; i < in; i++) {
					gradient[row + i] += d * prev[i];
					prevDelta[i] += d * params[row + i];
				}
			}
			if (l > 0) {
				for (int i = 0; i < in; i++) {
					prevDelta[i] *= 1 - prev[i] * prev[i]; // tanh'
				}
			}
			delta = prevDelta;
		}
	}

	private record Json(int[] sizes, double[] params) {
	}

	public String toJson() {
		return GSON.toJson(new Json(sizes, params));
	}

	public static Mlp fromJson(String json) {
		Json j = GSON.fromJson(json, Json.class);
		if (j == null || j.sizes() == null || j.params() == null) {
			throw new IllegalArgumentException("not a network");
		}
		return new Mlp(j.sizes(), j.params());
	}

	@Override
	public String toString() {
		return "Mlp" + Arrays.toString(sizes) + " (" + params.length + " weights)";
	}
}
