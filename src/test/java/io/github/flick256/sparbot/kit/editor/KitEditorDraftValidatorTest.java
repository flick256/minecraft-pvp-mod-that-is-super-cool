package io.github.flick256.sparbot.kit.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class KitEditorDraftValidatorTest {
	@Test
	void editorDraftRoundTripsAllItemFieldsAndUnverifiedProvenance() {
		Kit.KitItem sword = new Kit.KitItem("minecraft:diamond_sword", 1, Map.of("minecraft:sharpness", 5), null,
			"minecraft:custom_name='\"Practice blade\"',minecraft:damage=17");
		Kit.Provenance provenance = new Kit.Provenance("user-supplied", null, "custom", "26.2", "low", false,
			"Edited from a preset in the kit editor", null, List.of());
		Kit kit = new Kit("practice_blade", "Practice blade", "custom", provenance,
			new Kit.Armor(null, null, null, null), new Kit.KitItem("minecraft:shield", null, null, null), Map.of("0", sword), List.of());

		Kit roundTrip = Kits.parse(new StringReader(Kits.toJson(kit)));

		assertEquals(kit, roundTrip);
		assertTrue(KitEditorDraftValidator.validate(roundTrip).isEmpty());
		assertEquals(sword.components(), roundTrip.slots().get("0").components());
		assertEquals(sword.enchantments(), roundTrip.slots().get("0").enchantments());
	}

	@Test
	void editorRejectsForgedVerifiedProvenanceAndMalformedIds() {
		Kit.Provenance forged = new Kit.Provenance("user-supplied", null, "custom", "26.2", "high", true,
			"typed into editor", null, List.of());
		Kit kit = new Kit("Bad ID", "Test", "custom", forged, null, null, Map.of(), List.of());

		List<String> errors = KitEditorDraftValidator.validate(kit);

		assertFalse(errors.isEmpty());
		assertTrue(errors.stream().anyMatch(error -> error.contains("id must be lowercase")));
		assertTrue(errors.stream().anyMatch(error -> error.contains("unverified user-supplied provenance")));
	}
}
