package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import org.junit.jupiter.api.Test;

class ItemBehaviourTest {
	/** Runs the brain, "pressing" hotbar keys into the inventory like the game would, returning the last inputs. */
	private static Inputs runUntil(DuelBrain brain, InventoryState start, float health, int food, TargetState target, int ticks,
		java.util.function.Predicate<Inputs> stop) {
		InventoryState inv = start;
		Inputs last = Inputs.IDLE;
		for (int t = 0; t < ticks; t++) {
			SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, health, 1.0F, true, TestFixtures.flatGround(), inv, food);
			last = brain.act(new Observation(t, self, target));
			if (last.hotbarSlot() >= 0) {
				inv = new InventoryState(inv.slots(), inv.offhand(), inv.armor(), last.hotbarSlot(), inv.usingItem(), inv.usingOffhand(),
					inv.useTicks(), inv.usingKind(), inv.blocking(), inv.fishingHookOut(), inv.fishingHookOnTarget());
			}
			if (stop.test(last)) {
				return last;
			}
		}
		return last;
	}

	@Test
	void hotbarSwitchTakesHumanTime() {
		InventoryState inv = TestFixtures.inventory(3, TestFixtures.GAPPLE);
		for (String id : new String[] {"beginner", "pro"}) {
			Hands hands = new Hands();
			int ticks = 0;
			while (hands.request(inv, 3, TestFixtures.preset(id), new Rng(5)) < 0) {
				ticks++;
			}
			double minTicks = TestFixtures.preset(id).items().hotbarSwitchMs().min() / 50.0;
			assertTrue(ticks >= Math.floor(minTicks), id + " switched after " + ticks + " ticks");
		}
	}

	@Test
	void eatsAGoldenAppleWhenLowAndSafe() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 1);
		InventoryState inv = TestFixtures.inventory(4, TestFixtures.GAPPLE);
		Inputs in = runUntil(brain, inv, 6, 20, TestFixtures.target(new Vec3(0, 0, 12), 0), 40, i -> i.use());
		assertEquals("heal", brain.lastTrace().tactic());
		assertTrue(in.use(), "holds use to eat");
	}

	@Test
	void doesNotStartAGappleInTheOpponentsFace() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 1);
		InventoryState inv = TestFixtures.inventory(4, TestFixtures.GAPPLE);
		// 8/20 health: low enough for a gapple (pro threshold 0.5) but not desperate, opponent 2 blocks away.
		runUntil(brain, inv, 8, 20, TestFixtures.target(new Vec3(0, 0, 2), 0), 30, i -> false);
		assertNotEquals("heal", brain.lastTrace().tactic());
	}

	@Test
	void eatsFoodWhenHungryWithNobodyAround() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("intermediate"), 2);
		InventoryState inv = TestFixtures.inventory(6, TestFixtures.STEAK);
		Inputs in = runUntil(brain, inv, 20, 6, null, 40, i -> i.use());
		assertEquals("heal", brain.lastTrace().tactic());
		assertTrue(in.use());
	}

	@Test
	void switchesBackToTheSwordToFight() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 3);
		ItemInfo[] slots = TestFixtures.inventory().slots().clone();
		slots[0] = ItemInfo.EMPTY;
		slots[5] = TestFixtures.SWORD;
		slots[1] = TestFixtures.STEAK;
		InventoryState inv = new InventoryState(slots, ItemInfo.EMPTY, InventoryState.empty().armor(), 1, false, false, 0, ItemKind.EMPTY,
			false, false, false);
		Inputs in = runUntil(brain, inv, 20, 20, TestFixtures.target(new Vec3(0, 0, 4), 0), 40, i -> i.hotbarSlot() >= 0);
		assertEquals(5, in.hotbarSlot());
	}

	@Test
	void neverAttacksWithFoodInHand() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 4);
		ItemInfo[] slots = TestFixtures.inventory().slots().clone();
		slots[1] = TestFixtures.STEAK;
		InventoryState inv = new InventoryState(slots, ItemInfo.EMPTY, InventoryState.empty().armor(), 1, false, false, 0, ItemKind.EMPTY,
			false, false, false);
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1.0F, true, TestFixtures.flatGround(), inv, 20);
		for (int t = 0; t < 3; t++) {
			Inputs in = brain.act(new Observation(t, self, TestFixtures.target(new Vec3(0, 0, 2.5), 0)));
			assertFalse(in.attack(), "should switch to the sword before clicking");
		}
	}
}
