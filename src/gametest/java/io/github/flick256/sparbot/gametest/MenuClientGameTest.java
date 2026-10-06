package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.practice.colosseum.ColosseumSurvey;
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
			// The Celestial Colosseum, built in the background: wait for it, then look round outside and in.
			world.getServer().waitFor(server -> SparBot.practice().colosseumReady(), 12000);
			int cx = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_X;
			int cz = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_Z;
			// It is six hundred blocks across: look further than usual.
			int distance = context.computeOnClient(client -> client.options.renderDistance().get());
			context.runOnClient(client -> client.options.renderDistance().set(12));
			view(context, world, "colosseum-arrival", cx + 0.5, 64, cz + 226.5, 180, -8);
			view(context, world, "colosseum-approach", cx + 0.5, 110, cz + 262, 180, 12);
			view(context, world, "colosseum-aerial", cx + 0.5, 230, cz + 170, 180, 45);
			view(context, world, "colosseum-knights", cx + 70, 110, cz + 250, 150, 8);
			view(context, world, "colosseum-tunnel", cx + 0.5, 65, cz + 56, 180, -4);
			view(context, world, "colosseum-field", cx + 0.5, 66, cz + 0.5, 0, -22);
			view(context, world, "colosseum-field-north", cx + 0.5, 70, cz + 30, 180, -18);
			view(context, world, "colosseum-sky", cx + 0.5, 66, cz + 20, 180, -62);
			view(context, world, "colosseum-top-tier", cx + 0.5, 164, cz + 140, 180, 18);
			view(context, world, "colosseum-imperial-box", cx + 0.5, 96, cz + 70, 180, 12);
			view(context, world, "colosseum-imperial-box-front", cx + 0.5, 82, cz + 40, 0, -14);
			view(context, world, "colosseum-undercroft", cx + 70.5, 68, cz + 70.5, 45, 0);
			view(context, world, "colosseum-dragon", cx - 40, 240, cz - 40, 142, 5);
			view(context, world, "colosseum-whale", cx - 180, 215, cz + 90, 136, 5);
			view(context, world, "colosseum-phoenix", cx + 170, 250, cz + 20, -134, 5);
			view(context, world, "colosseum-fire", cx + 116, 120, cz - 116, -135, 30);
			view(context, world, "colosseum-frost", cx + 116, 120, cz + 116, -45, 30);
			view(context, world, "colosseum-citadel", cx + 0.5, 190, cz - 190, 180, 4);
			view(context, world, "colosseum-corridor", cx + 64.0, 65.6, cz + 53.7, 40.0F, 5.0F);
			view(context, world, "colosseum-atrium", cx + 88.5, 65.6, cz + 0.5, -90.0F, -25.0F);
			view(context, world, "colosseum-bridge", cx + 113.5, 89.6, cz + 0.5, 90.0F, 10.0F);
			view(context, world, "colosseum-vomitory", cx + 84.0, 108.6, cz + 9.0, -90.0F, 0.0F);
			view(context, world, "colosseum-archive", cx + 87.1, 65.6, cz + 29.6, -71.3F, 15.0F);
			view(context, world, "colosseum-warden", cx + 11.7, 65.6, cz + -58.8, -168.8F, 15.0F);
			view(context, world, "colosseum-chapel", cx + -89.3, 65.6, cz + -17.8, 101.3F, 15.0F);
			view(context, world, "colosseum-cells", cx + -23.8, 65.6, cz + -70.1, -18.8F, 30.0F);
			view(context, world, "colosseum-treasury", cx + 24.6, 65.6, cz + 123.6, -11.3F, 15.0F);
			view(context, world, "colosseum-champions", cx + 123.6, 65.6, cz + -24.6, -101.3F, 15.0F);
			view(context, world, "colosseum-concourse", cx + -42.0, 108.6, cz + 72.7, -150.0F, 20.0F);
			// The stair halls, halls of a dozen kinds, and the way down to the Heartwell.
			for (ColosseumSurvey.View v : ColosseumSurvey.stairs()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			for (ColosseumSurvey.View v : ColosseumSurvey.halls(12)) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			for (ColosseumSurvey.View v : ColosseumSurvey.heartwell()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			// A vault: crouch at its cracked wall until it gives way, then look inside.
			int[] door = ColosseumSurvey.vaultEntrance(0);
			world.getServer().runOnServer(server -> {
				var player = human(server);
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				double back = 1.6;
				double a = Math.atan2(door[2], door[0]);
				player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), cx + door[0] + 0.5 - Math.cos(a) * back, door[1],
					cz + door[2] + 0.5 - Math.sin(a) * back, java.util.Set.of(), 0, 0, true);
			});
			context.waitTicks(20);
			context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
			world.getServer().waitFor(server -> human(server).level().getBlockState(new net.minecraft.core.BlockPos(cx + door[0], door[1], cz + door[2])).isAir(),
				400);
			context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
			ColosseumSurvey.View inside = ColosseumSurvey.vault(0);
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(),
				cx + inside.x(), Math.floor(inside.y()), cz + inside.z(), java.util.Set.of(), inside.yaw(), 0, true));
			context.waitTicks(30);
			view(context, world, inside.name(), cx + inside.x(), inside.y(), cz + inside.z(), inside.yaw(), inside.pitch());
			// The fight: step into the Heartwell, see Vaelor rise, fight a moment, then he falls; the champion's rewards and plaque.
			double[] step = ColosseumSurvey.arenaStep();
			world.getServer().runOnServer(server -> {
				var player = human(server);
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), cx + step[0], step[1], cz + step[2], java.util.Set.of(), 0, 0, true);
			});
			world.getServer().waitFor(server -> SparBot.practice().crown().fight().running(), 200);
			context.waitTicks(105);
			world.getConnection().waitForChunksRender(false, 600);
			context.takeScreenshot("heartwell-vaelor-rises");
			world.getServer().waitFor(server -> SparBot.bots().get("Vaelor").map(b -> !b.brainPaused()).orElse(false), 300);
			context.waitTicks(30);
			context.takeScreenshot("heartwell-fight");
			world.getServer().runCommand("kill Vaelor");
			context.waitTicks(30);
			context.takeScreenshot("heartwell-victory");
			world.getServer().waitFor(server -> !SparBot.practice().crown().fight().running(), 600);
			world.getServer().runOnServer(server -> {
				var player = human(server);
				boolean sword = false;
				for (net.minecraft.world.item.ItemStack stack : player.getInventory()) {
					sword |= stack.is(net.minecraft.world.item.Items.NETHERITE_SWORD) && stack.getHoverName().getString().equals("Oathkeeper");
				}
				if (!sword) {
					throw new AssertionError("the champion should have Vaelor's blade, Oathkeeper");
				}
				var champion = server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("sparbot", "hollow_crown/champion"));
				if (champion == null || !player.getAdvancements().getOrStartProgress(champion).isDone()) {
					throw new AssertionError("the champion should have the advancement Unbroken No More");
				}
				var vault = server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("sparbot", "hollow_crown/vault_study"));
				if (vault == null || !player.getAdvancements().getOrStartProgress(vault).isDone()) {
					throw new AssertionError("finding Tell's hidden study should be a discovery");
				}
			});
			ColosseumSurvey.View plaque = ColosseumSurvey.plaque();
			view(context, world, plaque.name(), cx + plaque.x(), plaque.y(), cz + plaque.z(), plaque.yaw(), plaque.pitch());
			view(context, world, "colosseum-crystal", cx + 0.5, 70, cz + 40.5, 180, -40);
			view(context, world, "colosseum-crystal-far", cx + 0.5, 215, cz + 110, 180, 0);
			world.getServer().runCommand("time set midnight");
			view(context, world, "colosseum-night-inside", cx + 0.5, 96, cz + 70, 180, 12);
			view(context, world, "colosseum-night-outside", cx + 0.5, 130, cz + 262, 180, 16);
			view(context, world, "colosseum-night-room", cx + 45.5, 65.6, cz + 39.9, -48.8F, 15.0F);
			view(context, world, "colosseum-night-crystal", cx + 0.5, 215, cz + 110, 180, 0);
			context.runOnClient(client -> client.options.renderDistance().set(distance));
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
			// The training hall, and a drill: full charge against the dummy, real clicks scored by the server.
			view(context, world, "training-hall", io.github.flick256.sparbot.core.practice.PracticeLayout.HALL_X + 0.5, 92,
				io.github.flick256.sparbot.core.practice.PracticeLayout.HALL_Z + 60, 180, 30);
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill charge"));
			world.getServer().waitFor(server -> SparBot.drills().inDrill(human(server).getUUID()), 200);
			// After the countdown, nine full-charge hits a little over a second apart.
			world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
			for (int i = 0; i < 9; i++) {
				context.runOnClient(client -> {
					net.minecraft.world.entity.Entity dummy = null;
					for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.player.Player p && p.getName().getString().equals("Drill1")) {
							dummy = e;
						}
					}
					if (dummy != null) {
						client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
						client.gameMode.attack(client.player, dummy);
						client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				context.waitTicks(25);
				if (i == 2) {
					context.takeScreenshot("drill-charge");
				}
			}
			// Stage one ends 30 s after it started and is scored: six or more hits, all at full charge.
			world.getServer().waitFor(server -> SparBot.drills().stagesPassed(human(server).getUUID()) >= 1, 900);
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill stop"));
			world.getServer().waitFor(server -> !SparBot.drills().inDrill(human(server).getUUID()), 200);
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
		// The near chunks for certain; the far ones as far as they get in a few seconds (software rendering is slow).
		world.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(100);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(10);
		context.takeScreenshot(name);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		world.getServer().runOnServer(server -> human(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
	}
}
