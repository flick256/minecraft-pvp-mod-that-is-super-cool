package io.github.flick256.sparbot.core.aim;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import org.junit.jupiter.api.Test;

class AimControllerTest {
	private static float settleError(String profileId, long seed) {
		AimController aim = new AimController(TestFixtures.preset(profileId).aim(), new Rng(seed));
		float yaw = 0;
		float pitch = 0;
		float cap = (float) TestFixtures.preset(profileId).aim().maxTurnDegPerTick();
		float total = 0;
		for (int tick = 0; tick < 60; tick++) {
			float[] d = aim.step(yaw, pitch, 120, 10);
			float magnitude = (float) Math.hypot(d[0], d[1]);
			if (magnitude > cap) {
				d[0] *= cap / magnitude;
				d[1] *= cap / magnitude;
			}
			yaw += d[0];
			pitch += d[1];
			if (tick >= 40) {
				total += Angles.yawDistance(yaw, 120);
			}
		}
		return total / 20;
	}

	@Test
	void aimConvergesOnTarget() {
		assertTrue(settleError("pro", 1) < 3, "pro should settle within a few degrees");
		assertTrue(settleError("beginner", 1) < 12, "even a beginner gets roughly on target");
	}

	@Test
	void proAimsMoreSteadilyThanBeginner() {
		float pro = 0;
		float beginner = 0;
		for (long seed = 0; seed < 20; seed++) {
			pro += settleError("pro", seed);
			beginner += settleError("beginner", seed);
		}
		assertTrue(pro < beginner, "pro " + pro + " vs beginner " + beginner);
	}
}
