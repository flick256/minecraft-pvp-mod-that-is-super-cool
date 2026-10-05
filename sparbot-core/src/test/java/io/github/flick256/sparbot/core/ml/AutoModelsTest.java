package io.github.flick256.sparbot.core.ml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class AutoModelsTest {
	@Test
	void uhcBotsGetTheLearnedBrainForTheirTier() {
		List<String> all = List.of("sword", "uhc", "uhc_demon");
		assertEquals("uhc", AutoModels.choose("uhc", "advanced", all));
		assertEquals("uhc", AutoModels.choose("uhc", "pro", all));
		assertEquals("uhc_demon", AutoModels.choose("uhc", "demon", all));
		assertEquals("uhc", AutoModels.choose("uhc", "demon", List.of("uhc")), "no demon brain: the UHC one");
		assertNull(AutoModels.choose("sword", "pro", all), "other kits keep the scripted brain");
		assertNull(AutoModels.choose("uhc", "pro", List.of("sword")));
	}
}
