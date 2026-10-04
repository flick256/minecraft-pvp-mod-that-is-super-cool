package io.github.flick256.sparbot.core.brain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import org.junit.jupiter.api.Test;

class MovementTest {
	@Test
	void movementYawFollowsVanillaInputRotation() {
		// Facing south (yaw 0): forward goes south, "left" (vanilla leftImpulse) goes east (yaw -90).
		assertEquals(0, Movement.movementYaw(0, 1, 0), 1e-3);
		assertEquals(180, Math.abs(Movement.movementYaw(0, -1, 0)), 1e-3);
		assertEquals(-90, Movement.movementYaw(0, 0, 1), 1e-3);
		assertEquals(90, Movement.movementYaw(0, 0, -1), 1e-3);
		// Facing west (yaw 90): forward goes west.
		assertEquals(90, Movement.movementYaw(90, 1, 0), 1e-3);
	}

	@Test
	void directionIndexWrapsAround() {
		assertEquals(0, Movement.directionIndex(0));
		assertEquals(2, Movement.directionIndex(90));
		assertEquals(4, Movement.directionIndex(180));
		assertEquals(4, Movement.directionIndex(-180));
		assertEquals(6, Movement.directionIndex(-90));
		assertEquals(7, Movement.directionIndex(-45));
	}

	@Test
	void walkingForwardOffACliffIsBlocked() {
		int[] drops = TestFixtures.flatGround();
		drops[0] = SelfState.VOID_DROP; // cliff to the south
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, drops);
		assertFalse(Movement.isSafe(self, 0, 1, 0));
		assertTrue(Movement.isSafe(self, 0, -1, 0));
		assertTrue(Movement.isSafe(self, 180, 1, 0));
	}

	@Test
	void walksAroundLavaInsteadOfFreezing() {
		int[] drops = TestFixtures.flatGround();
		// Lava straight ahead (south) and to the south-east: the bot heads south-west around it.
		drops[0] = SelfState.VOID_DROP;
		drops[7] = SelfState.VOID_DROP;
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, drops);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(), TestFixtures.target(new Vec3(0, 0, 6), 0));
		io.github.flick256.sparbot.core.act.Inputs in = Movement.guardEdges(h.context(self),
			new io.github.flick256.sparbot.core.act.Inputs(0, 0, 1, 0, false, false, true, false, false, -1));
		assertEquals(1, in.forward(), "still moving towards the opponent");
		assertEquals(-1, in.strafe(), "stepping to the west, around the lava");
		assertTrue(Movement.isSafe(self, 0, in.forward(), in.strafe()));
	}

	@Test
	void stopsWhenEveryWayForwardIsUnsafe() {
		int[] drops = new int[SelfState.DIRECTIONS];
		java.util.Arrays.fill(drops, SelfState.VOID_DROP);
		drops[4] = 0; // only backwards is safe
		SelfState self = TestFixtures.self(Vec3.ZERO, 0, 0, 20, 1, true, drops);
		BrainHarness h = new BrainHarness(TestFixtures.flawless("pro"), TestFixtures.inventory(), TestFixtures.target(new Vec3(0, 0, 6), 0));
		io.github.flick256.sparbot.core.act.Inputs in = Movement.guardEdges(h.context(self),
			new io.github.flick256.sparbot.core.act.Inputs(0, 0, 1, 0, false, false, true, false, false, -1));
		assertEquals(0, in.forward());
		assertEquals(0, in.strafe());
	}
}
