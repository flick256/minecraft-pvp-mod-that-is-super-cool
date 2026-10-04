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
import org.junit.jupiter.api.Test;

class UhcTacticsTest {
	private static final ItemInfo LAVA = TestFixtures.item(ItemKind.LAVA_BUCKET, "minecraft:lava_bucket", 1, 1);
	private static final ItemInfo WATER = TestFixtures.item(ItemKind.WATER_BUCKET, "minecraft:water_bucket", 1, 1);
	private static final ItemInfo BUCKET = TestFixtures.item(ItemKind.BUCKET, "minecraft:bucket", 1, 1);
	private static final ItemInfo WEBS = TestFixtures.item(ItemKind.COBWEB, "minecraft:cobweb", 8, 1);
	private static final ItemInfo PLANKS = TestFixtures.item(ItemKind.BLOCK, "minecraft:oak_planks", 64, 1);
	private static final ItemInfo OBSIDIAN = TestFixtures.item(ItemKind.BLOCK, "minecraft:obsidian", 64, 1);

	/** Pro with certain UHC decisions, so assertions don't depend on dice rolls. */
	private static SkillProfile uhcPro() {
		SkillProfile p = TestFixtures.flawless("pro");
		SkillProfile.ItemSkills i = p.items();
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(), p.reach(),
			p.technique(), new SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), i.retotemSkill(), i.gappleHealthFraction(),
				i.eatHungerBelow(), i.shieldSkill(), i.axeSkill(), i.bowSkill(), i.pearlSkill(), i.potHealthFraction(), i.maceSkill(),
				i.spearSkill(), i.crystalSkill(), i.cartSkill(), 1.0),
			p.mistakeRate(), p.panicHealthFraction());
	}

	/** Runs until the bot right-clicks holding {@code kind}; returns whether it did within {@code ticks}. */
	private static boolean usesWithin(BrainHarness h, ItemKind kind, int ticks) {
		for (int i = 0; i < ticks; i++) {
			Inputs in = h.tick();
			if (in.use() && h.slots[h.selected].kind() == kind) {
				return true;
			}
		}
		return false;
	}

	@Test
	void poursLavaUnderTheOpponentAndScoopsItBackUp() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, LAVA), TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		h.attackStrength = 0.2F; // just swung
		assertTrue(usesWithin(h, ItemKind.LAVA_BUCKET, 40), "emptied the lava bucket");
		assertTrue(h.pitch > 15, "onto the ground at the opponent's feet, pitch " + h.pitch);
		h.slots[1] = BUCKET; // vanilla turns it into an empty bucket
		assertTrue(usesWithin(h, ItemKind.BUCKET, 80), "scooped the lava back up");
	}

	@Test
	void spamsLavaAndNeverLeavesItDown() {
		TestFixturesWeb web = new TestFixturesWeb();
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, LAVA), web.webbed(new Vec3(0, 0, 3.0)));
		int pours = 0;
		int scoops = 0;
		int firstPour = -1;
		int lastScoop = -1;
		for (int i = 0; i < 200; i++) {
			Inputs in = h.tick();
			if (in.use() && h.slots[1].is(ItemKind.LAVA_BUCKET) && h.selected == 1) {
				h.slots[1] = BUCKET; // the lava is down
				pours++;
				firstPour = firstPour < 0 ? i : firstPour;
			} else if (in.use() && h.slots[1].is(ItemKind.BUCKET) && h.selected == 1) {
				h.slots[1] = LAVA; // scooped back up
				scoops++;
				lastScoop = i;
			}
		}
		assertTrue(pours >= 3, "chained pours on the webbed opponent: " + pours);
		assertTrue(scoops >= pours - 1, "every pour was scooped back up (the last may still be down when the test stops): " + pours + " pours, " + scoops + " scoops");
		assertTrue((lastScoop - firstPour) / (double) pours < 25, "a quick rhythm, not one long burn");
	}

	@Test
	void websTheOpponent() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WEBS), TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		h.attackStrength = 0.2F; // just swung: the switch to the web costs no hit
		assertTrue(usesWithin(h, ItemKind.COBWEB, 40), "placed a cobweb");
		assertEquals("web", h.brain.lastTrace().tactic());
	}

	@Test
	void keepsItsChargedHitRatherThanReachingForUtility() {
		// Charged, with the opponent in reach and able to hit back: a switch to a web or bucket (and back)
		// throws the hit away, so a skilled player hits instead.
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WEBS, 2, LAVA),
			TestFixtures.target(new Vec3(0, 0, 3.0), 0));
		for (int i = 0; i < 40; i++) {
			h.attackStrength = 1;
			h.tick();
			assertFalse(h.selected == 1 || h.selected == 2, "reached for utility at tick " + i + " (" + h.brain.lastTrace().tactic() + ")");
		}
	}

	@Test
	void putsItselfOutWithWaterAndTakesItBack() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WATER), TestFixtures.target(new Vec3(0, 0, 6.0), 0));
		h.onFire = true;
		assertTrue(usesWithin(h, ItemKind.WATER_BUCKET, 40), "emptied the water bucket");
		assertTrue(h.pitch >= 80, "at its own feet, pitch " + h.pitch);
		h.slots[1] = BUCKET;
		h.onFire = false;
		assertTrue(usesWithin(h, ItemKind.BUCKET, 40), "picked the water back up");
	}

	@Test
	void watersItsWayOutOfAWeb() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, WATER), TestFixtures.target(new Vec3(0, 0, 9.0), 0));
		h.inWeb = true;
		assertTrue(usesWithin(h, ItemKind.WATER_BUCKET, 40), "emptied the water bucket onto the web");
		assertTrue(h.pitch >= 80, "looking down at the web, pitch " + h.pitch);
		assertEquals("water", h.brain.lastTrace().tactic());
	}

	@Test
	void poursLavaOntoAWebbedOpponent() {
		TestFixturesWeb web = new TestFixturesWeb();
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, LAVA), web.webbed(new Vec3(0, 0, 3.0)));
		assertTrue(usesWithin(h, ItemKind.LAVA_BUCKET, 40), "poured the lava");
		assertEquals("lava", h.brain.lastTrace().tactic());
		// Onto the top of the web (one block up from the ground), so less steeply than at the ground.
		assertTrue(h.pitch > 5 && h.pitch < 25, "aimed at the web's top, pitch " + h.pitch);
	}

	@Test
	void followsAShieldStunWithAnInstantHit() {
		ItemInfo axe = TestFixtures.AXE;
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, axe),
			TestFixtures.target(new Vec3(0, 0, 2.5), 0, ItemKind.SWORD, ItemKind.SHIELD, ItemKind.SHIELD, true));
		int axeHit = -1;
		int followUp = -1;
		for (int i = 0; i < 80 && followUp < 0; i++) {
			Inputs in = h.tick();
			if (in.attack() && h.slots[h.selected].kind() == ItemKind.AXE && axeHit < 0) {
				axeHit = i;
				// The axe hit disables the shield: it comes down.
				h.target = TestFixtures.target(new Vec3(0, 0, 2.5), 0, ItemKind.SWORD, ItemKind.SHIELD, ItemKind.EMPTY, false);
			} else if (in.attack() && axeHit >= 0 && h.slots[h.selected].kind() == ItemKind.SWORD) {
				followUp = i;
			}
		}
		assertTrue(axeHit >= 0, "disabled the shield with the axe");
		// Seeing the shield come down takes a tick or two, then the switch and the hit.
		assertTrue(followUp >= 0 && followUp - axeHit <= 6, "switched back and hit within 6 ticks (axe " + axeHit + ", sword " + followUp + ")");
	}

	@Test
	void wallsOffBeforeHealing() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, TestFixtures.GAPPLE, 2, PLANKS),
			TestFixtures.target(new Vec3(0, 0, 6.0), 0));
		h.health = 6;
		boolean placed = false;
		for (int i = 0; i < 40 && !placed; i++) {
			Inputs in = h.tick();
			placed = in.use() && "minecraft:oak_planks".equals(h.slots[h.selected].id());
			if (placed) {
				assertEquals(0, in.forward(), "stands still to build");
			}
		}
		assertTrue(placed, "put a block down between itself and the opponent");
		assertEquals("wall", h.brain.lastTrace().tactic());
	}

	@Test
	void buildsAFullWallThenEatsBehindIt() {
		BrainHarness h = new BrainHarness(uhcPro(), TestFixtures.inventory(0, TestFixtures.SWORD, 1, TestFixtures.GAPPLE, 2, PLANKS),
			TestFixtures.target(new Vec3(0, 0, 6.0), 0));
		// Low, but not yet low enough to eat in the open: the wall comes first.
		h.health = (float) (20 * (uhcPro().items().gappleHealthFraction() + 0.1));
		int placed = 0;
		int ateAt = -1;
		int lastBlockAt = -1;
		for (int i = 0; i < 120 && ateAt < 0; i++) {
			Inputs in = h.tick();
			ItemInfo held = h.slots[h.selected];
			if (in.use() && "minecraft:oak_planks".equals(held.id())) {
				// The harness doesn't place blocks: use one up, as vanilla would.
				h.slots[h.selected] = TestFixtures.item(ItemKind.BLOCK, held.id(), held.count() - 1, 1);
				placed++;
				lastBlockAt = i;
			} else if (in.use() && held.kind() == ItemKind.GOLDEN_APPLE) {
				ateAt = i;
			}
		}
		assertEquals(6, placed, "a pro puts up the full three-by-two wall");
		assertTrue(ateAt > lastBlockAt, "then eats behind it (wall done at " + lastBlockAt + ", eating at " + ateAt + ")");
		assertTrue(ateAt - lastBlockAt < 20, "straight away");
	}

	@Test
	void obsidianIsNotWallMaterial() {
		assertTrue(WallTactic.wallBlock(PLANKS));
		assertTrue(!WallTactic.wallBlock(OBSIDIAN) && !WallTactic.wallBlock(WEBS));
	}

	@Test
	void predictsWhereAKnockedUpPlayerLands() {
		// Knocked up and back: about 0.4 up and 0.4 away per tick, from standing on the ground.
		Vec3 land = BlockPlay.landing(new Vec3(0, 1.0, 3.0), new Vec3(0, 0.42, 0.4), 1.0);
		assertEquals(1.0, land.y(), 1e-9);
		assertTrue(land.z() > 6.0 && land.z() < 8.5, "lands a few blocks back, at z " + land.z());
	}

	/** A target standing in a cobweb. */
	private static final class TestFixturesWeb {
		io.github.flick256.sparbot.core.sense.TargetState webbed(Vec3 pos) {
			return new io.github.flick256.sparbot.core.sense.TargetState(7, "Steve", pos, Vec3.ZERO, 0, 20, 20, true, 0, false, true, 0, 0.3, 1.8,
				ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, 15, false, true);
		}
	}
}
