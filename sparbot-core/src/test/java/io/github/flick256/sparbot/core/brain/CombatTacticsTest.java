package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.TargetState;
import org.junit.jupiter.api.Test;

class CombatTacticsTest {
	private static final ItemInfo BOW = TestFixtures.item(ItemKind.BOW, "minecraft:bow", 1, 1);
	private static final ItemInfo ARROWS = TestFixtures.item(ItemKind.ARROW, "minecraft:arrow", 32, 1);
	private static final ItemInfo PEARLS = TestFixtures.item(ItemKind.ENDER_PEARL, "minecraft:ender_pearl", 8, 1);

	@Test
	void retotemsFromTheInventoryWithOneClick() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(20, TestFixtures.TOTEM), null);
		for (int i = 0; i < 40 && !h.offhand.is(ItemKind.TOTEM); i++) {
			h.tick();
		}
		assertTrue(h.offhand.is(ItemKind.TOTEM), "totem in offhand");
		assertEquals(1, h.history.stream().filter(in -> in.inventoryClick() != null).count(), "exactly one click");
		long openBeforeClick = h.history.stream().takeWhile(in -> in.inventoryClick() == null).filter(Inputs::inventoryOpen).count();
		assertTrue(openBeforeClick >= 2, "inventory opened before clicking");
		for (int i = 0; i < 20; i++) {
			h.tick();
		}
		assertFalse(h.history.get(h.history.size() - 1).inventoryOpen(), "inventory closed afterwards");
	}

	@Test
	void retotemsFromTheHotbarWithTheSwapKey() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(5, TestFixtures.TOTEM), null);
		for (int i = 0; i < 40 && !h.offhand.is(ItemKind.TOTEM); i++) {
			h.tick();
		}
		assertTrue(h.offhand.is(ItemKind.TOTEM));
		assertTrue(h.any(Inputs::swapOffhand), "used the swap-hands key");
		assertFalse(h.any(Inputs::inventoryOpen), "no inventory needed");
	}

	@Test
	void drawsTheBowFullyThenReleasesAtRange() {
		InventoryState inv = TestFixtures.inventory(2, BOW, 3, ARROWS);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, TestFixtures.target(new Vec3(0, 0, 18), 0));
		int releasedAt = -1;
		int drawn = 0;
		for (int i = 0; i < 120; i++) {
			int before = h.useTicks;
			h.tick();
			if (before > 0 && h.useTicks == 0 && h.slots[h.selected].is(ItemKind.BOW)) {
				releasedAt = i;
				drawn = before;
				break;
			}
		}
		assertTrue(releasedAt > 0, "the bow was released");
		assertTrue(drawn >= 20, "released only at full draw, after " + drawn + " ticks");
		assertEquals("ranged", h.brain.lastTrace().tactic());
	}

	@Test
	void switchesToTheAxeAgainstARaisedShield() {
		InventoryState inv = TestFixtures.inventory(1, TestFixtures.AXE);
		TargetState blocking = TestFixtures.target(new Vec3(0, 0, 2.5), 0, ItemKind.SWORD, ItemKind.SHIELD, ItemKind.SHIELD, true);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, blocking);
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertTrue(h.any(in -> in.hotbarSlot() == 1), "switched to the axe slot");
		assertTrue(h.any(Inputs::attack), "and swung it");
	}

	@Test
	void blockHitsBetweenSwingsAndLowersTheShieldToAttack() {
		InventoryState inv = TestFixtures.inventory(InventoryState.OFFHAND_BUTTON, TestFixtures.SHIELD);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, TestFixtures.target(new Vec3(0, 0, 2.6), 0));
		h.attackStrength = 0.3F;
		for (int i = 0; i < 60; i++) {
			h.tick();
		}
		assertTrue(h.any(Inputs::use), "raised the shield while recharging");
		assertFalse(h.any(in -> in.use() && in.attack()), "never clicks with the shield up");
		h.attackStrength = 1.0F;
		boolean attacked = false;
		for (int i = 0; i < 40 && !attacked; i++) {
			attacked = h.tick().attack();
		}
		assertTrue(attacked, "lowers the shield and swings once charged");
	}

	@Test
	void walksInBehindTheShieldOnAChargedSwordsman() {
		// A charged opponent with a sword a step out of reach: shield up and walk in, no swing into it.
		InventoryState inv = TestFixtures.inventory(InventoryState.OFFHAND_BUTTON, TestFixtures.SHIELD);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, TestFixtures.target(new Vec3(0, 0, 3.8), 0));
		boolean countered = false;
		for (int i = 0; i < 80 && !countered; i++) {
			Inputs in = h.tick();
			countered = in.use() && in.forward() > 0 && !in.attack();
		}
		assertTrue(countered, "walked in with the shield up");
	}

	@Test
	void guardsWithTheShieldAgainstAnArcher() {
		InventoryState inv = TestFixtures.inventory(InventoryState.OFFHAND_BUTTON, TestFixtures.SHIELD);
		TargetState archer = TestFixtures.target(new Vec3(0, 0, 14), 0, ItemKind.BOW, ItemKind.EMPTY, ItemKind.BOW, false);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, archer);
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertEquals("guard", h.brain.lastTrace().tactic());
		assertTrue(h.history.get(h.history.size() - 1).use(), "shield up");
	}

	@Test
	void throwsAPearlToCloseALongGap() {
		InventoryState inv = TestFixtures.inventory(4, PEARLS);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), inv, TestFixtures.target(new Vec3(0, 0, 28), 0));
		boolean thrown = false;
		for (int i = 0; i < 400 && !thrown; i++) {
			Inputs in = h.tick();
			thrown = in.use() && h.slots[h.selected].is(ItemKind.ENDER_PEARL);
		}
		assertTrue(thrown, "threw a pearl");
	}

	@Test
	void wornOutArmorMakesTheBotDisengageEarlier() {
		// Pro panics below 20% health; with near-broken armor it should already retreat at 25%.
		ItemInfo worn = new ItemInfo(ItemKind.ARMOR, "minecraft:diamond_chestplate", 1, 1, 0.1, false, false, null);
		ItemInfo fresh = new ItemInfo(ItemKind.ARMOR, "minecraft:diamond_chestplate", 1, 1, 1.0, false, false, null);
		for (boolean wornOut : new boolean[] {false, true}) {
			InventoryState base = TestFixtures.inventory();
			ItemInfo piece = wornOut ? worn : fresh;
			InventoryState inv = new InventoryState(base.slots(), base.offhand(), new ItemInfo[] {piece, piece, piece, piece}, 0, false, false, 0,
				ItemKind.EMPTY, false);
			DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 5);
			for (int t = 0; t < 20; t++) {
				brain.act(new io.github.flick256.sparbot.core.sense.Observation(t,
					TestFixtures.self(Vec3.ZERO, 0, 0, 5, 1.0F, true, TestFixtures.flatGround(), inv, 20), TestFixtures.target(new Vec3(0, 0, 4), 0)));
			}
			assertEquals(wornOut ? "retreat" : "engage", brain.lastTrace().tactic(), "worn armor: " + wornOut);
		}
	}
}
