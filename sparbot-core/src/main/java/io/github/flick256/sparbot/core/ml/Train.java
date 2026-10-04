package io.github.flick256.sparbot.core.ml;

import io.github.flick256.sparbot.core.brain.MeleeFeatures;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sim.Loadout;
import io.github.flick256.sparbot.core.sim.Tournament;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Trains a melee model: imitate the scripted pro (unless continuing from a model), then improve by
 * self-play, keeping the version that scores best against the scripted pro. From a checkout:
 * {@code ./gradlew :sparbot-core:trainSword} or {@code trainUhc} ({@code -Pgenerations=300 -Pout=model.json
 * [-Pstart=existing.json]}); in game: {@code /sparbot train [sword|uhc] <name> [generations] [from]}.
 */
public final class Train {
	private static final int EVAL_FIGHTS = 200;
	private static final int EVAL_EVERY = 10;

	/** What the model is for: which loadout it fights with and which feature version it sees. */
	public enum Mode {
		/** Sword duels: sword and armor only. */
		SWORD(Loadout.DIAMOND_SWORD, 1),
		/** UHC melee: sword, axe, shield, golden apples, Protection armor, no natural regeneration. */
		UHC(Loadout.UHC, 2);

		private final Loadout loadout;
		private final int version;

		Mode(Loadout loadout, int version) {
			this.loadout = loadout;
			this.version = version;
		}

		public Loadout loadout() {
			return loadout;
		}

		public int inputs() {
			return version >= 2 ? MeleeFeatures.COUNT_V2 : MeleeFeatures.COUNT;
		}

		public int outputs() {
			return version >= 2 ? MeleeFeatures.OUTPUTS_V2 : MeleeFeatures.OUTPUTS;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The mode a network of this shape was trained for. */
		public static Mode of(Mlp net) {
			return net.inputs() == MeleeFeatures.COUNT_V2 ? UHC : SWORD;
		}
	}

	private Train() {
	}

	/**
	 * Runs a training. Parallel parts run in the calling thread's fork-join pool (run it inside a
	 * dedicated pool to limit the cores it takes).
	 *
	 * @param start a model to continue from (of this mode's shape), or null to start by imitating the scripted pro
	 * @param log progress lines
	 * @param cancelled checked between generations
	 * @param onBest gets every new best model (and the starting one)
	 * @return the best model's score against the scripted pro
	 */
	public static double run(Mode mode, Mlp start, int generations, Consumer<String> log, BooleanSupplier cancelled, Consumer<Mlp> onBest) {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		SkillProfile pro = presets.get("pro");
		Tournament.Entrant scriptedPro = Tournament.scripted(pro);
		Tournament.Entrant scriptedAdvanced = Tournament.scripted(presets.get("advanced"));
		int cores = Math.max(1, java.util.concurrent.ForkJoinTask.getPool() != null ? java.util.concurrent.ForkJoinTask.getPool().getParallelism()
			: Runtime.getRuntime().availableProcessors());
		Mlp net;
		if (start != null) {
			if (start.inputs() != mode.inputs() || start.outputs() != mode.outputs()) {
				throw new IllegalArgumentException("that model is not a " + mode.id() + " model");
			}
			net = start.copy();
			log.accept("continuing from " + net);
		} else {
			log.accept("collecting examples from the scripted pro (" + mode.id() + ")");
			List<Imitation.Example> examples = Imitation.collect(pro, List.of(presets.get("intermediate"), presets.get("advanced"), pro),
				mode == Mode.UHC ? 200 : 400, 1, mode.loadout(), mode.version);
			net = Mlp.random(new Random(1), mode.inputs(), 32, 32, mode.outputs());
			double loss = Imitation.train(net, examples, 12, 0.003, 1);
			double[] agree = Imitation.agreement(net, examples);
			log.accept(String.format(Locale.ROOT, "imitated %d decisions (loss %.3f; same keys as the teacher: forward %.0f%%, strafe %.0f%%, sprint %.0f%%, click %.0f%%)",
				examples.size(), loss, 100 * agree[0], 100 * agree[1], 100 * agree[3], 100 * agree[4]));
		}
		double best = evaluate(mode, net, pro, scriptedPro, scriptedAdvanced, log, "start");
		onBest.accept(net.copy());
		SelfPlay selfPlay = new SelfPlay(pro, List.of(scriptedPro, scriptedAdvanced), SelfPlay.Config.DEFAULT.scaledTo(cores))
			.with(mode.loadout(), SelfPlay.Shaping.HUMAN_LIKE);
		for (int done = 0; done < generations && !cancelled.getAsBoolean(); done += EVAL_EVERY) {
			int chunk = Math.min(EVAL_EVERY, generations - done);
			net = selfPlay.train(net, chunk, 1000 + done, done, line -> { });
			double score = evaluate(mode, net, pro, scriptedPro, scriptedAdvanced, log, "after " + (done + chunk) + " generations");
			if (score > best) {
				best = score;
				onBest.accept(net.copy());
				log.accept("new best");
			}
		}
		return best;
	}

	private static double evaluate(Mode mode, Mlp net, SkillProfile limits, Tournament.Entrant pro, Tournament.Entrant advanced, Consumer<String> log,
		String when) {
		Tournament.Entrant me = SelfPlay.entrant("learned", limits, net);
		double vsPro = Tournament.score(me, pro, EVAL_FIGHTS, 424242, mode.loadout());
		double vsAdvanced = Tournament.score(me, advanced, EVAL_FIGHTS, 434343, mode.loadout());
		log.accept(String.format(Locale.ROOT, "%s: score against the scripted pro %.2f, against the scripted advanced %.2f", when, vsPro, vsAdvanced));
		return vsPro;
	}

	/** {@code Train <sword|uhc> [generations] [out.json] [start.json]} */
	public static void main(String[] args) throws IOException {
		Mode mode = Mode.valueOf(args.length > 0 ? args[0].toUpperCase(Locale.ROOT) : "SWORD");
		int generations = args.length > 1 ? Integer.parseInt(args[1]) : 100;
		Path out = Path.of(args.length > 2 ? args[2] : "build/models/" + mode.id() + ".json");
		Path startFrom = args.length > 3 && !args[3].isBlank() ? Path.of(args[3]) : null;
		Mlp start = startFrom == null ? null : Mlp.fromJson(Files.readString(startFrom, StandardCharsets.UTF_8));
		long t0 = System.nanoTime();
		Consumer<String> log = line -> System.out.printf(Locale.ROOT, "[%6.1f s] %s%n", (System.nanoTime() - t0) / 1e9, line);
		log.accept("training a " + mode.id() + " model on " + Runtime.getRuntime().availableProcessors() + " cores");
		double best = run(mode, start, generations, log, () -> false, net -> {
			try {
				if (out.getParent() != null) {
					Files.createDirectories(out.getParent());
				}
				Files.writeString(out, net.toJson(), StandardCharsets.UTF_8);
			} catch (IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
		log.accept(String.format(Locale.ROOT, "done: best score against the scripted pro %.2f, saved to %s", best, out));
	}
}
