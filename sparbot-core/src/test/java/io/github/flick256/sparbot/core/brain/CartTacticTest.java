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
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.potHealthFraction(), i.maceSkill(),
				i.spearSkill(), i.crystalSkill(), 1.0, i.uhcSkill()),
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
			fired = drawn >= 6 && !in.use() && h.slots[h.selected].kind() == ItemKind.BOW;
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

	@Test
	void waitsForAnOpening() {
		// Running sideways fast: a cart placed now would miss. Standing still: go.
		io.github.flick256.sparbot.core.sense.TargetState running = new io.github.flick256.sparbot.core.sense.TargetState(7, "Steve",
			new Vec3(0, 0, 2.5), new Vec3(0.28, 0, 0), 0, 20, 20, true, 0, false, true, 0, 0.3, 1.8, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, 15);
		BrainHarness h = new BrainHarness(cartPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, FLAME_BOW, 2, RAILS, 3, CART, 21, ARROWS), running);
		h.world = new Surroundings(List.of(), List.of(), List.of(new BlockSpot(1, -1, 3)), List.of(), List.of());
		for (int i = 0; i < 30; i++) {
			h.tick();
		}
		assertFalse(h.history.stream().anyMatch(in -> in.use() && "minecraft:rail".equals(h.slots[h.selected].id())), "no rail for a moving target");
	}

	@Test
	void leadsTheRailAheadOfAnApproachingOpponent() {
		io.github.flick256.sparbot.core.sense.TargetState coming = new io.github.flick256.sparbot.core.sense.TargetState(7, "Steve",
			new Vec3(0, 0, 6.0), new Vec3(0, 0, -0.1), 0, 20, 20, true, 0, false, true, 0, 0.3, 1.8, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, 15);
		// 16 ticks at 0.1 blocks per tick towards the bot, scaled by the pro's tracking lead.
		Vec3 ahead = CartTactic.predicted(coming, cartPro().aim().trackingLead());
		assertTrue(ahead.z() < 6.0 && ahead.z() > 3.5, "expected to be closer by the time the arrow arrives, z " + ahead.z());
		assertTrue(CartTactic.predicted(coming, 0).z() == 6.0, "no tracking skill, no lead");
	}

	private static final ItemInfo FLINT = TestFixtures.item(ItemKind.OTHER, "minecraft:flint_and_steel", 1, 1);

	private static ItemInfo crossbow(boolean loaded) {
		return new ItemInfo(ItemKind.CROSSBOW, "minecraft:crossbow", 1, 1, 1, false, loaded, null);
	}

	/** The high tier cart kit's hotbar: crossbow, rails, flint and steel, a cart; a cart down by the opponent. */
	private static BrainHarness crossbowHarness(boolean loaded, List<BlockSpot> fires) {
		BrainHarness h = new BrainHarness(cartPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 2, crossbow(loaded), 3, RAILS, 4, FLINT, 5, CART, 29, ARROWS),
			TestFixtures.target(new Vec3(-0.5, 0, 4.5), 0));
		// The cart on a rail at (0, 0, 5); the ground block in front of it (towards the bot) can take fire.
		h.world = new Surroundings(List.of(), List.of(), List.of(new BlockSpot(0, -1, 4)), List.of(new Vec3(0.5, 0.0625, 5.5)), List.of(), List.of(), List.of(),
			List.of(), List.of(), fires);
		return h;
	}

	@Test
	void lightsFireInFrontOfTheCartWithFlintAndSteel() {
		BrainHarness h = crossbowHarness(true, List.of());
		boolean lit = false;
		for (int i = 0; i < 40 && !lit; i++) {
			Inputs in = h.tick();
			lit = in.use() && "minecraft:flint_and_steel".equals(h.slots[h.selected].id());
		}
		assertTrue(lit, "clicked the ground in front of the cart with flint and steel, now " + h.brain.lastTrace().note());
		assertTrue(h.brain.lastTrace().note().endsWith("step=light"), h.brain.lastTrace().note());
	}

	@Test
	void firesTheLoadedCrossbowThroughTheFire() {
		BrainHarness h = crossbowHarness(true, List.of(new BlockSpot(0, 0, 4)));
		boolean fired = false;
		for (int i = 0; i < 40 && !fired; i++) {
			Inputs in = h.tick();
			fired = in.use() && h.slots[h.selected].kind() == ItemKind.CROSSBOW && h.slots[h.selected].charged();
		}
		assertTrue(fired, "shot the crossbow, now " + h.brain.lastTrace().note());
		// The bolt's line (straight from the eye along the look) runs through the fire block.
		Vec3 eye = new Vec3(0, 1.62, 0);
		Vec3 far = eye.add(io.github.flick256.sparbot.core.math.Angles.lookVector(h.yaw, h.pitch).scale(5.6));
		assertTrue(CartTactic.crosses(eye, far, new BlockSpot(0, 0, 4)), "aimed through the fire");
	}

	@Test
	void loadsTheCrossbowBeforeTheRail() {
		BrainHarness h = new BrainHarness(cartPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 2, crossbow(false), 3, RAILS, 4, FLINT, 5, CART, 29, ARROWS),
			TestFixtures.target(new Vec3(0, 0, 4.5), 0));
		h.world = new Surroundings(List.of(), List.of(), List.of(new BlockSpot(1, -1, 5)), List.of(), List.of());
		boolean loading = false;
		for (int i = 0; i < 30 && !loading; i++) {
			Inputs in = h.tick();
			loading = in.use() && h.slots[h.selected].kind() == ItemKind.CROSSBOW;
			assertFalse(in.use() && "minecraft:rail".equals(h.slots[h.selected].id()), "no rail before the crossbow is loaded");
		}
		assertTrue(loading, "held right click on the crossbow, now " + h.brain.lastTrace().tactic() + " " + h.brain.lastTrace().note());
	}
}
