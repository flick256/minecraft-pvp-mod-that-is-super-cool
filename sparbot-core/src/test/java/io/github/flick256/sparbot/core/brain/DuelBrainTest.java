package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import org.junit.jupiter.api.Test;

class DuelBrainTest {
	/** Drives the brain against a stationary target, applying rotations, and returns the inputs per tick. */
	private static Inputs[] run(SkillProfile profile, Vec3 targetPos, float startYaw, float health, float attackStrength, int ticks) {
		DuelBrain brain = new DuelBrain(profile, 42);
		float yaw = startYaw;
		float pitch = 0;
		Inputs[] out = new Inputs[ticks];
		for (int t = 0; t < ticks; t++) {
			SelfState self = TestFixtures.self(Vec3.ZERO, yaw, pitch, health, attackStrength, true, TestFixtures.flatGround());
			TargetState target = TestFixtures.target(targetPos, 0);
			Inputs in = brain.act(new Observation(t, self, target));
			yaw = Angles.wrapDegrees(yaw + in.yawDelta());
			pitch = Math.max(-90, Math.min(90, pitch + in.pitchDelta()));
			out[t] = in;
		}
		return out;
	}

	@Test
	void turnsTowardsAndAttacksTargetInReach() {
		Inputs[] inputs = run(TestFixtures.flawless("pro"), new Vec3(0, 0, 2.5), 90, 20, 1.0F, 60);
		boolean attacked = false;
		for (Inputs in : inputs) {
			attacked |= in.attack();
		}
		assertTrue(attacked, "a pro with full charge and the target in reach should attack");
	}

	@Test
	void neverClicksAtTargetOutOfReachWithoutMistakes() {
		Inputs[] inputs = run(TestFixtures.flawless("pro"), new Vec3(0, 0, 8), 0, 20, 1.0F, 60);
		for (Inputs in : inputs) {
			assertFalse(in.attack(), "flawless bot must not swing at a target 8 blocks away");
		}
	}

	@Test
	void disciplinedBotWaitsForAttackCharge() {
		Inputs[] inputs = run(TestFixtures.flawless("pro"), new Vec3(0, 0, 2.5), 0, 20, 0.3F, 60);
		for (Inputs in : inputs) {
			assertFalse(in.attack(), "pro should not spam at 30% charge");
		}
	}

	@Test
	void walksTowardsDistantTarget() {
		Inputs[] inputs = run(TestFixtures.flawless("intermediate"), new Vec3(0, 0, 10), 0, 20, 1.0F, 40);
		assertEquals(1, inputs[39].forward());
		assertTrue(inputs[39].sprint());
	}

	@Test
	void retreatsWhenBelowPanicThreshold() {
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 7);
		SelfState hurt = TestFixtures.self(Vec3.ZERO, 0, 0, 2, 1.0F, true, TestFixtures.flatGround());
		brain.act(new Observation(0, hurt, TestFixtures.target(new Vec3(0, 0, 4), 0)));
		for (int t = 1; t < 20; t++) {
			brain.act(new Observation(t, hurt, TestFixtures.target(new Vec3(0, 0, 4), 0)));
		}
		assertEquals("retreat", brain.lastTrace().tactic());
	}

	@Test
	void searchesWhenNoTarget() {
		DuelBrain brain = new DuelBrain(TestFixtures.preset("casual"), 1);
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1.0F, true, TestFixtures.flatGround());
		Inputs in = brain.act(new Observation(0, self, null));
		assertEquals("search", brain.lastTrace().tactic());
		assertFalse(in.attack());
	}

	@Test
	void doesNotWalkOffCliffWithoutMistakes() {
		int[] drops = TestFixtures.flatGround();
		drops[0] = SelfState.VOID_DROP;
		DuelBrain brain = new DuelBrain(TestFixtures.flawless("pro"), 3);
		for (int t = 0; t < 40; t++) {
			SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1.0F, true, drops);
			Inputs in = brain.act(new Observation(t, self, TestFixtures.target(new Vec3(0, 0, 10), 0)));
			assertTrue(Movement.isSafe(self, self.yaw() + in.yawDelta(), in.forward(), in.strafe()), "tick " + t + " walked off the cliff");
		}
	}
	@Test
	void placingUtilityDelaysNoticingOpponentMotionAndAttentionRecovers() {
		class Watching implements Tactic {
			boolean focused;
			double lastSeenX;
			public String name() { return "watching"; }
			public boolean requiresFocus() { return focused; }
			public double score(BrainContext c) { return 2; }
			public Inputs act(BrainContext c) { lastSeenX = c.seen().position().x(); return Inputs.IDLE; }
		}
		Watching busy = new Watching();
		Watching free = new Watching();
		busy.focused = true;
		DuelBrain occupied = new DuelBrain(TestFixtures.flawless("pro"), io.github.flick256.sparbot.core.style.Playstyle.BALANCED, 42, busy);
		DuelBrain watching = new DuelBrain(TestFixtures.flawless("pro"), io.github.flick256.sparbot.core.style.Playstyle.BALANCED, 42, free);
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, TestFixtures.flatGround());
		for (int t = 0; t < 30; t++) {
			Observation o = new Observation(t, self, TestFixtures.target(new Vec3(t * 0.1, 0, 8), 0));
			occupied.act(o); watching.act(o);
		}
		assertTrue(busy.lastSeenX < free.lastSeenX, "attention on placement notices movement later");
		busy.focused = false;
		for (int t = 30; t < 50; t++) {
			Observation o = new Observation(t, self, TestFixtures.target(new Vec3(t * 0.1, 0, 8), 0));
			occupied.act(o); watching.act(o);
		}
		assertEquals(free.lastSeenX, busy.lastSeenX, 0.001, "tracking recovers after returning attention to the opponent");
	}

}
