package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The learned sword model in the real game: it must land hits on a target, and in full fights against
 * the scripted pro it must fight properly (no lock-ups, damage dealt). Each fight's result is logged,
 * which shows whether what it learned in the simulator carries over to Minecraft.
 */
public class LearnedGameTests {
	private static final int FIGHT_TICKS = 900;

	@GameTest(maxTicks = 200)
	public void learnedBotLandsHits(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Learner", 1.5, 1, 3.5, -90, "pro", "basic_sword");
		bot.setModel("sword", SparBot.models().get("sword").orElseThrow());
		Bot target = TestSupport.dummy(helper, "Dummy", 5.5, 1, 3.5, 90, "basic_sword");
		BotPlayer t = TestSupport.body(target);
		bot.setAssignedTarget(t.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(bot.stats().hits() >= 2, "the learned bot landed hits");
			TestSupport.remove(bot, target);
		});
	}

	private static void fight(GameTestHelper helper, int round) {
		fight(helper, round, "sword", "basic_sword");
	}

	private static void fight(GameTestHelper helper, int round, String model, String kit) {
		FightGameTests.ring(helper);
		Bot learned = TestSupport.spawnBot(helper, "Learned", 4.5, 1, 7.5, -90, "pro", kit);
		learned.setModel(model, SparBot.models().get(model).orElseThrow());
		Bot scripted = TestSupport.spawnBot(helper, "Scripted", 11.5, 1, 7.5, 90, "pro", kit);
		learned.setAssignedTarget(TestSupport.body(scripted).getUUID());
		scripted.setAssignedTarget(TestSupport.body(learned).getUUID());
		FightDiagnostics diagnostics = new FightDiagnostics(learned, scripted);
		boolean[] over = {false};
		helper.onEachTick(() -> {
			diagnostics.tick(helper);
			BotPlayer a = learned.body();
			BotPlayer b = scripted.body();
			if (!over[0] && (a == null || b == null || !a.isAlive() || !b.isAlive() || helper.getTick() >= FIGHT_TICKS)) {
				over[0] = true;
				float la = a == null || !a.isAlive() ? 0 : a.getHealth();
				float lb = b == null || !b.isAlive() ? 0 : b.getHealth();
				String winner = la > lb ? "learned" : lb > la ? "scripted" : "draw";
				SparBot.LOGGER.info("Learned {} vs scripted pro, round {}: {} wins ({} vs {} health, {} ticks); learned {}; scripted {}", model, round, winner, la, lb,
					helper.getTick(), learned.stats().summary(), scripted.stats().summary());
				diagnostics.log();
				int stall = diagnostics.longestStall();
				double dealt = learned.stats().damageDealt();
				TestSupport.remove(learned, scripted);
				helper.assertTrue(stall <= FightDiagnostics.STALL_LIMIT, "the learned bot locked up for " + stall + " ticks");
				helper.assertTrue(dealt >= 4, "the learned bot fought (dealt " + dealt + ")");
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedVsScriptedRound1(GameTestHelper helper) {
		fight(helper, 1);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedVsScriptedRound2(GameTestHelper helper) {
		fight(helper, 2);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedVsScriptedRound3(GameTestHelper helper) {
		fight(helper, 3);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedVsScriptedRound4(GameTestHelper helper) {
		fight(helper, 4);
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedUhcVsScriptedRound1(GameTestHelper helper) {
		fight(helper, 1, "uhc", "sparbot_uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedUhcVsScriptedRound2(GameTestHelper helper) {
		fight(helper, 2, "uhc", "sparbot_uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedUhcVsScriptedRound3(GameTestHelper helper) {
		fight(helper, 3, "uhc", "sparbot_uhc");
	}

	@GameTest(maxTicks = FIGHT_TICKS + 20, padding = 40)
	public void learnedUhcVsScriptedRound4(GameTestHelper helper) {
		fight(helper, 4, "uhc", "sparbot_uhc");
	}

	@GameTest(maxTicks = 2400)
	public void trainingOnTheServerProducesAUsableModel(GameTestHelper helper) {
		java.util.List<String> lines = new java.util.concurrent.CopyOnWriteArrayList<>();
		io.github.flick256.sparbot.model.TrainingJob job = io.github.flick256.sparbot.model.TrainingJob.start(helper.getLevel().getServer(),
			SparBot.modelsDir(), io.github.flick256.sparbot.core.ml.Train.Mode.SWORD, "gametest_model", 10, null, lines::add);
		// Game ticks run as fast as they can in a test, so wait for the real time the first step takes.
		try {
			helper.assertTrue(job.awaitFirstModel(180), "the imitation step finished within 3 minutes");
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(SparBot.models().get("gametest_model").isPresent(), "the first model was saved and registered");
			job.stop();
			Bot bot = TestSupport.spawnBot(helper, "Trained", 2.5, 1, 3.5, 0, "pro", "basic_sword");
			bot.setModel("gametest_model", SparBot.models().get("gametest_model").orElseThrow());
			helper.assertTrue("gametest_model".equals(bot.modelId()), "a bot fights with it");
			SparBot.LOGGER.info("Training test progress: {}", lines);
			TestSupport.remove(bot);
			try {
				java.nio.file.Files.deleteIfExists(SparBot.modelsDir().resolve("gametest_model.json"));
			} catch (java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		});
	}
}
