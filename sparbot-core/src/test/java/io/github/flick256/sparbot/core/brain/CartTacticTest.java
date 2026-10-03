package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.Surroundings;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CartTacticTest {
	private static final ItemInfo FLAME_BOW = new ItemInfo(ItemKind.BOW, "minecraft:bow", 1, 1, 1, false, false, null, Set.of("minecraft:flame"));
	private static final ItemInfo PLAIN_BOW = TestFixtures.item(ItemKind.BOW, "minecraft:bow", 1, 1);
	private static final ItemInfo ARROWS = TestFixtures.item(ItemKind.ARROW, "minecraft:arrow", 64, 1);
	private static final ItemInfo CART = TestFixtures.item(ItemKind.TNT_MINECART, "minecraft:tnt_minecart", 1, 1);
	private static final ItemInfo RAILS = TestFixtures.item(ItemKind.BLOCK, "minecraft:rail", 64, 1);

	/** Pro that always goes for carts, so assertions don't depend on dice rolls. */
	private static SkillProfile cartPro() {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.rodSkill(), i.potHealthFraction(), i.maceSkill(),
				i.spearSkill(), i.crystalSkill(), 1.0),
			p.mistakeRate(), p.panicHealthFraction());
	}

	private static BrainHarness harness(ItemInfo bow, Surroundings world) {
		BrainHarness h = new BrainHarness(cartPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, bow, 2, RAILS, 3, CART, 21, ARROWS),
			TestFixtures.target(new Vec3(0, 0, 2.5), 0));
		h.world = world;
		return h;
	}

	@Test
	void shootsACartBesideTheOpponentWithAShortDraw() {
		BrainHarness h = harness(FLAME_BOW, new Surroundings(List.of(), List.of(), List.of(), List.of(new Vec3(1.2, 0, 3.0)), List.of()));
		boolean fired = false;
		for (int i = 0; i < 60 && !fired; i++) {
			int drawn = h.useTicks;
			Inputs in = h.tick();
			fired = drawn >= 10 && !in.use() && h.slots[h.selected].kind() == ItemKind.BOW;
		}
		assertTrue(fired, "drew the Flame bow and let go");
		assertEquals("cart", h.brain.lastTrace().tactic());
	}

	@Test
	void putsACartOnARailBesideTheOpponent() {
		BrainHarness h = harness(FLAME_BOW, new Surroundings(List.of(), List.of(), List.of(), List.of(), List.of(new BlockSpot(1, 0, 3))));
		boolean placed = false;
		for (int i = 0; i < 40 && !placed; i++) {
			Inputs in = h.tick();
			placed = in.use() && h.slots[h.selected].kind() == ItemKind.TNT_MINECART;
		}
		assertTrue(placed, "right-clicked the rail holding a TNT minecart");
	}

	@Test
	void putsDownARailWhenThereIsNone() {
		BrainHarness h = harness(FLAME_BOW, new Surroundings(List.of(), List.of(), List.of(new BlockSpot(1, -1, 3)), List.of(), List.of()));
		boolean placed = false;
		for (int i = 0; i < 40 && !placed; i++) {
			Inputs in = h.tick();
			placed = in.use() && "minecraft:rail".equals(h.slots[h.selected].id());
		}
		assertTrue(placed, "right-clicked the ground holding rails");
	}

	@Test
	void doesNotShootThroughTheOpponent() {
		// The cart is straight behind the opponent: the arrow would hit them, not the cart.
		BrainHarness h = harness(FLAME_BOW, new Surroundings(List.of(), List.of(), List.of(), List.of(new Vec3(0, 0, 3.8)), List.of()));
		for (int i = 0; i < 40; i++) {
			h.tick();
		}
		assertFalse(h.any(Inputs::use), "never drew at a cart behind the opponent");
	}

	@Test
	void needsAFlameBow() {
		BrainHarness h = harness(PLAIN_BOW, new Surroundings(List.of(), List.of(), List.of(new BlockSpot(1, -1, 3)), List.of(), List.of()));
		for (int i = 0; i < 20; i++) {
			h.tick();
		}
		assertEquals("engage", h.brain.lastTrace().tactic(), "without fire to set them off, carts are useless");
	}
}
