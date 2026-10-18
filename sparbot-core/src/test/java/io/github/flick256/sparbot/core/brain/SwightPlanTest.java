package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.TargetState;
import io.github.flick256.sparbot.core.style.Playstyle;
import org.junit.jupiter.api.Test;

class SwightPlanTest {
	@Test
	void specialistKeepsDemonsPhysicalLimitsAndConservativeReach() {
		var presets = SkillProfiles.loadPresets();
		var s = presets.get("swight"); var d = presets.get("demon");
		assertTrue(s.reactionTimeMs().min() >= d.reactionTimeMs().min());
		assertTrue(s.reactionTimeMs().mean() >= d.reactionTimeMs().mean());
		assertTrue(s.aim().maxTurnDegPerTick() <= d.aim().maxTurnDegPerTick());
		assertTrue(s.clicking().cps().max() <= d.clicking().cps().max());
		assertTrue(s.items().hotbarSwitchMs().min() >= d.items().hotbarSwitchMs().min());
		assertTrue(s.items().inventoryMs().min() >= d.items().inventoryMs().min());
		assertTrue(s.reach().rangeErrorBlocks().max() <= 0);
		assertTrue(s.reach().rangeErrorBlocks().mean() < 0 && s.reach().rangeErrorBlocks().stdDev() >= 0.1);
	}

