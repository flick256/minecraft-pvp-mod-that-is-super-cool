package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.Surroundings;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReflexesTest {
	/** A stand-in for any utility tactic that is busy and pressed nothing. */
	private static final Tactic BUSY = new Tactic() {
		@Override
		public String name() {
			return "busy";
		}

		@Override
		public double score(BrainContext context) {
			return 1;
		}

		@Override
		public Inputs act(BrainContext context) {
			return Inputs.IDLE;
		}
	};

	/** Sword in hand, fully charged, facing an opponent 2.5 blocks south. */
	private static SelfState swordReady() {
		return TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1.0F, true, TestFixtures.flatGround(), TestFixtures.inventory(), 20);
	}

	private static BrainHarness harness(Surroundings world) {
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(), TestFixtures.target(new Vec3(0, 0, 2.5), 0));
		h.world = world;
		return h;
	}

	@Test
	void takesTheFreeHitWhileBusyWithUtility() {
		BrainHarness h = harness(Surroundings.EMPTY);
		assertTrue(Reflexes.apply(h.context(swordReady()), BUSY, Inputs.IDLE).attack(), "the opponent walked into reach: hit them");
	}

	@Test
	void noReflexesWhenSwitchedOff() {
		BrainHarness h = harness(Surroundings.EMPTY);
		Inputs in = Reflexes.apply(h.context(swordReady(), EnumSet.of(Technique.REFLEXES)), BUSY, Inputs.IDLE);
		assertFalse(in.attack());
		assertEquals(0, in.strafe());
	}

	@Test
	void neverHitsACrystalStandingInFrontOfTheOpponent() {
		BrainHarness h = harness(new Surroundings(List.of(new Vec3(0, 0, 1.4)), List.of(), List.of()));
		assertFalse(Reflexes.apply(h.context(swordReady()), BUSY, Inputs.IDLE).attack(), "the click would set off the crystal at its own feet");
	}

	@Test
	void sidestepsInsteadOfStandingStillUnderPressure() {
		BrainHarness h = harness(Surroundings.EMPTY);
		// Pro strafe skill is below 1: over many ticks it sidesteps at least sometimes, never when strafing is off.
		boolean stepped = false;
		boolean steppedWhileOff = false;
		for (int i = 0; i < 50; i++) {
			BrainContext on = h.context(swordReady());
			on.memory.cooldownThreshold = 2; // no free hit, so only movement is tested
			stepped |= Reflexes.apply(on, BUSY, Inputs.IDLE).strafe() != 0;
			BrainContext off = h.context(swordReady(), Set.of(Technique.STRAFE));
			off.memory.cooldownThreshold = 2;
			steppedWhileOff |= Reflexes.apply(off, BUSY, Inputs.IDLE).strafe() != 0;
		}
		assertTrue(stepped, "sidestepped");
		assertFalse(steppedWhileOff, "strafing is switched off");
	}

	@Test
	void aSwitchedOffTechniqueIsNeverUsed() {
		BrainHarness h = harness(Surroundings.EMPTY);
		BrainContext c = h.context(swordReady(), EnumSet.of(Technique.W_TAP));
		assertEquals(0, c.skill(Technique.W_TAP));
		assertEquals(h.brain.profile().technique().sTapSkill(), c.skill(Technique.S_TAP));
		assertEquals(EnumSet.of(Technique.STRAFE, Technique.CRITS), Technique.parse("strafe, crits,bogus"));
	}

	@Test
	void engageNeverStrafesWhenStrafingIsOff() {
		BrainHarness h = harness(Surroundings.EMPTY);
		h.target = TestFixtures.target(new Vec3(0, 0, 4), 0);
		h.brain.setDisabledTechniques(EnumSet.of(Technique.STRAFE));
		for (int i = 0; i < 200; i++) {
			h.tick();
		}
		assertFalse(h.any(in -> in.strafe() != 0), "no strafing at all");
	}
}
