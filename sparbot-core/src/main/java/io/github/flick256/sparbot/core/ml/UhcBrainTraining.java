package io.github.flick256.sparbot.core.ml;

import io.github.flick256.sparbot.core.brain.LearnedTactics;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sim.DuelSim;
import io.github.flick256.sparbot.core.sim.Loadout;
import io.github.flick256.sparbot.core.sim.SimArena;
import io.github.flick256.sparbot.core.sim.Tournament;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Trains the whole UHC brain in full-kit fights in the simulator (blocks, water, lava, webs, fire, bow):
 * the melee network and the tactic chooser ({@link LearnedTactics}) together, by evolution strategies
 * as in {@link SelfPlay}. The tactic chooser starts neutral (the scripted brain's choices exactly) and
 * learns when lava, webs, walls, the bow, heals and boosts pay off. The league: the scripted pro, the pro
 * with the learned melee, and snapshots of the brain being trained.
 */
public final class UhcBrainTraining {
	private static final int MAX_TICKS = 3000;
	private static final int EVAL_FIGHTS = 200;

	/**
	 * @param meleeSigma the change size for the melee network's weights (0 keeps it as it is)
	 * @param tacticsSigma the change size for the tactic chooser's weights
	 */
	public record Config(int pairs, double meleeSigma, double tacticsSigma, double learningRate, int fights, int snapshotEvery, int leagueSize,
		double terrainShare) {
		public static final Config DEFAULT = new Config(16, 0.02, 0.05, 0.01, 16, 10, 6, 0.3);

		public Config scaledTo(int cores) {
			return new Config(Math.max(pairs, 4 * cores), meleeSigma, tacticsSigma, learningRate, fights, snapshotEvery, leagueSize, terrainShare);
		}
	}

	private final SkillProfile limits;
	private final List<Tournament.Entrant> fixed;
	private final Config config;
	private final Loadout loadout;
	private final List<Mlp> snapshots = new ArrayList<>();

	public UhcBrainTraining(SkillProfile limits, List<Tournament.Entrant> fixed, Config config, Loadout loadout) {
		this.limits = limits;
		this.fixed = fixed;
		this.config = config;
		this.loadout = loadout;
	}

	/** Melee weights then tactic weights, in one vector. */
	static double[] flatten(Mlp model) {
		double[] m = model.params();
		double[] t = model.tactics().params();
		double[] p = Arrays.copyOf(m, m.length + t.length);
		System.arraycopy(t, 0, p, m.length, t.length);
		return p;
	}

	static Mlp unflatten(Mlp template, double[] p) {
		int m = template.size();
		Mlp tactics = template.tactics().withParams(Arrays.copyOfRange(p, m, p.length));
		return template.withParams(Arrays.copyOf(p, m)).withTactics(tactics);
	}

	private Tournament.Entrant opponent(Random pick) {
		int k = pick.nextInt(fixed.size() + (snapshots.isEmpty() ? 0 : 1));
		if (k < fixed.size()) {
			return fixed.get(k);
		}
		return SelfPlay.entrant("snapshot", limits, snapshots.get(pick.nextInt(snapshots.size())));
	}

	/**
	 * How much of the fight the side's utility made (0-1): fire and lava damage on the opponent, time the
	 * opponent spent stuck in its webs, and arrows that landed. A small part of the fitness, so that of two
	 * equally winning brains the one that fights like a UHC player (blocks, webs, lava, the bow) is kept.
	 */
	static double style(DuelSim.Result r, int side) {
		DuelSim.SideStats me = r.sides()[side];
		DuelSim.SideStats them = r.sides()[1 - side];
		double fire = (them.lavaDamage() + them.fireDamage()) / 4.0;
		double webbed = them.ticksInWeb() / 60.0;
		double arrows = me.arrowHits() / 4.0;
		return (Math.min(1, fire) + Math.min(1, webbed) + Math.min(1, arrows)) / 3.0;
	}

	/** Mean score over this generation's fights (the same fights for every candidate): winning, smoothed by the damage difference, and the utility play. */
	double fitness(Mlp model, long generationSeed) {
		Random pick = new Random(generationSeed);
		Tournament.Entrant me = SelfPlay.entrant("candidate", limits, model);
		double total = 0;
		for (int i = 0; i < config.fights(); i++) {
			Tournament.Entrant opponent = opponent(pick);
			SimArena arena = pick.nextDouble() < config.terrainShare() ? SimArena.UHC_TERRAIN : SimArena.UHC;
			long seed = generationSeed * 7919 + i;
			boolean first = i % 2 == 0;
			DuelSim.Result r = first ? DuelSim.fight(me.side(seed), opponent.side(seed + 1), loadout, arena, seed, MAX_TICKS, null)
				: DuelSim.fight(opponent.side(seed + 1), me.side(seed), loadout, arena, seed, MAX_TICKS, null);
			int side = first ? 0 : 1;
			double damage = Math.max(-1, Math.min(1, (r.damage()[side] - r.damage()[1 - side]) / 40.0));
			total += 0.65 * r.score(side) + 0.25 * (0.5 + 0.5 * damage) + 0.1 * style(r, side);
		}
		return total / config.fights();
	}

	/** Improves {@code start} (a melee model with a tactic chooser) for {@code generations} generations. */
	public Mlp train(Mlp start, int generations, long seed, int firstGeneration, Consumer<String> log) {
		Mlp model = start.copy();
		double[] theta = flatten(model);
		int n = theta.length;
		int meleeSize = model.size();
		Adam adam = new Adam(n, config.learningRate());
		Random random = new Random(seed);
		for (int gen = 0; gen < generations; gen++) {
			long genSeed = random.nextLong();
			double[][] eps = new double[config.pairs()][n];
			for (double[] e : eps) {
				for (int i = 0; i < n; i++) {
					e[i] = random.nextGaussian() * (i < meleeSize ? config.meleeSigma() : config.tacticsSigma());
				}
			}
			double[] base = theta.clone();
			Mlp template = model;
			double[] fit = new double[2 * config.pairs()];
			IntStream.range(0, 2 * config.pairs()).parallel().forEach(k -> {
				double[] e = eps[k / 2];
				double sign = k % 2 == 0 ? 1 : -1;
				double[] p = new double[n];
				for (int i = 0; i < n; i++) {
					p[i] = base[i] + sign * e[i];
				}
				fit[k] = fitness(unflatten(template, p), genSeed);
			});
			double[] util = SelfPlay.centredRanks(fit);
			double[] grad = new double[n];
			for (int k = 0; k < fit.length; k++) {
				double[] e = eps[k / 2];
				double w = util[k] * (k % 2 == 0 ? 1 : -1) / fit.length;
				for (int i = 0; i < n; i++) {
					double sigma = i < meleeSize ? config.meleeSigma() : config.tacticsSigma();
					if (sigma > 0) {
						grad[i] += w * e[i] / (sigma * sigma);
					}
				}
			}
			adam.ascend(theta, grad);
			model = unflatten(template, theta);
			if ((gen + 1) % config.snapshotEvery() == 0) {
				snapshots.add(model.copy());
				if (snapshots.size() > config.leagueSize()) {
					snapshots.remove(0);
				}
			}
			log.accept(String.format(Locale.ROOT, "generation %d: mean fitness %.3f, best %.3f", firstGeneration + gen + 1, Arrays.stream(fit).average().orElse(0),
				Arrays.stream(fit).max().orElse(0)));
		}
		return model;
	}

	/**
	 * A whole training run: the bundled UHC melee with a neutral tactic chooser (or {@code start}), then
	 * self-play, keeping the version that scores best against the scripted pro and the learned-melee pro.
	 */
	public static double run(Mlp start, int generations, Consumer<String> log, BooleanSupplier cancelled, Consumer<Mlp> onBest) {
		return run(start, false, generations, log, cancelled, onBest);
	}

	/** @param freshTactics start the tactic chooser over from neutral (the scripted choices), keeping the melee */
	public static double run(Mlp start, boolean freshTactics, int generations, Consumer<String> log, BooleanSupplier cancelled, Consumer<Mlp> onBest) {
		return run(start, freshTactics, "pro", generations, log, cancelled, onBest);
	}

	/**
	 * @param profileId the skill tier the brain is trained at (its human limits), and the scripted
	 *     opponent's; "demon" trains against the scripted demon and the scripted pro
	 */
	public static double run(Mlp start, boolean freshTactics, String profileId, int generations, Consumer<String> log, BooleanSupplier cancelled,
		Consumer<Mlp> onBest) {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		SkillProfile pro = presets.get(profileId);
		if (pro == null) {
			throw new IllegalArgumentException("unknown skill profile " + profileId);
		}
		Mlp bundled = Models.loadBundled().get("uhc");
		Mlp model = start != null ? start.copy() : bundled.copy();
		if (model.tactics() == null || freshTactics || !LearnedTactics.fits(model.tactics())) {
			model = model.withTactics(LearnedTactics.neutral(new Random(7)));
		}
		Loadout loadout = Loadout.ofKit("sparbot_uhc");
		Tournament.Entrant scriptedPro = Tournament.scripted(pro);
		Tournament.Entrant meleePro = SelfPlay.entrant("learned melee", pro, bundled.withTactics(null));
		List<Tournament.Entrant> league = new ArrayList<>(List.of(scriptedPro, meleePro));
		if (!profileId.equals("pro")) {
			league.add(Tournament.scripted(presets.get("pro")));
		}
		if (bundled.tactics() != null) {
			league.add(SelfPlay.entrant("bundled brain", pro, bundled));
		}
		int cores = Math.max(1, java.util.concurrent.ForkJoinTask.getPool() != null ? java.util.concurrent.ForkJoinTask.getPool().getParallelism()
			: Runtime.getRuntime().availableProcessors());
		UhcBrainTraining training = new UhcBrainTraining(pro, league, Config.DEFAULT.scaledTo(cores), loadout);
		double best = evaluate(model, pro, scriptedPro, meleePro, loadout, log, "start");
		onBest.accept(model.copy());
		for (int done = 0; done < generations && !cancelled.getAsBoolean(); done += 10) {
			int chunk = Math.min(10, generations - done);
			model = training.train(model, chunk, 5000 + done, done, log);
			double score = evaluate(model, pro, scriptedPro, meleePro, loadout, log, "after " + (done + chunk) + " generations");
			if (score > best) {
				best = score;
				onBest.accept(model.copy());
				log.accept("new best");
			}
		}
		return best;
	}

	/** Mean of the scores against the scripted pro and the learned-melee pro, in full-kit UHC. */
	static double evaluate(Mlp model, SkillProfile limits, Tournament.Entrant scriptedPro, Tournament.Entrant meleePro, Loadout loadout, Consumer<String> log,
		String when) {
		Tournament.Entrant me = SelfPlay.entrant("brain", limits, model);
		double vsScripted = Tournament.score(me, scriptedPro, EVAL_FIGHTS, 515151, loadout);
		double vsMelee = Tournament.score(me, meleePro, EVAL_FIGHTS, 525252, loadout);
		log.accept(String.format(Locale.ROOT, "%s: score against the scripted pro %.3f, against the learned-melee pro %.3f", when, vsScripted, vsMelee));
		return (vsScripted + vsMelee) / 2;
	}

	/**
	 * {@code UhcBrainTraining [generations] [out.json] [start.json] [fresh] [profile]} ({@code fresh}: a neutral
	 * tactic chooser on the start's melee; {@code profile}: the skill tier to train at, pro by default)
	 */
	public static void main(String[] args) throws java.io.IOException {
		int generations = args.length > 0 ? Integer.parseInt(args[0]) : 100;
		java.nio.file.Path out = java.nio.file.Path.of(args.length > 1 ? args[1] : "build/models/uhc_brain.json");
		Mlp start = args.length > 2 && !args[2].isBlank() ? Mlp.fromJson(java.nio.file.Files.readString(java.nio.file.Path.of(args[2]))) : null;
		long t0 = System.nanoTime();
		Consumer<String> log = line -> System.out.printf(Locale.ROOT, "[%7.1f s] %s%n", (System.nanoTime() - t0) / 1e9, line);
		log.accept("training the UHC brain on " + Runtime.getRuntime().availableProcessors() + " cores");
		boolean fresh = args.length > 3 && args[3].equals("fresh");
		String profile = args.length > 4 ? args[4] : "pro";
		double best = run(start, fresh, profile, generations, log, () -> false, net -> {
			try {
				if (out.getParent() != null) {
					java.nio.file.Files.createDirectories(out.getParent());
				}
				java.nio.file.Files.writeString(out, net.toJson());
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
		log.accept(String.format(Locale.ROOT, "done: best %.3f, saved to %s", best, out));
	}
}
