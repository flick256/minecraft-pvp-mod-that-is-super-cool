package io.github.flick256.sparbot.core.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CoachTest {
	@Test
	void namesTheWorstSkillFirstWithItsDrill() {
		DrillTally t = new DrillTally();
		for (int i = 0; i < 10; i++) {
			// Mostly half-charged hits, all sprint hits, from a fair distance.
			t.hit(i < 7 ? 0.5 : 1.0, i % 3 == 0, true, 2.8);
		}
		List<String> lines = Coach.lines(t);
		assertTrue(lines.get(0).contains("below full charge") && lines.get(0).contains("Full charge"), lines.toString());
		assertTrue(lines.size() <= Coach.MAX_LINES);
	}

	@Test
	void slowWebEscapesAndTotemsAreCalledOut() {
		DrillTally t = new DrillTally();
		t.webEscapes = 2;
		t.webTicks = 100;
		t.retotems = 1;
		t.retotemTicks = 30;
		List<String> lines = Coach.lines(t);
		assertTrue(lines.stream().anyMatch(l -> l.contains("web")), lines.toString());
		assertTrue(lines.stream().anyMatch(l -> l.contains("totem")), lines.toString());
	}

	@Test
	void aCleanFightGetsPraise() {
		DrillTally t = new DrillTally();
		for (int i = 0; i < 12; i++) {
			t.hit(1.0, i % 2 == 0, true, 2.8);
		}
		assertEquals(List.of("A clean fight: nothing stood out. Try a harder bot, or go for gold in the drills."), Coach.lines(t));
	}

	@Test
	void numbersLeaveOutWhatDidNotHappen() {
		DrillTally t = new DrillTally();
		t.hit(1.0, true, false, 2.7);
		Map<String, String> n = Coach.numbers(t);
		assertEquals("100%", n.get("Full charge"));
		assertEquals("2.70", n.get("Reach"));
		assertTrue(!n.containsKey("Pot accuracy") && !n.containsKey("Out of webs"));
	}
}
