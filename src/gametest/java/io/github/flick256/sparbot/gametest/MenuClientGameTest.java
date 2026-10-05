package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.client.SparBotMenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Milestone 7: the client menu, driven like a player would in a real (headless) client: press B, spawn a
 * bot from the Spawn tab, see it in the Bots tab, change a setting and remove the bot. Screenshots of
 * each tab land in the client game test run directory.
 */
public class MenuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			world.getServer().runCommand("sparbot config set autoTarget false");
			context.waitTicks(5);

			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitForScreen(SparBotMenuScreen.class);
			context.takeScreenshot("sparbot-menu-bots-empty");

			context.clickScreenButton("Spawn");
			context.waitTick();
			context.takeScreenshot("sparbot-menu-spawn");
			context.clickScreenButton("Spawn bot");
			world.getServer().waitFor(server -> SparBot.bots().get("Bot1").isPresent());

			context.clickScreenButton("Bots");
			context.waitTicks(15);
			context.takeScreenshot("sparbot-menu-bots");
			context.clickScreenButton("Tech");
			context.waitTicks(2);
			context.takeScreenshot("sparbot-menu-techniques");
			// Switch strafing off, and the melee to the learned sword model.
			context.clickScreenButton("strafe");
			world.getServer().waitFor(server -> SparBot.bots().get("Bot1").map(b -> b.disabledTechniques().contains(
				io.github.flick256.sparbot.core.brain.Technique.STRAFE)).orElse(false));
			context.waitTicks(10);
			context.clickScreenButton("melee");
			world.getServer().waitFor(server -> SparBot.bots().get("Bot1").map(b -> "sword".equals(b.modelId())).orElse(false));

			context.clickScreenButton("Kits");
			context.waitTicks(2);
			context.takeScreenshot("sparbot-menu-kits");
			context.clickScreenButton("Equip");
			boolean equipped = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().stream()
				.anyMatch(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer) && !p.getInventory().isEmpty()));
			for (int i = 0; i < 20 && !equipped; i++) {
				context.waitTick();
				equipped = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().stream()
					.anyMatch(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer) && !p.getInventory().isEmpty()));
			}
			if (!equipped) {
				throw new AssertionError("Equip didn't give the player a kit");
			}

			context.clickScreenButton("Save layout");
			world.getServer().waitFor(server -> server.getPlayerList().getPlayers().stream()
				.anyMatch(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer) && !SparBot.playerLayouts().kits(p.getUUID()).isEmpty()));

			context.clickScreenButton("You");
			context.waitTicks(2);
			context.takeScreenshot("sparbot-menu-you");
			world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().stream()
				.filter(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer)).forEach(p -> p.setHealth(5)));
			context.clickScreenButton("Full heal");
			world.getServer().waitFor(server -> server.getPlayerList().getPlayers().stream()
				.anyMatch(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer) && p.getHealth() >= p.getMaxHealth()));

			context.clickScreenButton("Settings");
			context.waitTicks(2);
			context.takeScreenshot("sparbot-menu-settings");

			context.clickScreenButton("Bots");
			context.waitTicks(2);
			context.clickScreenButton("Remove");
			world.getServer().waitFor(server -> SparBot.bots().get("Bot1").isEmpty());

			boolean autoTargetOff = world.getServer().computeOnServer(server -> !SparBot.config().autoTarget);
			if (!autoTargetOff) {
				throw new AssertionError("the config command didn't apply");
			}
			world.getServer().runCommand("sparbot config set autoTarget true");
			context.clickScreenButton("Done");
		}
	}
}
