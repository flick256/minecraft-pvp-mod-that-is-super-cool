package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Milestone 6d: crystal PvP. Every step is a real click: obsidian and crystals are placed with ServerboundUseItemOnPacket. */
public class CrystalGameTests {
	private static void floor(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
		for (int x = 1; x < 7; x++) {
			for (int z = 1; z < 7; z++) {
				helper.setBlock(x, 0, z, block);
			}
		}
	}

	@GameTest(maxTicks = 400, padding = 24)
	public void botPlacesAndDetonatesACrystalOnObsidian(GameTestHelper helper) {
		TestSupport.arena(helper);
		floor(helper, Blocks.OBSIDIAN);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Crystaller", 1.5, 1, 3.5, -90, "pro", "sparbot_crystal"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Victim", 5.0, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int crystalsBefore = body.getInventory().countItem(Items.END_CRYSTAL);
		helper.succeedWhen(() -> {
			helper.assertTrue(bot.stats().blastHits() > 0, "a crystal the bot detonated hurt the opponent");
			helper.assertTrue(body.getInventory().countItem(Items.END_CRYSTAL) < crystalsBefore, "crystals were really used up");
			SparBot.LOGGER.info("Crystal test (obsidian floor): {}", bot.stats().summary());
			TestSupport.remove(bot, target);
		});
	}

	@GameTest(maxTicks = 400, padding = 24)
	public void botPutsDownObsidianForItsCrystal(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.certain(TestSupport.spawnBot(helper, "Builder", 1.5, 1, 3.5, -90, "pro", "sparbot_crystal"));
		BotPlayer body = TestSupport.body(bot);
		Bot target = TestSupport.dummy(helper, "Victim", 5.0, 1, 3.5, 90, "basic_sword");
		bot.setAssignedTarget(TestSupport.body(target).getUUID());
		int obsidianBefore = body.getInventory().countItem(Items.OBSIDIAN);
		helper.succeedWhen(() -> {
			helper.assertTrue(body.getInventory().countItem(Items.OBSIDIAN) < obsidianBefore, "obsidian was placed");
			helper.assertTrue(bot.stats().blastHits() > 0, "then a crystal on it hurt the opponent");
			SparBot.LOGGER.info("Crystal test (stone floor): {}", bot.stats().summary());
			TestSupport.remove(bot, target);
		});
	}
}
