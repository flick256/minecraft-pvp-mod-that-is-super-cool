package io.github.flick256.sparbot.core.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KitValidatorTest {
	private static Kit.Provenance provenance(String source, String version, String confidence, boolean verified, String reference, List<String> deviations) {
		return new Kit.Provenance(source, null, "sword", version, confidence, verified, reference, null, deviations);
	}

	private static Kit kit(Kit.Provenance provenance, Map<String, Kit.KitItem> slots) {
		return new Kit("test", "Test", "sword", provenance, null, null, slots, List.of());
	}

	@Test
	void bundledKitsAreValid() {
		Map<String, Kit> kits = Kits.loadBundled();
		assertEquals(Kits.BUNDLED_IDS.size(), kits.size());
		kits.values().forEach(k -> assertTrue(KitValidator.validate(k).isEmpty(), k.id() + ": " + KitValidator.validate(k)));
	}

	@Test
	void bundledThirdPartyKitIsNotMarkedVerified() {
		Kit recreation = Kits.loadBundled().get("mctiers_sword_recreation");
		assertFalse(recreation.provenance().verified(), "third-party recreations must stay unverified");
		assertEquals("low", recreation.provenance().confidence());
	}

	@Test
	void basicSwordKitMatchesItsSpec() {
		Kit kit = Kits.loadBundled().get("basic_sword");
		assertEquals("minecraft:diamond_helmet", kit.armor().head().id());
		assertEquals("minecraft:diamond_chestplate", kit.armor().chest().id());
		assertEquals("minecraft:diamond_leggings", kit.armor().legs().id());
		assertEquals("minecraft:diamond_boots", kit.armor().feet().id());
		assertEquals(Map.of("0", new Kit.KitItem("minecraft:diamond_sword", null, null, null)), kit.slots());
	}

	@Test
	void mctiersRecreationMatchesSourceCommand() {
		// Values copied from HelixCraft/Minecraft-PVP-Kits kits.txt "Sword Kit" /give command.
		Kit kit = Kits.loadBundled().get("mctiers_sword_recreation");
		assertEquals(Map.of("minecraft:protection", 4, "minecraft:unbreaking", 3), kit.armor().head().enchantments());
		assertEquals(Map.of("minecraft:protection", 4, "minecraft:unbreaking", 3), kit.armor().chest().enchantments());
		assertEquals(Map.of("minecraft:protection", 3, "minecraft:unbreaking", 3), kit.armor().legs().enchantments());
		assertEquals(Map.of("minecraft:protection", 3, "minecraft:unbreaking", 3), kit.armor().feet().enchantments());
		assertEquals("minecraft:diamond_sword", kit.slots().get("0").id());
		assertEquals(Map.of("minecraft:unbreaking", 3), kit.slots().get("0").enchantments());
		assertEquals(1, kit.slots().size());
	}

	@Test
	void verifiedClaimWithoutEvidenceIsRejected() {
		Kit kit = kit(provenance("some forum post", "26.2", "high", true, null, List.of()), Map.of());
		assertTrue(KitValidator.validate(kit).stream().anyMatch(e -> e.contains("verified kit needs")));
	}

	@Test
	void translatedKitMustListDeviations() {
		Kit kit = kit(provenance("user-supplied", "1.8.9", "high", true, null, List.of()), Map.of());
		assertTrue(KitValidator.validate(kit).stream().anyMatch(e -> e.contains("deviations")));
	}

	@Test
	void badSlotsAndCountsAreRejected() {
		Map<String, Kit.KitItem> slots = Map.of(
			"36", new Kit.KitItem("minecraft:stone", 1, null, null),
			"3", new Kit.KitItem("minecraft:golden_apple", 120, null, null),
			"x", new Kit.KitItem("minecraft:stone", 1, null, null),
			"4", new Kit.KitItem("diamond_sword", 1, Map.of("minecraft:sharpness", 0), null));
		List<String> errors = KitValidator.validate(kit(provenance("sparbot-original", "26.2", "high", true, null, List.of()), slots));
		assertTrue(errors.stream().anyMatch(e -> e.contains("outside 0-35")), errors.toString());
		assertTrue(errors.stream().anyMatch(e -> e.contains("count must be 1-99")), errors.toString());
		assertTrue(errors.stream().anyMatch(e -> e.contains("not a number")), errors.toString());
		assertTrue(errors.stream().anyMatch(e -> e.contains("namespaced")), errors.toString());
		assertTrue(errors.stream().anyMatch(e -> e.contains("level must be 1-255")), errors.toString());
	}
}
