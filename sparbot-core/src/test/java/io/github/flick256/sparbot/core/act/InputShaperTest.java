package io.github.flick256.sparbot.core.act;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import org.junit.jupiter.api.Test;

class InputShaperTest {
	private static final Inputs CLICK_AND_FLICK = new Inputs(170, 80, 1, 0, false, false, true, true, false, -1);

	@Test
	void rotationNeverExceedsProfileCap() {
		for (String id : new String[] {"beginner", "pro"}) {
			SkillProfile profile = TestFixtures.preset(id);
			InputShaper shaper = new InputShaper(profile, new Rng(1), 20);
			for (int i = 0; i < 500; i++) {
				Inputs out = shaper.shape(CLICK_AND_FLICK);
				double turn = Math.hypot(out.yawDelta(), out.pitchDelta());
				assertTrue(turn <= profile.aim().maxTurnDegPerTick() + 1e-3, id + " turned " + turn);
				assertTrue(turn <= InputShaper.ABSOLUTE_MAX_TURN_DEG + 1e-3);
			}
		}
	}

	@Test
	void clicksNeverExceedProfileMaxCps() {
		for (String id : new String[] {"beginner", "intermediate", "pro"}) {
			SkillProfile profile = TestFixtures.preset(id);
			InputShaper shaper = new InputShaper(profile, new Rng(2), 20);
			int clicks = 0;
			int ticks = 20 * 60;
			for (int i = 0; i < ticks; i++) {
				if (shaper.shape(CLICK_AND_FLICK).attack()) {
					clicks++;
				}
			}
			double cps = clicks / 60.0;
			assertTrue(cps <= profile.clicking().cps().max() + 0.1, id + " clicked at " + cps + " cps");
			assertTrue(cps >= profile.clicking().cps().min() * 0.8, id + " clicked too slowly: " + cps);
		}
	}

	@Test
	void serverCpsCapIsHonoured() {
		InputShaper shaper = new InputShaper(TestFixtures.preset("pro"), new Rng(3), 5);
		int clicks = 0;
		for (int i = 0; i < 20 * 30; i++) {
			if (shaper.shape(CLICK_AND_FLICK).attack()) {
				clicks++;
			}
		}
		assertTrue(clicks / 30.0 <= 5.05, "cps " + clicks / 30.0);
	}

	@Test
	void inputsArriveAfterSimulatedLatency() {
		SkillProfile profile = TestFixtures.preset("beginner");
		InputShaper shaper = new InputShaper(profile, new Rng(4), 20);
		int latency = shaper.latencyTicks();
		assertTrue(latency >= 0);
		Inputs first = new Inputs(5, 0, 1, 0, false, false, false, false, false, -1);
		Inputs out = shaper.shape(first);
		if (latency > 0) {
			assertEquals(0, out.yawDelta(), "mouse movement must not arrive before the latency elapses");
			assertFalse(out.attack());
		} else {
			assertEquals(5, out.yawDelta(), 1e-6);
		}
	}

	@Test
	void inventoryClicksNeedTheScreenOpenFirstAndBlockEverythingElse() {
		SkillProfile profile = TestFixtures.preset("pro");
		InputShaper shaper = new InputShaper(profile, new Rng(9), 20);
		InventoryClick click = new InventoryClick(20, 40);
		int firstClickTick = -1;
		int clicks = 0;
		for (int t = 0; t < 40; t++) {
			Inputs desired = new Inputs(30, 10, 1, 1, true, false, true, true, true, 3, true, true, click);
			Inputs out = shaper.shape(desired);
			if (out.inventoryOpen()) {
				assertEquals(0, out.yawDelta());
				assertEquals(0, out.forward());
				assertFalse(out.attack() || out.use() || out.jump() || out.sprint() || out.swapOffhand());
				assertEquals(-1, out.hotbarSlot());
			}
			if (out.inventoryClick() != null) {
				clicks++;
				if (firstClickTick < 0) {
					firstClickTick = t;
				}
			}
		}
		assertTrue(firstClickTick >= InputShaper.MIN_TICKS_OPEN_BEFORE_CLICK, "clicked on tick " + firstClickTick);
		assertTrue(clicks <= 40 / InputShaper.MIN_TICKS_BETWEEN_INVENTORY_ACTIONS, "too many clicks: " + clicks);
	}

	@Test
	void swapKeyIsRateLimited() {
		InputShaper shaper = new InputShaper(TestFixtures.preset("pro"), new Rng(10), 20);
		int swaps = 0;
		for (int t = 0; t < 40; t++) {
			if (shaper.shape(Inputs.IDLE.withSwapOffhand(true)).swapOffhand()) {
				swaps++;
			}
		}
		assertTrue(swaps <= 20, "swaps " + swaps);
	}
}
