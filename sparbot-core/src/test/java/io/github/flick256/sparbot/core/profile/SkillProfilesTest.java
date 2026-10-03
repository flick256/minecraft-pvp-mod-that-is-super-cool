package io.github.flick256.sparbot.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillProfilesTest {
	@Test
	void allPresetsLoadAndValidate() {
		Map<String, SkillProfile> presets = SkillProfiles.loadPresets();
		assertEquals(SkillProfiles.PRESET_IDS, new ArrayList<>(presets.keySet()));
		presets.values().forEach(p -> assertTrue(p.validate().isEmpty(), p.id() + ": " + p.validate()));
	}

	@Test
	void presetsGetStrictlyStrongerFromBeginnerToPro() {
		List<SkillProfile> ordered = new ArrayList<>(SkillProfiles.loadPresets().values());
		for (int i = 1; i < ordered.size(); i++) {
			SkillProfile weaker = ordered.get(i - 1);
			SkillProfile stronger = ordered.get(i);
			String pair = weaker.id() + " -> " + stronger.id();
			assertTrue(stronger.reactionTimeMs().mean() < weaker.reactionTimeMs().mean(), pair + " reaction");
			assertTrue(stronger.aim().maxTurnDegPerTick() > weaker.aim().maxTurnDegPerTick(), pair + " turn speed");
			assertTrue(stronger.aim().jitterDeg() < weaker.aim().jitterDeg(), pair + " jitter");
			assertTrue(stronger.clicking().cooldownDiscipline() > weaker.clicking().cooldownDiscipline(), pair + " discipline");
			assertTrue(stronger.technique().critSkill() > weaker.technique().critSkill(), pair + " crits");
			assertTrue(stronger.mistakeRate() < weaker.mistakeRate(), pair + " mistakes");
			assertTrue(Math.abs(stronger.reach().rangeErrorBlocks().mean()) < Math.abs(weaker.reach().rangeErrorBlocks().mean()), pair + " reach");
		}
	}

	@Test
	void superhumanReactionIsRejected() {
		String json = SkillProfiles.toJson(SkillProfiles.loadPresets().get("pro"))
			.replace("\"min\": 130.0", "\"min\": 20.0");
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SkillProfiles.parse(new StringReader(json)));
		assertTrue(e.getMessage().contains("reactionTimeMs"), e.getMessage());
	}

	@Test
	void clickingAboveTwentyCpsIsRejected() {
		String json = SkillProfiles.toJson(SkillProfiles.loadPresets().get("pro"))
			.replace("\"max\": 17.0", "\"max\": 40.0");
		assertThrows(IllegalArgumentException.class, () -> SkillProfiles.parse(new StringReader(json)));
	}

	@Test
	void malformedJsonGivesClearError() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SkillProfiles.parse(new StringReader("{ nope")));
		assertTrue(e.getMessage().startsWith("Malformed"), e.getMessage());
	}
}
