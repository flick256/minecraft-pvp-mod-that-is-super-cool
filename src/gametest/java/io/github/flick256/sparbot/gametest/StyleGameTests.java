package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.style.Playstyle;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ServerPlayer;

/** Milestone 4: playstyles and the debug overlay. */
public class StyleGameTests {
	@GameTest(maxTicks = 60)
	public void debugOverlayShowsTheBotsMindOnlyToTheWatcher(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Shown", 2.5, 1, 3.5, -90, "pro", "basic_sword");
		Bot dummy = TestSupport.dummy(helper, "Seen", 5.0, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(dummy).getUUID());
		ServerPlayer viewer = TestSupport.spawnRealPlayer(helper, 4, 1, 6);
		ServerPlayer bystander = TestSupport.spawnRealPlayer(helper, 3, 1, 6);
		SparBot.debug().watch(viewer, bot);
		TestSupport.sentTo(viewer);
		TestSupport.sentTo(bystander);
		helper.runAfterDelay(20, () -> {
			List<Object> toViewer = TestSupport.sentTo(viewer);
			List<Object> toBystander = TestSupport.sentTo(bystander);
			boolean text = toViewer.stream().anyMatch(p -> p instanceof ClientboundSystemChatPacket bar && bar.overlay()
				&& bar.content().getString().contains(bot.name()) && bar.content().getString().contains("pro/balanced"));
			helper.assertTrue(text, "the watcher gets the bot's tactic and scores on the action bar");
			helper.assertTrue(toViewer.stream().anyMatch(p -> p instanceof ClientboundLevelParticlesPacket), "and its crosshair marker");
			helper.assertFalse(toBystander.stream().anyMatch(p -> p instanceof ClientboundSystemChatPacket bar && bar.overlay()
				&& bar.content().getString().contains(bot.name())), "nobody else sees it");
			SparBot.debug().stop(viewer);
			TestSupport.remove(bot, dummy);
			TestSupport.removeRealPlayer(viewer);
			TestSupport.removeRealPlayer(bystander);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void kiterOpensTheGapForItsBow(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 24);
		Bot kiter = TestSupport.spawnBot(helper, "Kiter", 3.5, 1, 6.5, 180, "pro", "sparbot_ranged");
		kiter.setPlaystyle(SparBot.playstyles().resolve("kiter"));
		Bot chaser = TestSupport.dummy(helper, "Chaser", 3.5, 1, 2.5, 0, "basic_sword");
		BotPlayer k = TestSupport.body(kiter);
		BotPlayer c = TestSupport.body(chaser);
		kiter.setAssignedTarget(c.getUUID());
		double[] widest = {0};
		helper.onEachTick(() -> widest[0] = Math.max(widest[0], k.distanceTo(c)));
		helper.succeedWhen(() -> {
			// It backs off to ~75% of its 12-block preferred range, then turns to shoot.
			helper.assertTrue(widest[0] > 8, "kiter opened the gap from 4 to bow range, widest " + widest[0]);
			helper.assertTrue(k.isUsingItem() || kiter.stats().rangedHits() > 0, "and started shooting");
			TestSupport.remove(kiter, chaser);
		});
	}

	@GameTest(maxTicks = 200, padding = 32)
	public void balancedBotClosesInInsteadOfKiting(GameTestHelper helper) {
		TestSupport.platform(helper, 8, 24);
		Bot fighter = TestSupport.spawnBot(helper, "Closer", 3.5, 1, 8.5, 180, "pro", "sparbot_ranged");
		Bot dummy = TestSupport.dummy(helper, "Still", 3.5, 1, 2.5, 0, "basic_sword");
		BotPlayer f = TestSupport.body(fighter);
		BotPlayer d = TestSupport.body(dummy);
		fighter.setAssignedTarget(d.getUUID());
		helper.succeedWhen(() -> {
			helper.assertTrue(f.distanceTo(d) < 3.5, "balanced bot walked in to melee, now " + f.distanceTo(d));
			TestSupport.remove(fighter, dummy);
		});
	}

	@GameTest(maxTicks = 40)
	public void playstyleMixesApplyToALiveBot(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Mixed", 4, 1, 4, 0, "advanced", "basic_sword");
		Playstyle mix = SparBot.playstyles().resolve("aggressive_rusher:0.7,kiter:0.3");
		bot.setPlaystyle(mix);
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(bot.trace().note().contains("style=" + mix.id()), "the brain runs the mix: " + bot.trace().note());
			helper.assertTrue(bot.profile().id().equals("advanced"), "skill level unchanged");
			TestSupport.remove(bot);
			helper.succeed();
		});
	}
}
