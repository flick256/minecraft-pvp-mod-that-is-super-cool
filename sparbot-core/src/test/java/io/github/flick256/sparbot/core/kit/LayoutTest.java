package io.github.flick256.sparbot.core.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LayoutTest {
	private static final Kit.KitItem SWORD = new Kit.KitItem("minecraft:diamond_sword", null, null, null);
	private static final Kit.KitItem GAPPLES = new Kit.KitItem("minecraft:golden_apple", 8, null, null);
	private static final Kit.KitItem PEARLS = new Kit.KitItem("minecraft:ender_pearl", 16, null, null);
	private static final Kit.KitItem TOTEM = new Kit.KitItem("minecraft:totem_of_undying", null, null, null);
	private static final Kit.KitItem SHIELD = new Kit.KitItem("minecraft:shield", null, null, null);

	private static Kit kit() {
		return new Kit("duel", "Duel", "sword", new Kit.Provenance("sparbot-original", null, "sword", "26.2", "high", true, null, null, List.of()),
			null, SHIELD, Map.of("0", SWORD, "1", GAPPLES, "2", PEARLS, "20", TOTEM), List.of());
	}

	@Test
	void movesItemsToTheLayoutSlots() {
		Layout layout = new Layout("mine", "duel", Map.of("8", "minecraft:golden_apple", "3", "minecraft:diamond_sword"), null);
		Kit arranged = layout.arrange(kit());
		assertEquals(SWORD, arranged.slots().get("3"));
		assertEquals(GAPPLES, arranged.slots().get("8"));
		assertEquals(PEARLS, arranged.slots().get("2"), "unmentioned items keep their slot");
		assertEquals(TOTEM, arranged.slots().get("20"));
		assertEquals(SHIELD, arranged.offhand());
	}

	@Test
	void offhandPreferenceSwapsWithTheKitOffhand() {
		Layout layout = new Layout("totem-off", "duel", Map.of(), "minecraft:totem_of_undying");
		Kit arranged = layout.arrange(kit());
		assertEquals(TOTEM, arranged.offhand());
		assertEquals(SHIELD, arranged.slots().get("20"), "the old offhand item takes the totem's slot");
	}

	@Test
	void neverCreatesOrLosesItems() {
		Layout layout = new Layout("chaos", "duel", Map.of("1", "minecraft:ender_pearl", "2", "minecraft:golden_apple", "0", "minecraft:golden_apple",
			"5", "minecraft:not_in_kit"), "minecraft:diamond_sword");
		Kit original = kit();
		Kit arranged = layout.arrange(original);
		assertEquals(multiset(original), multiset(arranged));
	}

	@Test
	void displacedItemsFindAFreeSlot() {
		// The sword wants slot 1, where the gapples were; gapples must not be lost.
		Layout layout = new Layout("x", "duel", Map.of("1", "minecraft:diamond_sword", "0", "minecraft:ender_pearl"), null);
		Kit arranged = layout.arrange(kit());
		assertEquals(SWORD, arranged.slots().get("1"));
		assertEquals(PEARLS, arranged.slots().get("0"));
		assertTrue(arranged.slots().containsValue(GAPPLES));
		assertEquals(4, arranged.slots().size());
	}

	@Test
	void layoutJsonRoundTrips() {
		Layout layout = new Layout("mine", "duel", Map.of("8", "minecraft:golden_apple"), "minecraft:totem_of_undying");
		assertEquals(layout, Kits.parseLayout(new StringReader(Kits.toJson(layout))));
	}

	@Test
	void invalidLayoutsAreRejected() {
		Layout bad = new Layout("Bad Id", null, Map.of("40", "minecraft:stone", "x", "stone"), null);
		List<String> errors = bad.validate();
		assertTrue(errors.size() >= 4, errors.toString());
	}

	@Test
	void componentsAreWrittenWithoutBrackets() {
		Kit kit = new Kit("c", "C", "x", new Kit.Provenance("sparbot-original", null, "x", "26.2", "high", true, null, null, List.of()), null, null,
			Map.of("0", new Kit.KitItem("minecraft:stick", null, null, null, "[minecraft:damage=1]")), List.of());
		assertTrue(KitValidator.validate(kit).stream().anyMatch(e -> e.contains("brackets")));
	}

	private static List<String> multiset(Kit kit) {
		List<String> items = new ArrayList<>();
		kit.slots().values().forEach(i -> items.add(i.id() + "x" + i.countOrDefault()));
		if (kit.offhand() != null) {
			items.add(kit.offhand().id() + "x" + kit.offhand().countOrDefault());
		}
		items.sort(String::compareTo);
		return items;
	}
}
