package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.practice.colosseum.ColosseumSurvey;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The colosseum's blueprint, walked like a player would (see {@link ColosseumSurvey}): every hall and every stair
 * level in a quarter of the stands can be reached from the gate, the second concourse from the seats, and no
 * water has an open side to run out of.
 */
public class ColosseumGameTests {
	@GameTest
	public void everyHallCanBeReachedAndNoWaterLeaks(GameTestHelper helper) {
		long started = System.currentTimeMillis();
		List<String> problems = ColosseumSurvey.run();
		io.github.flick256.sparbot.SparBot.LOGGER.info("Colosseum survey: {} problems in {} ms", problems.size(), System.currentTimeMillis() - started);
		helper.assertTrue(problems.isEmpty(), "the colosseum survey found:\n" + String.join("\n", problems));
		helper.succeed();
	}
}
