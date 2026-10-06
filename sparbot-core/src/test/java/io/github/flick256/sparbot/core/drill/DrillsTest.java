package io.github.flick256.sparbot.core.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.kit.Kits;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DrillsTest {
	@Test
	void everyDrillIsComplete() {
		Set<String> ids = new HashSet<>();
		Set<String> profiles = SkillProfiles.PRESET_IDS.stream().collect(java.util.stream.Collectors.toSet());
		for (Drill d : Drills.ALL) {
			assertTrue(ids.add(d.id()), "two drills are called " + d.id());
			assertTrue(d.id().matches("[a-z0-9_]+"), d.id());
			assertEquals(3, d.stages().size(), d.id() + " has three stages (bronze, silver, gold)");
			assertTrue(Kits.BUNDLED_IDS.contains(d.kit()), d.id() + ": unknown kit " + d.kit());
			assertTrue(Kits.BUNDLED_IDS.contains(d.botKit()), d.id() + ": unknown bot kit " + d.botKit());
			assertTrue(d.seconds() >= 20 && d.seconds() <= 60, d.id() + " runs 20-60 s a stage");
			assertFalse(d.how().isBlank() || d.skill().isBlank(), d.id() + " says what it trains and how");
			for (Drill.Stage s : d.stages()) {
				assertTrue(profiles.contains(s.profile()), d.id() + ": unknown profile " + s.profile());
				assertTrue(s.distance() >= 3 && s.distance() <= 2 * 15 - 2, d.id() + ": the bot starts inside the bay");
				assertTrue(s.pass() > 0, d.id() + ": a pass mark");
				for (Map.Entry<String, Double> w : s.tactics().entrySet()) {
					assertTrue(Playstyle.TACTICS.contains(w.getKey()), d.id() + ": unknown tactic " + w.getKey());
					assertTrue(w.getValue() >= 0 && w.getValue() <= 3, d.id() + ": tactic weight 0-3");
				}
				for (String t : s.techniquesOff()) {
					assertTrue(Technique.byId(t).isPresent(), d.id() + ": unknown technique " + t);
				}
				assertTrue(new Playstyle("drill", "Drill", "", s.tactics(), Map.of(), 0).validate().isEmpty(), d.id() + ": a valid playstyle");
			}
		}
	}

	@Test
	void everySkillTheUserAskedForHasADrill() {
		for (String id : new String[] {"shieldstun", "pots", "jumpreset", "wtap", "stap", "crits", "combo", "blockhit", "webs", "lava", "bow", "rod",
			"crystals", "anchors", "retotem", "carts", "mace"}) {
			assertTrue(Drills.get(id).isPresent(), "no drill " + id);
		}
	}

	@Test
	void scoresComeFromTheTally() {
		DrillTally t = new DrillTally();
		t.hit(1.0, true, true, 2.9);
		t.hit(0.5, false, false, 2.1);
		t.hit(0.95, true, false, 2.6);
		assertEquals(2 / 3.0, Drill.Metric.FULL_CHARGE.value(t), 1e-9);
		assertEquals(2 / 3.0, Drill.Metric.CRITS.value(t), 1e-9);
		assertEquals(1 / 3.0, Drill.Metric.SPRINT_HITS.value(t), 1e-9);
		assertEquals((2.9 + 2.1 + 2.6) / 3, Drill.Metric.REACH.value(t), 1e-9);
		assertEquals(3, Drill.Metric.COMBO.value(t));
		t.hitTaken(true);
		t.hit(1, false, false, 3);
		assertEquals(3, Drill.Metric.COMBO.value(t), "a hit taken breaks the combo");
		assertEquals(0.8, Drill.Metric.HIT_SHARE.value(t), 1e-9);
	}

	@Test
	void tooFewTriesNeverPass() {
		DrillTally t = new DrillTally();
		t.hit(1, false, false, 3);
		assertEquals(1.0, Drill.Metric.FULL_CHARGE.value(t));
		assertFalse(Drill.Metric.FULL_CHARGE.passes(t, 0.5), "one perfect hit isn't a pass");
		for (int i = 0; i < 6; i++) {
			t.hit(1, false, false, 3);
		}
		assertTrue(Drill.Metric.FULL_CHARGE.passes(t, 0.9));
	}

	@Test
	void fasterIsBetterForEscapesAndTotems() {
		DrillTally t = new DrillTally();
		assertFalse(Drill.Metric.WEB_ESCAPE.passes(t, 1.5), "never caught: nothing to score");
		t.webEscapes = 2;
		t.webTicks = 30;
		assertEquals(0.75, Drill.Metric.WEB_ESCAPE.value(t), 1e-9);
		assertTrue(Drill.Metric.WEB_ESCAPE.passes(t, 1.0));
		assertFalse(Drill.Metric.WEB_ESCAPE.passes(t, 0.5));
		assertEquals("0.8 s", Drill.Metric.WEB_ESCAPE.format(0.75));
	}

	@Test
	void medalsFollowStagesPassed() {
		assertEquals("none", Drill.medal(0));
		assertEquals("bronze", Drill.medal(1));
		assertEquals("silver", Drill.medal(2));
		assertEquals("gold", Drill.medal(3));
	}
}
