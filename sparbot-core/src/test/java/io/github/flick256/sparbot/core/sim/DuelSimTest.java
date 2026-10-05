package io.github.flick256.sparbot.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Rng;
import org.junit.jupiter.api.Test;

/** The simulator against numbers measured in (or read from) the real game. */
class DuelSimTest {
	private static final Rng RNG = new Rng(1);

	private static SimWorld flat() {
		return SimWorld.arena(30, 6, new Rng(0));
	}

	private static SimFighter fighter() {
		return new SimFighter(Loadout.DIAMOND_SWORD, flat(), 0, 0, -10, 0);
	}

	/** A fighter far away, for the other side of a click. */
	private static SimFighter dummy(SimWorld world) {
		return new SimFighter(Loadout.DIAMOND_SWORD, world, 25, 0, 25, 0);
	}

	private static void step(SimFighter f, SimFighter other, Inputs keys) {
		f.receiveKnockback();
		f.look(keys);
		f.act(keys, other, RNG);
		f.move();
	}

	private static double speedAfter(Inputs keys, int ticks) {
		SimFighter f = fighter();
		SimFighter other = dummy(f.world);
		for (int i = 0; i < ticks; i++) {
			step(f, other, keys);
		}
		return Math.hypot(f.x - f.lastX, f.z - f.lastZ);
	}

	@Test
	void walksAndSprintsAtVanillaSpeed() {
		// 4.317 and 5.612 blocks per second.
		assertEquals(4.317 / 20, speedAfter(new Inputs(0, 0, 1, 0, false, false, false, false, false, -1), 60), 0.0005);
		assertEquals(5.612 / 20, speedAfter(new Inputs(0, 0, 1, 0, false, false, true, false, false, -1), 60), 0.0005);
	}

	@Test
	void jumpsAsHighAsVanilla() {
		SimFighter f = fighter();
		SimFighter other = dummy(f.world);
		double apex = 0;
		for (int i = 0; i < 20; i++) {
			step(f, other, new Inputs(0, 0, 0, 0, i == 0, false, false, false, false, -1));
			apex = Math.max(apex, f.y);
		}
		assertEquals(1.2522, apex, 0.001);
		assertTrue(f.onGround, "landed again");
		assertEquals(0, f.y, 1e-9);
	}

	@Test
	void aJumpCantClimbAFullBlockStepOrAWallTwoHigh() {
		SimWorld w = flat();
		w.init(0, 0, 2, SimWorld.STONE);
		SimFighter f = new SimFighter(Loadout.DIAMOND_SWORD, w, 0.5, 0, 0.5, 0);
		SimFighter other = dummy(w);
		for (int i = 0; i < 20; i++) {
			step(f, other, new Inputs(0, 0, 1, 0, false, false, false, false, false, -1));
		}
		assertEquals(1.7, f.z, 1e-6, "walking stops at the block (no 1-block step-up)");
		assertTrue(f.horizontalCollision);
		for (int i = 0; i < 12; i++) {
			step(f, other, new Inputs(0, 0, 1, 0, i == 0, false, false, false, false, -1));
		}
		for (int i = 0; i < 10; i++) {
			step(f, other, Inputs.IDLE);
		}
		assertEquals(1.0, f.y, 1e-6, "a jump gets on top of it");
	}