	@Test
	void closesOnUtilityButStillRespectsAReadiedSword() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(), null);
		var self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 0.3F, true, TestFixtures.flatGround());
		h.target = TestFixtures.target(new Vec3(0, 0, 3), 0, ItemKind.WATER_BUCKET, ItemKind.SHIELD, ItemKind.EMPTY, false);
		var c = h.context(self); c.memory.reading = true;
		var move = new SwordPlan.Move(1, 0, true);
		SwordPlan.plan(c, 2.7, 2.9, move);
		assertEquals(1, move.forward, "punishes a delayed, visibly occupied hand");
		h.target = TestFixtures.target(new Vec3(0, 0, 3), 0);
		c = h.context(self); c.memory.reading = true;
		move = new SwordPlan.Move(1, 0, true);
		SwordPlan.plan(c, 2.7, 2.9, move);
		assertEquals(-1, move.forward, "does not apply the same chase to an armed opponent while recharging");
	}

	@Test
	void webPunishStopsBeforeWalkingIntoTheTrap() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(), null);
		h.target = new TargetState(7, "Target", new Vec3(0, 0, 3), Vec3.ZERO, 0, 20, 20, true, 0, false, true, 0, .3, 1.8,
			ItemKind.SWORD, ItemKind.SHIELD, ItemKind.EMPTY, 15, false, true, false, 0);
		var c = h.context(TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround()));
		c.memory.reading = true;
		var move = new SwordPlan.Move(1, 1, true);
		assertTrue(SwightPlan.meleeOpening(c, 2.1, 2.9, move));
		assertEquals(0, move.forward);
		assertEquals(0, move.strafe);
	}

	@Test
	void sustainedShieldRequestsHaveReleaseWindowsAndResetForANewRound() {
		var p = TestFixtures.preset("swight");
		Tactic hold = new Tactic() {
			public String name() { return "hold"; }
			public double score(BrainContext c) { return 2; }
			public Inputs act(BrainContext c) { return Inputs.IDLE.withUse(true); }
		};
		var brain = new DuelBrain(p, Playstyle.BALANCED, 7, hold);
		var self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround(), TestFixtures.inventory(40, TestFixtures.SHIELD), 20);
		int held = 0, longest = 0, releases = 0;
		for (int tick = 0; tick < 100; tick++) {
			Inputs in = brain.act(new Observation(tick, self, TestFixtures.target(new Vec3(0, 0, 8), 0)));
			if (in.use()) { longest = Math.max(longest, ++held); } else { held = 0; releases++; }
		}
		assertTrue(longest >= 5 && longest <= 22, "blocks meaningfully, then releases");
		assertTrue(releases >= 12, "multiple punishable release windows");
		brain.reset();
		assertTrue(brain.act(new Observation(0, self, null)).use(), "new round clears the shield recovery state");
	}

	@Test
	void shieldReleaseNeverInterruptsAnAppleOrPotion() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(0, TestFixtures.GAPPLE, 40, TestFixtures.SHIELD), null);
		var c = h.context(TestFixtures.self(Vec3.ZERO, 0, 0, 10, 1, true, TestFixtures.flatGround(), h.inventory(), 20));
		var plan = new SwightPlan();
		for (int i = 0; i < 80; i++) { assertTrue(plan.limitShield(c, Inputs.IDLE.withUse(true)).use()); }
	}
	@Test
	void weaponSwitchesCannotExtendShieldHoldsAndHealingStillWorksDuringRelease() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(1, TestFixtures.AXE, 2, TestFixtures.GAPPLE, 40, TestFixtures.SHIELD), null);
		var self = TestFixtures.self(Vec3.ZERO, 0, 0, 10, 1, true, TestFixtures.flatGround(), h.inventory(), 20);
		var seed = h.context(self);
		var plan = new SwightPlan();
		int held = 0, longest = 0;
		for (int tick = 0; tick < 80; tick++) {
			var c = new BrainContext(new Observation(tick, self, null), null, 0, seed.profile, seed.style, seed.rng, seed.aim, seed.memory);
			Inputs in = new Inputs(0, 0, 0, 0, false, false, false, false, true, tick % 2);
			boolean use = plan.limitShield(c, in).use();
			longest = Math.max(longest, held = use ? held + 1 : 0);
			if (!use) {
				assertTrue(plan.limitShield(c, new Inputs(0, 0, 0, 0, false, false, false, false, true, 2)).use(), "switching to food remains possible in a release window");
			}
		}
		assertTrue(longest <= 22, "alternating sword and axe must not reset the shield limit");
	}

	@Test
	void circlesForAnAngleWhenAWebTakesTheClickBeforeTheOpponent() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(), null);
		h.target = new TargetState(7, "Target", new Vec3(0, 0, 2.6), Vec3.ZERO, 0, 20, 20, true, 0, false, true, 0, .3, 1.8,
			ItemKind.SWORD, ItemKind.SHIELD, ItemKind.EMPTY, 15, false, true, false, 0);
		var b = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround());
		var blocked = new io.github.flick256.sparbot.core.sense.SelfState(b.position(), b.eyePosition(), b.velocity(), b.yaw(), b.pitch(),
			b.health(), b.maxHealth(), b.absorption(), b.foodLevel(), b.onGround(), b.sprinting(), b.inWater(), b.horizontalCollision(),
			b.fallDistance(), b.attackStrength(), b.attackReach(), b.hurtTime(), b.holdingMeleeWeapon(), b.dropDepth(), b.inventory(),
			b.effects(), false, false, false, 1.5);
		var c = h.context(blocked); c.memory.reading = true;
		var move = new SwordPlan.Move(1, 0, true);
		assertTrue(SwightPlan.meleeOpening(c, 2.3, 2.9, move));
		assertEquals(0, move.forward, "do not charge into the obstructing trap");
		assertEquals(c.memory.strafeDirection, move.strafe, "find an angle instead of waiting for the opponent to free us");
		org.junit.jupiter.api.Assertions.assertFalse(move.sprint);
	}

	@Test
	void aimsBelowAHeadHeightWebInsteadOfTryingToHitThroughIt() {
		var h = new BrainHarness(TestFixtures.preset("swight"), TestFixtures.inventory(), null);
		h.target = new TargetState(7, "Target", new Vec3(0, 0, 2.6), Vec3.ZERO, 0, 20, 20, true, 0, false, true, 0, .3, 1.8,
			ItemKind.SWORD, ItemKind.SHIELD, ItemKind.EMPTY, 15, false, true, false, 0);
		h.world = new io.github.flick256.sparbot.core.sense.Surroundings(java.util.List.of(), java.util.List.of(), java.util.List.of(),
			java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of(),
			java.util.List.of(new io.github.flick256.sparbot.core.sense.BlockSpot(0, 1, 2)));
		var c = h.context(TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround()));
		assertTrue(SwightPlan.meleeAim(c, h.target, Vec3.ZERO).y() < 1, "the legs are exposed below the head-height web");
		h.world = io.github.flick256.sparbot.core.sense.Surroundings.EMPTY;
		c = h.context(c.self);
		assertEquals(h.target.chest(), SwightPlan.meleeAim(c, h.target, Vec3.ZERO), "do not invent unseen obstructions");
	}

}
