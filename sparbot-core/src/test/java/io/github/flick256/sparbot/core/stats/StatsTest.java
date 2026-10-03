package io.github.flick256.sparbot.core.stats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatsTest {
	@Test
	void eloExpectedScoreIsSymmetric() {
		assertEquals(0.5, Elo.expected(1000, 1000), 1e-9);
		assertEquals(1.0, Elo.expected(1400, 1000) + Elo.expected(1000, 1400), 1e-9);
		assertEquals(0.909, Elo.expected(1400, 1000), 1e-3);
	}

	@Test
	void eloUpdateConservesPoints() {
		double[] after = Elo.update(1200, 1000, 0, Elo.DEFAULT_K);
		assertEquals(2200, after[0] + after[1], 1e-9);
		assertTrue(after[0] < 1200, "favourite losing must drop");
	}

	@Test
	void fightStatsTrackCombosAndRates() {
		FightStats stats = new FightStats();
		for (int i = 0; i < 4; i++) {
			stats.recordSwing();
		}
		stats.recordHit(5, true);
		stats.recordHit(4, false);
		stats.recordHit(6, true);
		stats.recordDamageTaken(3);
		stats.recordHit(4, false);
		stats.recordDeath();
		assertEquals(3, stats.longestCombo());
		assertEquals(1.0, stats.hitRate(), 1e-9);
		assertEquals(0.5, stats.critRate(), 1e-9);
		assertEquals(19, stats.damageDealt(), 1e-9);
		assertEquals(1, stats.deaths());
	}
}
