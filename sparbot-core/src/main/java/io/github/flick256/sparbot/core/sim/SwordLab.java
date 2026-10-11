package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import java.util.Locale;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Sword fights between two scripted bots in a kit, and how they went: how long they lasted, how many hits
 * a kill took, how many of those were crits and sprint hits, and the longest run of hits without one back.
 * Used to balance the sword kits (a fight that ends in a handful of hits teaches nothing) and to check
 * that a change to the brain didn't make a tier fight in an unhuman way.
 */
public final class SwordLab {
	private SwordLab() {
	}

	/** What a batch of fights averaged. */
	public record Summary(int fights, double winsA, double seconds, double hitsToKill, double critShare, double sprintShare, double timeouts,
		double meanHitDistance, double hitsBeyond3) {
		@Override
		public String toString() {
			return String.format(Locale.ROOT, "%d fights: A won %.0f%%, %.1f s a fight, %.1f hits to a kill, crits %.0f%%, sprint hits %.0f%%, timeouts %.0f%%,"
				+ " hits from %.2f blocks on average (%.1f%% beyond 3)", fights, winsA * 100, seconds, hitsToKill, critShare * 100, sprintShare * 100,
				timeouts * 100, meanHitDistance, hitsBeyond3 * 100);
		}
	}

	public static Summary run(SkillProfile a, SkillProfile b, Loadout loadout, int fights, long seed) {
		return run(Tournament.scripted(a), Tournament.scripted(b), loadout, fights, seed);
	}

	public static Summary run(Tournament.Entrant ea, Tournament.Entrant eb, Loadout loadout, int fights, long seed) {
		DuelSim.Result[] results = IntStream.range(0, fights).parallel().mapToObj(i -> {
			long s = seed * 1_000_003L + i;
			return DuelSim.fight(ea.side(s), eb.side(s ^ 0x9E3779B9L), loadout, SimArena.DUEL, s, Tournament.MAX_TICKS * 2, null);
		}).toArray(DuelSim.Result[]::new);
		double wins = 0;
		double ticks = 0;
		double killHits = 0;
		int kills = 0;
		double crits = 0;
		double sprint = 0;
		double hits = 0;
		int timeouts = 0;
		double distance = 0;
		double beyond = 0;
		double damaging = 0;
		for (DuelSim.Result r : results) {
			wins += r.score(0);
			ticks += r.ticks();
			if (r.winner() >= 0) {
				killHits += r.hits()[r.winner()];
				kills++;
			} else {
				timeouts++;
			}
			for (int k = 0; k < 2; k++) {
				crits += r.crits()[k];
				sprint += r.sprintHits()[k];
				hits += r.hits()[k];
				DuelSim.SideStats st = r.sides()[k];
				distance += st.meanHitDistance() * st.damagingHits();
				beyond += st.hitsBeyond(3.0);
				damaging += st.damagingHits();
			}
		}
		return new Summary(fights, wins / fights, ticks / fights / 20.0, kills == 0 ? 0 : killHits / kills, hits == 0 ? 0 : crits / hits,
			hits == 0 ? 0 : sprint / hits, timeouts / (double) fights, damaging == 0 ? 0 : distance / damaging, damaging == 0 ? 0 : beyond / damaging);
	}

	/** {@code fights a b kit}: e.g. {@code 100 pro intermediate pvphq_sword}. */
	public static void main(String[] args) {
		int fights = args.length > 0 ? Integer.parseInt(args[0]) : 100;
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		// A name is a profile id, or <profile>-off:<technique>,<technique> (see UhcLab#entrant).
		Tournament.Entrant a = UhcLab.entrant(args.length > 1 ? args[1] : "pro", presets);
		Tournament.Entrant b = UhcLab.entrant(args.length > 2 ? args[2] : "pro", presets);
		String kit = args.length > 3 ? args[3] : "pvphq_sword";
		System.out.println(a.name() + " vs " + b.name() + " in " + kit + ": " + run(a, b, Loadout.ofKit(kit), fights, 1));
	}
}
