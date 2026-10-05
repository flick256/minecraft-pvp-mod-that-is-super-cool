package io.github.flick256.sparbot.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.brain.LearnedTactics;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sim.DuelSim;
import io.github.flick256.sparbot.core.sim.Loadout;
import io.github.flick256.sparbot.core.sim.SimArena;
import io.github.flick256.sparbot.core.sim.Tournament;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The learned tactic chooser starts from exactly the scripted choices. */
class UhcBrainTest {
	@Test
	void aNeutralTacticsNetworkChangesNothing() {
		SkillProfile pro = SkillProfiles.loadPresets().get("pro");
		Mlp melee = Models.loadBundled().get("uhc").withTactics(null);
		Mlp neutral = melee.withTactics(LearnedTactics.neutral(new Random(5)));
		Tournament.Entrant plain = SelfPlay.entrant("plain", pro, melee);
		Tournament.Entrant withTactics = SelfPlay.entrant("neutral", pro, neutral);
		Tournament.Entrant scripted = Tournament.scripted(pro);
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		for (long seed = 1; seed <= 3; seed++) {
			DuelSim.Result a = DuelSim.fight(plain.side(seed), scripted.side(seed + 1), kit, SimArena.UHC, seed, 1500, null);
			DuelSim.Result b = DuelSim.fight(withTactics.side(seed), scripted.side(seed + 1), kit, SimArena.UHC, seed, 1500, null);
			assertEquals(a.ticks(), b.ticks(), "same fight");
			assertEquals(a.health()[0], b.health()[0], 1e-6);
			assertEquals(a.health()[1], b.health()[1], 1e-6);
		}
	}

	@Test
	void noTacticsNetworkCanTrainTheUtilityAway() {
		// The worst a network can do to UHC utility: hold back lava, webs, the bow, walls and boosts as far as
		// it may, and lift plain melee as far as it may. The bot still plays its utility.
		SkillProfile pro = SkillProfiles.loadPresets().get("pro");
		Mlp melee = Models.loadBundled().get("uhc").withTactics(null);
		Mlp tactics = LearnedTactics.neutral(new Random(5));
		double[] p = tactics.params();
		int bias = p.length - LearnedTactics.OUTPUTS;
		for (int k = 0; k < LearnedTactics.OUTPUTS; k++) {
			String name = LearnedTactics.TACTICS.get(k);
			boolean utility = java.util.Set.of("lava", "web", "ranged", "wall", "boost", "backline").contains(name);
			p[bias + k] = name.equals("engage") || name.equals("guard") || name.equals("retreat") ? 10 : utility ? -10 : 0;
		}
		Tournament.Entrant suppressed = SelfPlay.entrant("suppressed", pro, melee.withTactics(tactics.withParams(p)));
		Tournament.Entrant scripted = Tournament.scripted(pro);
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		int webs = 0;
		int lava = 0;
		int arrows = 0;
		for (long seed = 1; seed <= 12; seed++) {
			DuelSim.Result r = DuelSim.fight(suppressed.side(seed), scripted.side(seed + 1), kit, SimArena.UHC, seed, 3000, null);
			webs += r.sides()[0].websPlaced();
			lava += r.sides()[0].lavaPours();
			arrows += r.sides()[0].arrowsShot();
		}
		assertTrue(webs >= 6, "webs placed in 12 fights: " + webs);
		assertTrue(lava >= 4, "lava pours in 12 fights: " + lava);
		assertTrue(arrows >= 12, "arrows shot in 12 fights: " + arrows);
	}
}
