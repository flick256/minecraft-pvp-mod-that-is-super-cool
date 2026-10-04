package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.brain.DuelBrain;
import io.github.flick256.sparbot.core.brain.Policy;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongFunction;
import java.util.stream.IntStream;

/**
 * Round robins in the simulator: how often each player beats each other one. Used to check that the
 * skill tiers are really ordered (a pro beats an advanced player, who beats an intermediate...), and as
 * the yardstick for learned policies.
 */
public final class Tournament {
	/** 60 seconds, a long sword duel. */
	public static final int MAX_TICKS = 1200;
	/** 150 seconds for a full-kit (UHC) fight, which takes longer. */
	public static final int MAX_KIT_TICKS = 3000;

	private Tournament() {
	}

	/** A named contestant: makes a fresh policy for each fight (a policy has per-fight state) and says whose limits apply. */
	public record Entrant(String name, SkillProfile limits, LongFunction<Policy> factory) {
		public DuelSim.Side side(long seed) {
			return new DuelSim.Side(factory.apply(seed), limits);
		}
	}

	/** The scripted brain at a skill profile. */
	public static Entrant scripted(SkillProfile profile) {
		return new Entrant(profile.id(), new DuelBrain(profile, 0).profile(), seed -> new DuelBrain(profile, seed));
	}

	/** The scripted brain at a skill profile with some techniques switched off. */
	public static Entrant scripted(SkillProfile profile, String name, java.util.Set<io.github.flick256.sparbot.core.brain.Technique> disabled) {
		return new Entrant(name, new DuelBrain(profile, 0).profile(), seed -> {
			DuelBrain brain = new DuelBrain(profile, seed);
			brain.setDisabledTechniques(disabled);
			return brain;
		});
	}

	/** Mean score of {@code a} against {@code b} over {@code fights} sword fights (sides swapped every other fight), run in parallel. */
	public static double score(Entrant a, Entrant b, int fights, long seed) {
		return score(a, b, fights, seed, Loadout.DIAMOND_SWORD);
	}

	/** As {@link #score(Entrant, Entrant, int, long)} with the given loadout. */
	public static double score(Entrant a, Entrant b, int fights, long seed, Loadout loadout) {
		int maxTicks = loadout.hasKit() ? MAX_KIT_TICKS : MAX_TICKS;
		return IntStream.range(0, fights).parallel().mapToDouble(i -> {
			long s = seed * 1_000_003L + i;
			if (i % 2 == 0) {
				return DuelSim.fight(a.side(s), b.side(s ^ 0x9E3779B9L), loadout, s, maxTicks).score(0);
			}
			return DuelSim.fight(b.side(s ^ 0x9E3779B9L), a.side(s), loadout, s, maxTicks).score(1);
		}).average().orElse(0.5);
	}

	/** Score matrix: row entrant's mean score against the column entrant. */
	public static double[][] matrix(List<Entrant> entrants, int fights, long seed) {
		int n = entrants.size();
		double[][] m = new double[n][n];
		for (int i = 0; i < n; i++) {
			m[i][i] = 0.5;
			for (int j = i + 1; j < n; j++) {
				double s = score(entrants.get(i), entrants.get(j), fights, seed + i * 31L + j);
				m[i][j] = s;
				m[j][i] = 1 - s;
			}
		}
		return m;
	}

	public static String format(List<Entrant> entrants, double[][] m) {
		StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "%-14s", ""));
		for (Entrant e : entrants) {
			sb.append(String.format(Locale.ROOT, "%13s", e.name()));
		}
		sb.append('\n');
		for (int i = 0; i < entrants.size(); i++) {
			sb.append(String.format(Locale.ROOT, "%-14s", entrants.get(i).name()));
			for (int j = 0; j < entrants.size(); j++) {
				sb.append(String.format(Locale.ROOT, "%13.2f", m[i][j]));
			}
			sb.append('\n');
		}
		return sb.toString();
	}

	/** Prints the matrix of the five bundled tiers: {@code java ... Tournament [fightsPerPair]}. */
	public static void main(String[] args) {
		int fights = args.length > 0 ? Integer.parseInt(args[0]) : 40;
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		List<Entrant> entrants = new ArrayList<>();
		for (String id : SkillProfiles.PRESET_IDS) {
			entrants.add(scripted(presets.get(id)));
		}
		long start = System.nanoTime();
		double[][] m = matrix(entrants, fights, 1);
		double seconds = (System.nanoTime() - start) / 1e9;
		System.out.print(format(entrants, m));
		System.out.printf(Locale.ROOT, "%d fights in %.1f s%n", fights * entrants.size() * (entrants.size() - 1) / 2, seconds);
	}
}
