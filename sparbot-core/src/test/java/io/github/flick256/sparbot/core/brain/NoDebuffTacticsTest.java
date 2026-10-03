package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.item.Potions;
import io.github.flick256.sparbot.core.math.Vec3;
import org.junit.jupiter.api.Test;

class NoDebuffTacticsTest {
	private static final ItemInfo POT = new ItemInfo(ItemKind.SPLASH_POTION, "minecraft:splash_potion", 1, 1, 1, false, false, "minecraft:strong_healing");
	private static final ItemInfo SPEED = new ItemInfo(ItemKind.DRINK_POTION, "minecraft:potion", 1, 1, 1, false, false, "minecraft:strong_swiftness");

	@Test
	void potionsKnowTheirEffects() {
		assertEquals("minecraft:instant_health", Potions.effectOf("minecraft:strong_healing"));
		assertEquals("minecraft:speed", Potions.effectOf("minecraft:long_swiftness"));
		assertTrue(Potions.isHealingSplash(POT));
		assertTrue(Potions.isBuff(SPEED));
		assertFalse(Potions.isBuff(POT));
	}

	@Test
	void potsDownAtItsFeetWhenLow() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(4, POT, 5, POT), TestFixtures.target(new Vec3(0, 0, 6), 0));
		h.health = 9;
		boolean thrown = false;
		for (int i = 0; i < 40 && !thrown; i++) {
			Inputs in = h.tick();
			thrown = in.use() && Potions.isHealingSplash(h.slots[h.selected]);
			if (thrown) {
				assertTrue(h.pitch >= 80, "thrown looking down, pitch " + h.pitch);
			}
		}
		assertTrue(thrown, "threw a healing potion");
		assertEquals("pot", h.brain.lastTrace().tactic());
	}

	@Test
	void doesNotPotAtHighHealth() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(4, POT), TestFixtures.target(new Vec3(0, 0, 6), 0));
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.any(in -> in.use() && Potions.isHealingSplash(h.slots[h.selected])));
	}

	@Test
	void refillsTheHotbarFromTheInventoryWhenSafe() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(20, POT, 21, POT, 22, POT), null);
		for (int i = 0; i < 60; i++) {
			h.tick();
		}
		int potsInHotbar = 0;
		for (int i = 0; i < 9; i++) {
			if (Potions.isHealingSplash(h.slots[i])) {
				potsInHotbar++;
			}
		}
		assertEquals(3, potsInHotbar, "all three pots moved to the hotbar");
		assertFalse(h.history.get(h.history.size() - 1).inventoryOpen(), "closed the inventory afterwards");
	}

	@Test
	void refillWaitsWhileTheOpponentIsClose() {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(20, POT), TestFixtures.target(new Vec3(0, 0, 2.5), 0));
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::inventoryOpen), "never opens the inventory in melee range");
	}

	@Test
	void drinksSpeedWhenItWoreOff() {
		ItemInfo twoSpeed = new ItemInfo(ItemKind.DRINK_POTION, "minecraft:potion", 2, 1, 1, false, false, "minecraft:strong_swiftness");
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(2, twoSpeed), TestFixtures.target(new Vec3(0, 0, 15), 0));
		for (int i = 0; i < 20; i++) {
			h.tick();
		}
		assertEquals("buff", h.brain.lastTrace().tactic());
		assertTrue(h.useTicks > 0 && h.slots[h.selected].is(ItemKind.DRINK_POTION), "drinking the speed potion");
		for (int i = 0; i < 40 && h.slots[2].count() == 2; i++) {
			h.tick();
		}
		assertEquals(1, h.slots[2].count(), "one potion drunk");
		h.effects.add(new io.github.flick256.sparbot.core.sense.EffectInfo("minecraft:speed", 1, 3600));
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertEquals(1, h.slots[2].count(), "the second is kept while speed is up");
		assertTrue(!"buff".equals(h.brain.lastTrace().tactic()), "no more drinking once the effect is up");
	}
}
