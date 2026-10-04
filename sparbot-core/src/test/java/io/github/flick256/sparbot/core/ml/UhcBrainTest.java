package io.github.flick256.sparbot.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
