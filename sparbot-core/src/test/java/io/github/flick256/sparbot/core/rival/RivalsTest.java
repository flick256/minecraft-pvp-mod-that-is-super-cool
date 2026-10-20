package io.github.flick256.sparbot.core.rival;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RivalsTest {
	@Test void namedRosterHasDistinctModesAndUhcChampion() {
		assertEquals(18, Rivals.ALL.size());
		assertEquals(Rivals.ALL.size(), Rivals.ALL.stream().map(Rival::id).distinct().count());
		Rival champ = Rivals.get("swight").orElseThrow();
		assertEquals("uhc_duel", champ.mode());
		assertTrue(champ.champion());
		assertEquals("swight", champ.profile());
		assertEquals(4, champ.tier());
		for (String mode : Rivals.ALL.stream().map(Rival::mode).distinct().toList()) {
			var ladder = Rivals.inMode(mode);
			assertEquals(1, ladder.stream().filter(Rival::champion).count(), mode + " has one champion");
			assertTrue(ladder.getLast().champion(), mode + " champion is the final career rival");
			for (int tier = 0; tier < ladder.size(); tier++) assertEquals(tier, ladder.get(tier).tier(), mode + " tiers are contiguous");
		}
	}
}
