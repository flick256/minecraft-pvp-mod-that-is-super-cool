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
			assertFalse(d.how().isBlank() || d.skill().isBlank(), d.id() + " says what it trains and how");
			assertFalse(d.tips().isEmpty(), d.id() + " has tips");
			assertTrue(d.stages().get(0).cues(), d.id() + ": the first stage teaches with cues");
			assertFalse(d.stages().get(2).cues(), d.id() + ": the last stage is done without them");
			for (Drill.Stage s : d.stages()) {
				assertTrue(profiles.contains(s.profile()), d.id() + ": unknown profile " + s.profile());
				assertTrue(s.distance() >= 3 && s.distance() <= 2 * 15 - 2, d.id() + ": the bot starts inside the bay");
				assertTrue(s.reps() >= 1 && s.reps() <= 20, d.id() + ": 1-20 good reps to pass");
				assertTrue(s.seconds() >= 45 && s.seconds() <= 180, d.id() + ": a try lasts 45-180 s");
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
			"crystals", "anchors", "retotem", "carts", "mace", "punish", "firsthit"}) {
			assertTrue(Drills.get(id).isPresent(), "no drill " + id);
		}
	}

	@Test
	void repsAddUpAndStrictRepsTakeOneBack() {
		DrillTally t = new DrillTally();
		t.rep(Feedback.charge(0.95), false);
		t.rep(Feedback.charge(0.5), false);
		assertEquals(2, t.reps);
		assertEquals(1, t.goodReps, "a bad rep only costs time");
		DrillTally aim = new DrillTally();
		aim.rep(Feedback.aim(true), true);
		aim.rep(Feedback.aim(true), true);
		aim.rep(Feedback.aim(false), true);
		assertEquals(1, aim.goodReps, "a miss takes a good swing back");
		assertTrue(aim.commonestMistake().startsWith("Missed"), aim.commonestMistake());
	}

	@Test
	void feedbackSaysWhatWentWrong() {
		assertTrue(Feedback.charge(0.92).good());
		assertFalse(Feedback.charge(0.6).good());
		assertTrue(Feedback.charge(0.6).text().contains("60%"));
		assertEquals("sprint", Feedback.crit(false, 1.0, false, true, true).reason(), "sprinting spoils a crit");
		assertEquals("rising", Feedback.crit(false, 1.0, false, false, false).reason(), "on the way up isn't a crit");
		assertEquals("ground", Feedback.crit(false, 1.0, true, false, false).reason());
		assertTrue(Feedback.crit(true, 1, false, true, false).good());
		assertTrue(Feedback.jumpReset(0).good());
		assertTrue(Feedback.jumpReset(-1).good());
		assertEquals("late", Feedback.jumpReset(4).reason());
		assertTrue(Feedback.jumpReset(4).text().contains("4 ticks late"));
		assertEquals("early", Feedback.jumpReset(-5).reason());
		assertEquals("none", Feedback.jumpReset(null).reason());
		assertTrue(Feedback.reach(2.8, 2.6).good());
		assertFalse(Feedback.reach(1.5, 2.6).good());
		assertTrue(Feedback.punish(6, 10).good());
		assertFalse(Feedback.punish(16, 10).good());
		assertFalse(Feedback.punish(null, 10).good());
		assertTrue(Feedback.combo(4, 3).good());
		assertFalse(Feedback.combo(2, 3).good());
		assertTrue(Feedback.web(0.8, 1.0).good());
		assertFalse(Feedback.web(2.5, 1.0).good());
	}

	@Test
	void medalsFollowStagesPassed() {
		assertEquals("none", Drill.medal(0));
		assertEquals("bronze", Drill.medal(1));
		assertEquals("silver", Drill.medal(2));
		assertEquals("gold", Drill.medal(3));
	}
}
