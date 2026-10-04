package io.github.flick256.sparbot.core.ml;

import io.github.flick256.sparbot.core.brain.DuelBrain;
import io.github.flick256.sparbot.core.brain.LearnedMeleeTactic;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sim.DuelSim;
import io.github.flick256.sparbot.core.sim.Loadout;
import io.github.flick256.sparbot.core.sim.Tournament;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Step two of training: get better than the teacher by playing. Evolution strategies (Salimans et al.,
 * 2017): each generation tries many small random changes to the weights, plays every changed network
 * against the same opponents in the simulator, and moves the weights towards the changes that won.
 * No gradients through the game are needed, every candidate runs on its own core, and the noise is
 * kept down with mirrored pairs (+change and -change) that play the same fights.
 *
 * <p>The opponents are a league: the scripted brain at the top levels plus snapshots of the network
 * itself from earlier generations, so it can't win by learning one trick that only beats one opponent
 * (the usual failure of pure self-play).
 */
public final class SelfPlay {
	/**
	 * @param pairs mirrored pairs per generation (population = 2 x pairs)
	 * @param sigma size of the random weight changes
	 * @param learningRate Adam step size
	 * @param fights fights per candidate per generation
	 * @param snapshotEvery generations between snapshots added to the league
	 * @param leagueSize most snapshots kept
	 */
	public record Config(int pairs, double sigma, double learningRate, int fights, int snapshotEvery, int leagueSize) {
		public static final Config DEFAULT = new Config(16, 0.03, 0.01, 12, 10, 6);

		/** The same, with the population scaled to use {@code cores} cores well (more cores, more candidates). */
		public Config scaledTo(int cores) {
			return new Config(Math.max(pairs, 4 * cores), sigma, learningRate, fights, snapshotEvery, leagueSize);
		}
	}

	/**
	 * Shaping on top of winning: a penalty for strafing more than {@code freeStrafe} of the time (constant
	 * circling wins against bots but looks dizzy and teaches nothing), a bonus per crit landed.
	 */
	public record Shaping(double freeStrafe, double strafePenalty, double critBonus) {
		public static final Shaping NONE = new Shaping(1.0, 0, 0);
		/** Prefer timing, crits and spacing over constant strafing. */
		public static final Shaping HUMAN_LIKE = new Shaping(0.3, 0.3, 0.004);
	}

	private final SkillProfile limits;
	private final List<Tournament.Entrant> scripted;
	private final Config config;
	private Loadout loadout = Loadout.DIAMOND_SWORD;
	private Shaping shaping = Shaping.NONE;
	private final List<Mlp> snapshots = new ArrayList<>();

	/**
	 * @param limits the skill profile whose human limits (reaction, aim, clicks) the learned player plays under
	 * @param scripted scripted opponents always in the league
	 */
	public SelfPlay(SkillProfile limits, List<Tournament.Entrant> scripted, Config config) {
		this.limits = limits;
		this.scripted = scripted;
		this.config = config;
	}

	/** Fights with {@code newLoadout} and scores with {@code newShaping}. */
	public SelfPlay with(Loadout newLoadout, Shaping newShaping) {
		this.loadout = newLoadout;
		this.shaping = newShaping;
		return this;
	}

	/** The learned network as a tournament entrant, under the given limits. */
	public static Tournament.Entrant entrant(String name, SkillProfile limits, Mlp net) {
		return new Tournament.Entrant(name, new DuelBrain(limits, 0).profile(),
			seed -> new DuelBrain(limits, Playstyle.BALANCED, seed, new LearnedMeleeTactic(net)));
	}

