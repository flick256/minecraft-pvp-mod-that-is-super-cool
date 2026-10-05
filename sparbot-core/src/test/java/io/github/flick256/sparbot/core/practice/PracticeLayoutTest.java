package io.github.flick256.sparbot.core.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.match.GameModes;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PracticeLayoutTest {
	@Test
	void everyBundledModeHasAnArena() {
		Set<String> placed = new HashSet<>();
		PracticeLayout.SITES.forEach(s -> placed.addAll(s.modes()));
		for (String mode : GameModes.BUNDLED_IDS) {
			assertTrue(placed.contains(mode), mode + " has no practice arena");
		}
		assertEquals(PracticeLayout.CRYSTAL, PracticeLayout.siteFor("crystal_duel"));
		assertEquals(PracticeLayout.SWORD, PracticeLayout.siteFor("some_custom_mode"));
	}

	@Test
	void arenasFitTheSnapshotLimitAndDontOverlap() {
		for (PracticeLayout.Site a : PracticeLayout.SITES) {
			assertTrue(a.volume() <= 2_000_000, a.id() + " is " + a.volume() + " blocks");
			// Clear of the hub plaza, and of each other by a wide margin (blasts, lava).
			assertTrue(Math.max(Math.abs(a.centerX()), Math.abs(a.centerZ())) - a.radius() > PracticeLayout.HUB_RADIUS + 40, a.id());
			for (PracticeLayout.Site b : PracticeLayout.SITES) {
				if (a != b) {
					boolean apart = a.maxX() + 30 < b.minX() || b.maxX() + 30 < a.minX() || a.maxZ() + 30 < b.minZ() || b.maxZ() + 30 < a.minZ();
					assertTrue(apart, a.id() + " and " + b.id() + " are too close");
				}
			}
		}
	}

	@Test
	void theCrystalDesertGoesDownToBedrock() {
		assertEquals(PracticeLayout.BEDROCK, PracticeLayout.CRYSTAL.minY());
	}

	@Test
	void theMeadowRollsButBothSpawnsAreLevel() {
		Set<Integer> heights = new HashSet<>();
		for (int dx = -30; dx <= 30; dx++) {
			for (int dz = -30; dz <= 30; dz++) {
				int h = PracticeLayout.meadowHeight(dx, dz);
				assertTrue(h >= 0 && h <= 3);
				heights.add(h);
			}
		}
		assertTrue(heights.size() >= 3, "the ground rolls: " + heights);
		int off = PracticeLayout.UHC.spawnOffset();
		for (int d = -2; d <= 2; d++) {
			assertEquals(PracticeLayout.spawnHeight(), PracticeLayout.meadowHeight(d, off + d));
			assertEquals(PracticeLayout.spawnHeight(), PracticeLayout.meadowHeight(d, -off + d));
		}
	}

	@Test
	void hubPadsAreOnTheCircleAndApart() {
		Set<String> seen = new HashSet<>();
		for (PracticeLayout.Site s : PracticeLayout.SITES) {
			int[] p = PracticeLayout.pad(s);
			double r = Math.hypot(p[0], p[1]);
			assertTrue(Math.abs(r - PracticeLayout.PAD_RADIUS) < 1.0, s.id() + " pad at radius " + r);
			assertFalse(!seen.add(p[0] + "," + p[1]), "two pads on one block");
		}
	}
}
