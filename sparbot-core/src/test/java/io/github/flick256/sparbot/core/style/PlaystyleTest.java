package io.github.flick256.sparbot.core.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.brain.DuelBrain;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlaystyleTest {
	private static final Map<String, Playstyle> PRESETS = Playstyles.loadPresets();

	@Test
	void presetsLoadAndValidate() {
		assertEquals(Playstyles.PRESET_IDS, List.copyOf(PRESETS.keySet()));
		PRESETS.values().forEach(s -> assertTrue(s.validate().isEmpty(), s.id() + ": " + s.validate()));
	}

	@Test
	void mixesAverageWeightsBiasesAndRangeByShare() {
		Playstyle mix = Playstyles.resolve("aggressive_rusher:0.7,kiter:0.3", PRESETS);
		Playstyle rusher = PRESETS.get("aggressive_rusher");
		Playstyle kiter = PRESETS.get("kiter");
		assertEquals(0.7 * rusher.weight("ranged") + 0.3 * kiter.weight("ranged"), mix.weight("ranged"), 1e-9);
		assertEquals(0.7 * rusher.weight("engage") + 0.3 * kiter.weight("engage"), mix.weight("engage"), 1e-9);
		assertEquals(0.3 * kiter.preferredRange(), mix.preferredRange(), 1e-9);
		assertEquals(0.7 * rusher.skillBias().get("pearlSkill"), mix.skillBias().get("pearlSkill"), 1e-9);
		assertTrue(mix.validate().isEmpty(), mix.validate().toString());
	}

	@Test
	void sharesNeedNotAddUpToOne() {
		Playstyle a = Playstyles.resolve("kiter:2,balanced:2", PRESETS);
		Playstyle b = Playstyles.resolve("kiter:0.5,balanced:0.5", PRESETS);
		assertEquals(a.weight("ranged"), b.weight("ranged"), 1e-9);
	}

	@Test
	void badSpecsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> Playstyles.resolve("nope", PRESETS));
		assertThrows(IllegalArgumentException.class, () -> Playstyles.resolve("kiter:x", PRESETS));
		assertThrows(IllegalArgumentException.class, () -> Playstyles.resolve("kiter:-1", PRESETS));
		Playstyle bad = new Playstyle("bad", "Bad", "", Map.of("teleport", 1.0, "engage", 9.0), Map.of("flying", 0.5), 99);
		assertTrue(bad.validate().size() >= 4, bad.validate().toString());
	}

	@Test
	void biasesShiftAndClampSkills() {
		SkillProfile pro = TestFixtures.preset("pro");
		SkillProfile wtap = PRESETS.get("wtap_combo").applyTo(pro);
		assertEquals(Math.min(1, pro.technique().wTapSkill() + 0.3), wtap.technique().wTapSkill(), 1e-9);
		assertEquals(1.0, PRESETS.get("pearl_aggro").applyTo(pro).items().pearlSkill(), 1e-9, "clamped at 1");
	}

	@Test
	void rusherKeepsFightingWhereABalancedBotRetreats() {
		assertEquals("retreat", tacticAfter(PRESETS.get("balanced"), 3, TestFixtures.inventory(), 4));
		assertEquals("engage", tacticAfter(PRESETS.get("aggressive_rusher"), 3, TestFixtures.inventory(), 4));
	}

	@Test
	void kiterBacksOffToBowRange() {
		InventoryState bow = TestFixtures.inventory(2, TestFixtures.item(ItemKind.BOW, "minecraft:bow", 1, 1),
			3, TestFixtures.item(ItemKind.ARROW, "minecraft:arrow", 32, 1));
		assertEquals("kite", tacticAfter(PRESETS.get("kiter"), 20, bow, 6));
		assertEquals("engage", tacticAfter(PRESETS.get("balanced"), 20, bow, 6));
	}

	private static String tacticAfter(Playstyle style, float health, InventoryState inv, double distance) {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), style, 3);
		for (int t = 0; t < 20; t++) {
			SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, health, 1.0F, true, TestFixtures.flatGround(), inv, 20);
			brain.act(new Observation(t, self, TestFixtures.target(new Vec3(0, 0, distance), 0)));
		}
		return brain.lastTrace().tactic();
	}
}
