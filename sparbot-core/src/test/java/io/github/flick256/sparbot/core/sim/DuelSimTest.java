package io.github.flick256.sparbot.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Rng;
import org.junit.jupiter.api.Test;

/** The simulator against numbers measured in the real game. */
class DuelSimTest {
	private static SimFighter fighter() {
		return new SimFighter(Loadout.DIAMOND_SWORD, 0, -10, 0);
	}

	private static double speedAfter(Inputs keys, int ticks) {
		SimFighter f = fighter();
		for (int i = 0; i < ticks; i++) {
			f.look(keys);
			f.move(100);
		}
		return Math.hypot(f.x - f.lastX, f.z - f.lastZ);
	}

	@Test
	void walksAndSprintsAtVanillaSpeed() {
		// 4.317 and 5.612 blocks per second.
		assertEquals(4.317 / 20, speedAfter(new Inputs(0, 0, 1, 0, false, false, false, false, false, -1), 60), 0.0005);
		assertEquals(5.612 / 20, speedAfter(new Inputs(0, 0, 1, 0, false, false, true, false, false, -1), 60), 0.0005);
	}

	@Test
	void jumpsAsHighAsVanilla() {
		SimFighter f = fighter();
		double apex = 0;
		for (int i = 0; i < 20; i++) {
			f.look(new Inputs(0, 0, 0, 0, i == 0, false, false, false, false, -1));
			f.move(100);
			apex = Math.max(apex, f.y);
		}
		assertEquals(1.2522, apex, 0.001);
		assertTrue(f.onGround, "landed again");
	}

	@Test
	void swordDamageThroughDiamondArmorMatchesVanilla() {
		SimFighter a = new SimFighter(Loadout.DIAMOND_SWORD, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.DIAMOND_SWORD, 0, 2.0, 180);
		a.click(b, new Rng(1));
		// 7 damage; armor 20, toughness 8: 20 - 7 / 4 = 18.25 effective armor, so 1 - 18.25 / 25 of it gets through.
		assertEquals(7 * (1 - 18.25 / 25), 20 - b.health, 1e-4);
		assertEquals(1, a.hits);
		assertTrue(b.pendingMotion != null && b.pendingMotion.z() > 0.3, "knocked back, away from the attacker");
		// Straight away again: invulnerable and the charge is gone, so nothing gets through.
		a.click(b, new Rng(1));
		assertEquals(7 * (1 - 18.25 / 25), 20 - b.health, 1e-4);
	}

	@Test
	void aFallingUnsprintedFullHitCrits() {
		SimFighter a = new SimFighter(Loadout.DIAMOND_SWORD, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.DIAMOND_SWORD, 0, 2.0, 180);
		a.onGround = false;
		a.fallDistance = 0.3;
		a.y = 0.5;
		a.pitch = 15;
		a.click(b, new Rng(1));
		assertEquals(1, a.crits);
		assertEquals(10.5 * (1 - (20 - 10.5 / 4) / 25), 20 - b.health, 1e-4);
	}

	@Test
	void theScriptedProBeatsTheScriptedBeginner() {
		var presets = io.github.flick256.sparbot.core.profile.SkillProfiles.loadPresets();
		double score = Tournament.score(Tournament.scripted(presets.get("pro")), Tournament.scripted(presets.get("beginner")), 20, 7);
		assertTrue(score > 0.8, "pro's score against a beginner: " + score);
	}
}
