package io.github.flick256.sparbot.core.aim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class BallisticsTest {
	@Test
	void solvedPitchActuallyLandsOnTarget() {
		for (Ballistics.Projectile p : new Ballistics.Projectile[] {Ballistics.ARROW_FULL_BOW, Ballistics.ARROW_CROSSBOW, Ballistics.ENDER_PEARL, Ballistics.HOOK}) {
			double[][] shots = {{10, 0}, {20, -1.5}, {15, 3}};
			for (double[] shot : shots) {
				if (p == Ballistics.HOOK && shot[0] > 12) {
					continue; // a fishing hook only reaches a dozen blocks or so
				}
				OptionalDouble pitch = Ballistics.solvePitch(p, shot[0], shot[1]);
				assertTrue(pitch.isPresent(), p.name() + " should reach " + shot[0]);
				double height = Ballistics.simulate(p, (float) pitch.getAsDouble(), shot[0]).heightAtTarget();
				assertEquals(shot[1], height, 0.05, p.name() + " lands at " + height + " instead of " + shot[1]);
			}
		}
	}

	@Test
	void arrowNeedsToAimSlightlyUpAtRangeAndFliesFast() {
		double pitch = Ballistics.solvePitch(Ballistics.ARROW_FULL_BOW, 30, 0).orElseThrow();
		assertTrue(pitch < 0 && pitch > -10, "pitch " + pitch);
		int ticks = Ballistics.flightTicks(Ballistics.ARROW_FULL_BOW, (float) pitch, 30);
		assertTrue(ticks >= 10 && ticks <= 13, "a 3.0 arrow with drag 0.99 covers 30 blocks in ~11 ticks, took " + ticks);
	}

	@Test
	void pearlsNeedAHighArcAndCannotReachForever() {
		double pitch = Ballistics.solvePitch(Ballistics.ENDER_PEARL, 30, 0).orElseThrow();
		assertTrue(pitch < -10 && pitch > -25, "pearl pitch " + pitch);
		assertTrue(Ballistics.solvePitch(Ballistics.ENDER_PEARL, 300, 0).isEmpty(), "pearls cannot fly 300 blocks");
	}
}