	/** Mean shaped score of {@code net} over this generation's fights (the same fights for every candidate). */
	double fitness(Mlp net, long generationSeed) {
		Random pick = new Random(generationSeed);
		Tournament.Entrant me = entrant("candidate", limits, net);
		double total = 0;
		for (int i = 0; i < config.fights(); i++) {
			Tournament.Entrant opponent = opponent(pick);
			long seed = generationSeed * 7919 + i;
			boolean first = i % 2 == 0;
			DuelSim.Result r = first
				? DuelSim.fight(me.side(seed), opponent.side(seed + 1), loadout, seed, Tournament.MAX_TICKS)
				: DuelSim.fight(opponent.side(seed + 1), me.side(seed), loadout, seed, Tournament.MAX_TICKS);
			int side = first ? 0 : 1;
			// Winning is what counts; the damage difference smooths the signal between wins and losses.
			double damage = Math.max(-1, Math.min(1, (r.damage()[side] - r.damage()[1 - side]) / 20.0));
			double strafe = r.strafeTicks()[side] / (double) Math.max(1, r.ticks());
			total += 0.7 * r.score(side) + 0.3 * (0.5 + 0.5 * damage) - shaping.strafePenalty() * Math.max(0, strafe - shaping.freeStrafe())
				+ Math.min(0.05, shaping.critBonus() * r.crits()[side]);
		}
		return total / config.fights();
	}

	private Tournament.Entrant opponent(Random pick) {
		if (!snapshots.isEmpty() && pick.nextBoolean()) {
			return entrant("snapshot", limits, snapshots.get(pick.nextInt(snapshots.size())));
		}
		return scripted.get(pick.nextInt(scripted.size()));
	}

	/** Improves {@code start} for {@code generations} generations; {@code log} gets a line per generation. */
	public Mlp train(Mlp start, int generations, long seed, Consumer<String> log) {
		return train(start, generations, seed, 0, log);
	}

	/** As {@link #train(Mlp, int, long, Consumer)}, numbering the generations from {@code firstGeneration + 1} in the log. */
	public Mlp train(Mlp start, int generations, long seed, int firstGeneration, Consumer<String> log) {
		Mlp theta = start.copy();
		Adam adam = new Adam(theta.size(), config.learningRate());
		Random random = new Random(seed);
		int n = theta.size();
		for (int gen = 0; gen < generations; gen++) {
			long genSeed = random.nextLong();
			double[][] eps = new double[config.pairs()][n];
			for (double[] e : eps) {
				for (int i = 0; i < n; i++) {
					e[i] = random.nextGaussian();
				}
			}
			double[] base = theta.params().clone();
			double[] fit = new double[2 * config.pairs()];
			IntStream.range(0, 2 * config.pairs()).parallel().forEach(k -> {
				double[] e = eps[k / 2];
				double sign = k % 2 == 0 ? 1 : -1;
				double[] p = new double[n];
				for (int i = 0; i < n; i++) {
					p[i] = base[i] + sign * config.sigma() * e[i];
				}
				fit[k] = fitness(theta.withParams(p), genSeed);
			});
			double[] util = centredRanks(fit);
			double[] grad = new double[n];
			for (int k = 0; k < fit.length; k++) {
				double[] e = eps[k / 2];
				double w = util[k] * (k % 2 == 0 ? 1 : -1) / (fit.length * config.sigma());
				for (int i = 0; i < n; i++) {
					grad[i] += w * e[i];
				}
			}
			adam.ascend(theta.params(), grad);
			if ((gen + 1) % config.snapshotEvery() == 0) {
				snapshots.add(theta.copy());
				if (snapshots.size() > config.leagueSize()) {
					snapshots.remove(0);
				}
			}
			log.accept(String.format(java.util.Locale.ROOT, "generation %d: mean fitness %.3f, best %.3f", firstGeneration + gen + 1, Arrays.stream(fit).average().orElse(0),
				Arrays.stream(fit).max().orElse(0)));
		}
		return theta;
	}

	/** Fitness shaping: ranks mapped to [-0.5, 0.5], so a lucky outlier can't dominate a step. */
	static double[] centredRanks(double[] values) {
		Integer[] order = new Integer[values.length];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, (a, b) -> Double.compare(values[a], values[b]));
		double[] ranks = new double[values.length];
		for (int r = 0; r < order.length; r++) {
			ranks[order[r]] = values.length == 1 ? 0 : r / (double) (values.length - 1) - 0.5;
		}
		return ranks;
	}
}
