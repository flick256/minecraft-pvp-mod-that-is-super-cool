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

			// The practice world: the Practice tab takes you to the hub, which is built on the way.
			context.clickScreenButton("Practice");
			context.waitTicks(2);
			context.takeScreenshot("sparbot-menu-practice");
			context.clickScreenButton("Hub");
			world.getServer().waitFor(server -> human(server) != null && human(server).level().dimension().equals(
				io.github.flick256.sparbot.practice.PracticeWorld.DIMENSION), 600);
			context.clickScreenButton("Done");
			String pond = world.getServer().computeOnServer(server -> human(server).level().getBlockState(new net.minecraft.core.BlockPos(15, 63, 156)).toString());
			if (!pond.contains("water")) {
				throw new AssertionError("the meadow's pond has no water: " + pond);
			}
			world.getServer().runCommand("time set noon");
			view(context, world, "sparbot-practice-hub", 0.5, 72, -24, 0, 18);
			view(context, world, "sparbot-practice-hub-gateway", 0.5, 70, 6, 0, 10);
			// The Arcane Colosseum, outside and in.
			int cx = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_X;
			int cz = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_Z;
			view(context, world, "colosseum-arrival", cx + 0.5, 64, cz + 65.5, 180, -12);
			view(context, world, "colosseum-aerial", cx + 0.5, 150, cz + 78, 180, 48);
			view(context, world, "colosseum-diagonal", cx + 70, 120, cz + 70, 135, 35);
			view(context, world, "colosseum-low", cx - 95, 72, cz + 40, -112, -6);
			view(context, world, "colosseum-tunnel", cx + 0.5, 64, cz + 52, 180, 0);
			view(context, world, "colosseum-field-edge", cx + 0.5, 65, cz + 27, 180, -10);
			view(context, world, "colosseum-field-centre", cx + 0.5, 64, cz + 0.5, 0, -25);
			view(context, world, "colosseum-royal-box", cx + 0.5, 85, cz + 48.5, 180, 22);
			view(context, world, "colosseum-promenade", cx + 30, 81, cz + 34, 140, 6);
			view(context, world, "colosseum-floor", cx + 0.5, 92, cz + 0.5, 0, 90);
			view(context, world, "colosseum-citadel", cx + 0.5, 122, cz - 50, 180, 2);
			view(context, world, "colosseum-waterfall", cx + 82, 92, cz + 58, 139, 5);
			view(context, world, "colosseum-portal", cx + 0.5, 70, cz + 80, 180, -22);
			world.getServer().runCommand("time set midnight");
			view(context, world, "colosseum-night-inside", cx + 0.5, 85, cz + 48.5, 180, 16);
			view(context, world, "colosseum-night-outside", cx + 0.5, 100, cz + 92, 180, 22);
			world.getServer().runCommand("time set noon");
			// It resets once everyone has left: stand in the field, dig a hole and place a block, leave, and it is back.
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(), cx + 0.5, 64,
				cz + 0.5, java.util.Set.of(), 0, 0, true));
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				var level = (net.minecraft.server.level.ServerLevel) human(server).level();
				level.setBlock(new net.minecraft.core.BlockPos(cx + 3, 63, cz + 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(new net.minecraft.core.BlockPos(cx + 3, 64, cz + 4), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
			});
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(), 0.5, 64, -3.5,
				java.util.Set.of(), 0, 0, true));
			world.getServer().waitFor(server -> {
				var level = human(server).level();
				return !level.getBlockState(new net.minecraft.core.BlockPos(cx + 3, 63, cz + 3)).isAir()
					&& level.getBlockState(new net.minecraft.core.BlockPos(cx + 3, 64, cz + 4)).isAir();
			}, 1200);
			// A fight in the meadow against a pro, started like the Fight button does.
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight uhc_duel pro"));
			world.getServer().waitFor(server -> SparBot.matches().match("practice_uhc").isPresent(), 600);
			context.waitTicks(80);
			world.getConnection().waitForChunksRender();
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTick();
			context.takeScreenshot("sparbot-practice-uhc-fight");
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			world.getServer().runOnServer(server -> {
				SparBot.matches().stop(server, "practice_uhc");
				SparBot.bots().get("SparUhc").ifPresent(b -> SparBot.bots().remove(b, "test over"));
			});
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice leave"));
			world.getServer().waitFor(server -> human(server) != null && human(server).level() == server.overworld(), 200);
		}
	}

	private static net.minecraft.server.level.ServerPlayer human(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer)).findFirst().orElse(null);
	}

	/** Puts the player (in spectator, so nothing is in the way) at a viewpoint and takes a screenshot once the chunks show. */
	private static void view(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			var player = human(server);
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
		});
		context.waitTicks(40);
		world.getConnection().waitForChunksRender();
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(10);
		context.takeScreenshot(name);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		world.getServer().runOnServer(server -> human(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
	}
}
