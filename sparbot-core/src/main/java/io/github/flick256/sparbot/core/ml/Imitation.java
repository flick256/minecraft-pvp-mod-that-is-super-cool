package io.github.flick256.sparbot.core.ml;

import io.github.flick256.sparbot.core.brain.DuelBrain;
import io.github.flick256.sparbot.core.brain.ImitationRecorder;
import io.github.flick256.sparbot.core.brain.MeleeFeatures;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sim.DuelSim;
import io.github.flick256.sparbot.core.sim.Loadout;
import io.github.flick256.sparbot.core.sim.Tournament;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Step one of training: copy a teacher. The scripted brain fights in the simulator and every melee
 * decision it makes becomes an example; the network then learns to press the same keys in the same
 * situations (supervised learning, cross-entropy). This costs seconds and gives self-play a player
 * that already knows the basics, instead of one that has to discover walking forward by chance.
 * Recordings of real fights (the mod's recorder) can feed the same step later.
 */
public final class Imitation {
	/** One example: what the teacher saw, and what it pressed. */
	public record Example(double[] features, double[] target) {
	}

	private Imitation() {
	}

	/** Runs {@code fights} simulator fights of {@code teacher} against each opponent in turn and records the teacher's decisions. */
	public static List<Example> collect(SkillProfile teacher, List<SkillProfile> opponents, int fights, long seed) {
		List<Example> examples = Collections.synchronizedList(new ArrayList<>());
		java.util.stream.IntStream.range(0, fights).parallel().forEach(i -> {
			List<Example> local = new ArrayList<>();
			DuelBrain student = new DuelBrain(teacher, Playstyle.BALANCED, seed + i, new ImitationRecorder((f, t) -> local.add(new Example(f, t))));
			SkillProfile opponent = opponents.get(i % opponents.size());
			DuelSim.fight(new DuelSim.Side(student, student.profile()), Tournament.scripted(opponent).side(seed + i + 77), Loadout.DIAMOND_SWORD,
				seed * 31 + i, Tournament.MAX_TICKS);
			examples.addAll(local);
		});
		return new ArrayList<>(examples);
	}

	/**
	 * Trains {@code net} on the examples with Adam: softmax cross-entropy on the two three-way choices
	 * (forward/back, left/right), logistic loss on jump, sprint and click. Clicks are rare (one tick in
	 * ten or so), so their examples weigh more. Returns the final mean loss.
	 */
	public static double train(Mlp net, List<Example> examples, int epochs, double learningRate, long seed) {
		Random random = new Random(seed);
		List<Example> data = new ArrayList<>(examples);
		Adam adam = new Adam(net.size(), learningRate);
		int batch = 256;
		double lastLoss = 0;
		for (int epoch = 0; epoch < epochs; epoch++) {
			Collections.shuffle(data, random);
			double total = 0;
			for (int start = 0; start < data.size(); start += batch) {
				int end = Math.min(data.size(), start + batch);
				double[] grad = new double[net.size()];
				for (int k = start; k < end; k++) {
					Example e = data.get(k);
					double[] out = net.forward(e.features());
					double[] g = new double[MeleeFeatures.OUTPUTS];
					total += softmaxHead(out, g, 0, (int) e.target()[0]);
					total += softmaxHead(out, g, 3, (int) e.target()[1]);
					total += logistic(out, g, 6, e.target()[2], 1.0);
					total += logistic(out, g, 7, e.target()[3], 1.0);
					total += logistic(out, g, 8, e.target()[4], 3.0);
					net.backward(e.features(), g, grad);
				}
				int n = end - start;
				for (int p = 0; p < grad.length; p++) {
					grad[p] /= n;
				}
				adam.descend(net.params(), grad);
			}
			lastLoss = total / data.size();
		}
		return lastLoss;
	}

	private static double softmaxHead(double[] out, double[] g, int from, int label) {
		double max = Math.max(out[from], Math.max(out[from + 1], out[from + 2]));
		double[] e = new double[3];
		double sum = 0;
		for (int i = 0; i < 3; i++) {
			e[i] = Math.exp(out[from + i] - max);
			sum += e[i];
		}
		for (int i = 0; i < 3; i++) {
			g[from + i] = e[i] / sum - (i == label ? 1 : 0);
		}
		return -Math.log(Math.max(1e-12, e[label] / sum));
	}

	private static double logistic(double[] out, double[] g, int index, double label, double positiveWeight) {
		double p = 1 / (1 + Math.exp(-out[index]));
		double w = label > 0.5 ? positiveWeight : 1.0;
		g[index] = w * (p - label);
		return -w * (label * Math.log(Math.max(1e-12, p)) + (1 - label) * Math.log(Math.max(1e-12, 1 - p)));
	}

	/** Share of examples where the network presses the same keys as the teacher, per output (forward, strafe, jump, sprint, click). */
	public static double[] agreement(Mlp net, List<Example> examples) {
		double[] agree = new double[5];
		for (Example e : examples) {
			double[] out = net.forward(e.features());
			agree[0] += argmax3(out, 0) == (int) e.target()[0] ? 1 : 0;
			agree[1] += argmax3(out, 3) == (int) e.target()[1] ? 1 : 0;
			for (int i = 0; i < 3; i++) {
				agree[2 + i] += (out[6 + i] > 0) == (e.target()[2 + i] > 0.5) ? 1 : 0;
			}
		}
		for (int i = 0; i < 5; i++) {
			agree[i] /= examples.size();
		}
		return agree;
	}

	private static int argmax3(double[] out, int from) {
		int best = 0;
		for (int i = 1; i < 3; i++) {
			if (out[from + i] > out[from + best]) {
				best = i;
			}
		}
		return best;
	}
}
