package io.github.flick256.sparbot.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Random;
import org.junit.jupiter.api.Test;

class MlpTest {
	@Test
	void backpropMatchesNumericalGradient() {
		Mlp net = Mlp.random(new Random(3), 4, 5, 3);
		double[] x = {0.3, -0.7, 0.1, 0.9};
		double[] target = {0.5, -0.2, 1.0};
		// Loss = 1/2 |y - target|^2, so d loss / d y = y - target.
		double[] y = net.forward(x);
		double[] gy = new double[3];
		for (int i = 0; i < 3; i++) {
			gy[i] = y[i] - target[i];
		}
		double[] grad = new double[net.size()];
		net.backward(x, gy, grad);
		double eps = 1e-6;
		for (int p = 0; p < net.size(); p++) {
			double old = net.params()[p];
			net.params()[p] = old + eps;
			double up = loss(net.forward(x), target);
			net.params()[p] = old - eps;
			double down = loss(net.forward(x), target);
			net.params()[p] = old;
			assertEquals((up - down) / (2 * eps), grad[p], 1e-6, "weight " + p);
		}
	}

	private static double loss(double[] y, double[] t) {
		double s = 0;
		for (int i = 0; i < y.length; i++) {
			s += 0.5 * (y[i] - t[i]) * (y[i] - t[i]);
		}
		return s;
	}

	@Test
	void survivesJson() {
		Mlp net = Mlp.random(new Random(1), 3, 4, 2);
		Mlp back = Mlp.fromJson(net.toJson());
		assertArrayEquals(net.forward(new double[] {1, 2, 3}), back.forward(new double[] {1, 2, 3}), 1e-12);
	}
}