	@Test
	void swordDamageThroughDiamondArmorMatchesVanilla() {
		SimWorld w = flat();
		SimFighter a = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 2.0, 180);
		a.click(b, new Rng(1));
		// 7 damage; armor 20, toughness 8: 20 - 7 / 4 = 18.25 effective armor, so 1 - 18.25 / 25 of it gets through.
		assertEquals(7 * (1 - 18.25 / 25), 20 - b.health, 1e-4);
		assertEquals(1, a.hits);
		assertTrue(b.pendingMotion != null && b.pendingMotion.z() > 0.3, "knocked back, away from the attacker");
		// Straight away again: invulnerable and the charge is gone, so nothing gets through.
		a.click(b, new Rng(1));
		assertEquals(7 * (1 - 18.25 / 25), 20 - b.health, 1e-4);
	}

	@Test
	void aFallingUnsprintedFullHitCrits() {
		SimWorld w = flat();
		SimFighter a = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 2.0, 180);
		a.onGround = false;
		a.fallDistance = 0.3;
		a.y = 0.5;
		a.pitch = 15;
		a.click(b, new Rng(1));
		assertEquals(1, a.crits);
		assertEquals(10.5 * (1 - (20 - 10.5 / 4) / 25), 20 - b.health, 1e-4);
	}

	@Test
	void aClickAtNothingLocksClicksForTenTicksButKeepsTheCharge() {
		SimWorld w = flat();
		SimFighter a = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.DIAMOND_SWORD, w, 0, 0, 3.6, 180);
		a.click(b, new Rng(1));
		assertEquals(10, a.missTime, "out of reach: a miss");
		assertEquals(1.0F, a.attackStrength(), 1e-6, "a miss doesn't reset the server-side charge (Player#onAttack only on attacks)");
		b.z = 2.0;
		a.click(b, new Rng(1));
		assertEquals(20, b.health, 1e-6, "locked out");
	}

	@Test
	void theScriptedProBeatsTheScriptedBeginner() {
		var presets = io.github.flick256.sparbot.core.profile.SkillProfiles.loadPresets();
		double score = Tournament.score(Tournament.scripted(presets.get("pro")), Tournament.scripted(presets.get("beginner")), 20, 7);
		assertTrue(score > 0.8, "pro's score against a beginner: " + score);
	}

	private static Inputs press(int slot, boolean use, boolean attack) {
		return new Inputs(0, 0, 0, 0, false, false, false, attack, use, slot);
	}

	@Test
	void aGoldenAppleTakes32TicksAndHeals() {
		SimFighter f = new SimFighter(Loadout.UHC, flat(), 0, 0, 0, 0);
		SimFighter other = dummy(f.world);
		f.health = 10;
		step(f, other, press(SimFighter.GAPPLE_SLOT, false, false));
		for (int i = 0; i < 31; i++) {
			step(f, other, press(-1, true, false));
		}
		assertEquals(Loadout.UHC.gapples(), f.count(ItemKind.GOLDEN_APPLE), "still eating after 31 ticks");
		step(f, other, press(-1, true, false));
		assertEquals(Loadout.UHC.gapples() - 1, f.count(ItemKind.GOLDEN_APPLE), "eaten on the 32nd tick");
		assertEquals(4.0F, f.absorption, 1e-6);
		for (int i = 0; i < 100; i++) {
			step(f, other, press(-1, false, false));
		}
		assertEquals(14.0F, f.health, 1e-6, "Regeneration II: 4 health over 5 s");
	}

	@Test
	void aRaisedShieldBlocksAfterFiveTicksAndAnAxeDisablesIt() {
		SimWorld w = flat();
		SimFighter a = new SimFighter(Loadout.UHC, w, 0, 0, 0, 0);
		SimFighter b = new SimFighter(Loadout.UHC, w, 0, 0, 2.0, 180); // facing the attacker
		for (int i = 0; i < 4; i++) {
			step(b, a, press(-1, true, false));
		}
		assertFalse(b.blocking(), "not blocking yet after 4 ticks");
		step(b, a, press(-1, true, false));
		assertTrue(b.blocking(), "blocking from the 5th tick");
		a.click(b, new Rng(1));
		assertEquals(20, b.health, 1e-6, "the sword hit was blocked");
		assertEquals(1, b.blockedHits);
		assertEquals(null, b.pendingMotion, "a blocked hit pushes nobody");
		// Wait out the invulnerability, then an axe hit disables the shield.
		for (int i = 0; i < 20; i++) {
			step(b, a, press(-1, true, false));
			step(a, b, press(-1, false, false));
		}
		a.look(press(SimFighter.AXE_SLOT, false, false));
		for (int i = 0; i < 25; i++) {
			step(a, b, press(-1, false, false));
			step(b, a, press(-1, true, false));
		}
		a.click(b, new Rng(1));
		assertTrue(!b.blocking() && b.shieldCooldown > 0, "an axe disables the shield");
	}

	@Test
	void switchingItemsResetsTheCharge() {
		SimFighter f = new SimFighter(Loadout.UHC, flat(), 0, 0, 0, 0);
		assertEquals(1.0F, f.attackStrength(), 1e-6);
		f.look(press(SimFighter.AXE_SLOT, false, false));
		assertTrue(f.attackStrength() < 0.1F, "a different item in hand starts from no charge");
	}

	// ------------------------------------------------------------------ blocks and fluids

	private static int fluidCells(SimWorld w, byte t) {
		return w.count(t);
	}

	@Test
	void waterFlowsSevenBlocksOnFlatGroundAndLavaThree() {
		SimWorld w = flat();
		w.set(0, 0, 0, SimWorld.WATER);
		for (int i = 0; i < 60; i++) {
			w.tick();
		}
		// A diamond of radius 7 around the source: 1 + 4 * (1 + 2 + ... + 7) = 113 blocks.
		assertEquals(113, fluidCells(w, SimWorld.WATER));
		assertEquals(1.0F / 9, w.fluidHeight(7, 0, 0), 1e-6, "level 1 at 7 blocks");
		assertEquals(SimWorld.AIR, w.type(8, 0, 0));

		SimWorld l = flat();
		l.set(0, 0, 0, SimWorld.LAVA);
		for (int i = 0; i < 29; i++) {
			l.tick();
		}
		assertEquals(1, fluidCells(l, SimWorld.LAVA), "lava waits 30 ticks before its first step");
		for (int i = 0; i < 200; i++) {
			l.tick();
		}
		// Drop-off 2: amounts 8, 6, 4, 2 -> a diamond of radius 3: 1 + 4 * (1 + 2 + 3) = 25 blocks.
		assertEquals(25, fluidCells(l, SimWorld.LAVA));
	}

	@Test
	void waterTurnsLavaToObsidianOrCobblestone() {
		SimWorld w = flat();
		w.set(0, 0, 0, SimWorld.LAVA);
		w.set(1, 0, 0, SimWorld.WATER);
		assertEquals(SimWorld.OBSIDIAN, w.type(0, 0, 0), "a lava source next to water");
		SimWorld w2 = flat();
		w2.set(0, 0, 0, SimWorld.LAVA);
		for (int i = 0; i < 40; i++) {
			w2.tick();
		}
		assertEquals(SimWorld.LAVA, w2.type(1, 0, 0), "flowing lava");
		w2.set(3, 0, 0, SimWorld.WATER);
		for (int i = 0; i < 10; i++) {
			w2.tick();
		}
		assertEquals(SimWorld.COBBLE, w2.type(1, 0, 0), "flowing lava touched by water");
		assertEquals(SimWorld.LAVA, w2.type(0, 0, 0), "the source behind it is still lava");
	}

	@Test
	void waterWashesAWebAway() {
		SimWorld w = flat();
		w.set(0, 0, 0, SimWorld.WEB);
		w.set(0, 1, 0, SimWorld.WATER);
		for (int i = 0; i < 10; i++) {
			w.tick();
		}
		assertEquals(SimWorld.WATER, w.type(0, 0, 0));
	}

	@Test
	void aWaterBucketPouredAtTheFeetIsScoopedBack() {
		SimWorld w = flat();
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		SimFighter f = new SimFighter(kit, w, 0.5, 0, 0.5, 0);
		SimFighter other = dummy(w);
		f.pitch = 90;
		int bucket = 5; // the kit's water bucket
		step(f, other, press(bucket, false, false));
		step(f, other, press(-1, true, false));
		assertEquals(SimWorld.WATER, w.type(0, 0, 0), "the water goes into the space above the clicked floor");
		assertEquals(ItemKind.BUCKET, f.slots[bucket].kind());
		step(f, other, press(-1, false, false));
		step(f, other, press(-1, true, false));
		assertEquals(SimWorld.AIR, w.type(0, 0, 0), "scooped");
		assertEquals(ItemKind.WATER_BUCKET, f.slots[bucket].kind());
	}

	@Test
	void lavaBurnsAndWaterPutsTheFireOut() {
		SimWorld w = flat();
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		SimFighter f = new SimFighter(kit, w, 0.5, 0, 0.5, 0);
		SimFighter other = dummy(w);
		w.set(0, 0, 0, SimWorld.LAVA);
		step(f, other, Inputs.IDLE);
		assertTrue(f.inLava && f.onFire());
		// 4 lava damage through the UHC kit's diamond armor: armor 20 - 4 / 4 = 19, then 40% off (Protection III, II, II, III).
		assertEquals(4 * (1 - 19.0 / 25) * (1 - 10 / 25.0), 20 - f.health, 1e-4);
		w.set(0, 0, 0, SimWorld.WATER);
		step(f, other, Inputs.IDLE);
		assertFalse(f.onFire(), "water puts it out");
	}

	@Test
	void aWebSlowsToACrawl() {
		SimWorld w = flat();
		w.init(0, 0, 1, SimWorld.WEB);
		w.init(0, 1, 1, SimWorld.WEB);
		SimFighter f = new SimFighter(Loadout.DIAMOND_SWORD, w, 0.5, 0, 0.5, 0);
		SimFighter other = dummy(w);
		Inputs walk = new Inputs(0, 0, 1, 0, false, false, false, false, false, -1);
		for (int i = 0; i < 30; i++) {
			step(f, other, walk);
		}
		assertTrue(f.inWeb, "walked into it");
		double before = f.z;
		for (int i = 0; i < 10; i++) {
			step(f, other, walk);
		}
		assertTrue(f.z - before < 0.3, "a quarter of a block per second or so: " + (f.z - before));
	}

	@Test
	void fallsHurtBeyondThreeBlocks() {
		SimWorld w = flat();
		SimFighter f = new SimFighter(Loadout.DIAMOND_SWORD, w, 0.5, 6, 0.5, 0);
		SimFighter other = dummy(w);
		for (int i = 0; i < 40; i++) {
			step(f, other, Inputs.IDLE);
		}
		assertEquals(3.0F, 20 - f.health, 1e-6, "6 blocks: 3 damage, armor doesn't help");
	}

	@Test
	void holdingJumpInDeepWaterSwimsUp() {
		SimWorld w = flat();
		for (int y = -3; y < 3; y++) {
			w.init(0, y, 0, SimWorld.WATER);
		}
		SimFighter f = new SimFighter(Loadout.DIAMOND_SWORD, w, 0.5, -2, 0.5, 0);
		SimFighter other = dummy(w);
		for (int i = 0; i < 60; i++) {
			step(f, other, new Inputs(0, 0, 0, 0, true, false, false, false, false, -1));
		}
		assertTrue(f.y > 0, "rose out of the pit: y = " + f.y);
	}

	@Test
	void blocksGoOnTheFloorButNotIntoABodyWhileACobwebDoes() {
		SimWorld w = flat();
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		SimFighter a = new SimFighter(kit, w, 0.5, 0, 0.5, 0);
		SimFighter b = new SimFighter(kit, w, 0.5, 0, 3.5, 180);
		// Look at the top of the floor block two ahead (0, -1, 2): the block goes into (0, 0, 2).
		a.pitch = (float) Math.toDegrees(Math.atan2(1.62, 2.0));
		step(a, b, press(8, false, false));
		step(a, b, press(-1, true, false));
		assertEquals(SimWorld.COBBLE, w.type(0, 0, 2));
		assertEquals(63, a.count(ItemKind.BLOCK));
		// The space b stands in takes a web but not a block. The near edge of the floor under b is in
		// sight (b's body hides the rest of it).
		w.set(0, 0, 2, SimWorld.AIR);
		a.pitch = (float) Math.toDegrees(Math.atan2(1.62, 2.6));
		step(a, b, press(-1, false, false));
		for (int i = 0; i < 5; i++) {
			step(a, b, press(-1, true, false));
		}
		assertEquals(SimWorld.AIR, w.type(0, 0, 3), "not into b");
		step(a, b, press(6, false, false));
		step(a, b, press(-1, true, false));
		assertEquals(SimWorld.WEB, w.type(0, 0, 3), "a cobweb goes into b's space");
	}

	@Test
	void aFullyDrawnBowShotHitsHard() {
		SimWorld w = flat();
		Loadout kit = Loadout.ofKit("sparbot_uhc");
		SimFighter a = new SimFighter(kit, w, 0.5, 0, 0.5, 0);
		SimFighter b = new SimFighter(kit, w, 0.5, 0, 12.5, 180);
		a.pitch = -1.5F;
		step(a, b, press(2, false, false));
		for (int i = 0; i < 21; i++) {
			step(a, b, press(-1, true, false));
		}
		step(a, b, press(-1, false, false));
		assertEquals(1, a.shot.size());
		SimArrow arrow = a.shot.get(0);
		for (int i = 0; i < 20 && !arrow.done; i++) {
			arrow.tick(w, b, new Rng(3));
		}
		assertTrue(b.health < 20, "hit");
		assertEquals(31, a.count(ItemKind.ARROW));
	}
}
