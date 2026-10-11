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
			if (Boolean.getBoolean("sparbot.drillsOnly")) {
				drills(context, world);
				return;
			}

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
			// Switch strafing off.
			context.clickScreenButton("strafe");
			world.getServer().waitFor(server -> SparBot.bots().get("Bot1").map(b -> b.disabledTechniques().contains(
				io.github.flick256.sparbot.core.brain.Technique.STRAFE)).orElse(false));
			context.waitTicks(10);

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
			if (Boolean.getBoolean("sparbot.deepOnly")) {
				context.runOnClient(client -> client.options.renderDistance().set(10));
				for (ColosseumSurvey.View v : io.github.flick256.sparbot.practice.colosseum.DeepSurvey.views()) {
					view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
				}
				deep(context, world, cx, cz);
				return;
			}
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
			view(context, world, "colosseum-concourse", cx + -42.0, 108.6, cz + 72.7, -150.0F, 20.0F);
			// The twenty-four great halls, and the way down to the Brink.
			for (ColosseumSurvey.View v : ColosseumSurvey.halls()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			for (ColosseumSurvey.View v : ColosseumSurvey.descent()) {
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
			// The Deep: the Brink, the cavern, Vaelor on his throne; the Leap, the fight in three phases, the way back up.
			for (ColosseumSurvey.View v : io.github.flick256.sparbot.practice.colosseum.DeepSurvey.views()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			deep(context, world, cx, cz);
			ColosseumSurvey.View plaque = ColosseumSurvey.plaque();
			view(context, world, plaque.name(), cx + plaque.x(), plaque.y(), cz + plaque.z(), plaque.yaw(), plaque.pitch());
			view(context, world, "colosseum-crystal", cx + 0.5, 70, cz + 40.5, 180, -40);
			view(context, world, "colosseum-crystal-far", cx + 0.5, 215, cz + 110, 180, 0);
			world.getServer().runCommand("time set midnight");
			view(context, world, "colosseum-night-inside", cx + 0.5, 96, cz + 70, 180, 12);
			view(context, world, "colosseum-night-outside", cx + 0.5, 130, cz + 262, 180, 16);
			view(context, world, "colosseum-night-hall", cx + 45.5, 67.2, cz + 39.9, -48.8F, 8.0F);
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
			drills(context, world);
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

	/**
	 * The drills, played by the real client: full charge against the dummy, jump resets against the attacker
	 * (jumping the moment the client sees its hit, as a player would), punishing the whiffer's missed swing,
	 * and the guard that stops a drill killing you.
	 */
	/** While set, the client presses jump on exactly the client ticks a hit's knockback arrives (see drills()). */
	private static volatile boolean JUMP_ON_HIT;
	private static net.minecraft.world.phys.Vec3 jumpLastVelocity;
	private static boolean jumpHookRegistered;

	private static void drills(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> {
			if (!jumpHookRegistered) {
				jumpHookRegistered = true;
				// The start of a client tick comes after the packets that arrived since the last: a hurt animation seen
				// here came with its knockback, so jump is held on this very tick (and let go on the next).
				// The knockback is a new velocity set by a packet between ticks: at the start of the tick it differs from
				// what the last tick left (the player stood or walked, so only knockback pushes it away from the bot).
				net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(c -> {
					if (c.player == null) {
						return;
					}
					net.minecraft.world.phys.Vec3 v = c.player.getDeltaMovement();
					boolean knocked = jumpLastVelocity != null && v.y > 0.3 && jumpLastVelocity.y <= 0 && c.player.onGround();
					if (JUMP_ON_HIT) {
						c.options.keyJump.setDown(knocked);
					}
				});
				net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
					if (c.player != null) {
						jumpLastVelocity = c.player.getDeltaMovement();
					}
				});
			}
		});
		// Full charge: nine full-charge hits a little over a second apart pass the first stage (eight needed).
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill charge"));
		world.getServer().waitFor(server -> SparBot.drills().inDrill(human(server).getUUID()), 600);
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		for (int i = 0; i < 9; i++) {
			context.runOnClient(client -> {
				net.minecraft.world.entity.Entity dummy = drillBot(client);
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
		world.getServer().waitFor(server -> SparBot.drills().stagesPassed(human(server).getUUID()) >= 1, 300);
		stopDrill(context, world);

		// Jump reset: the attacker swings on a rhythm. Taking its hits without jumping counts for nothing (the
		// knockback lifting you is not a jump); then the client sprints into it and jumps on the very client tick
		// each hit's knockback arrives (the start of the tick its hurt animation came in), as a perfect player would.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill jumpreset"));
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		int[] taken = {0};
		int[] lastHurt = {0};
		for (int i = 0; i < 400 && taken[0] < 2; i++) {
			context.runOnClient(client -> {
				int hurt = client.player.hurtTime;
				if (hurt > lastHurt[0]) {
					taken[0]++;
				}
				lastHurt[0] = hurt;
			});
			context.waitTick();
		}
		context.waitTicks(30);
		int unearned = world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()));
		if (taken[0] < 2 || unearned != 0) {
			throw new AssertionError("taking " + taken[0] + " hits without jumping counted " + unearned + " jump resets");
		}
		JUMP_ON_HIT = true;
		context.runOnClient(client -> client.options.keyUp.setDown(true));
		for (int i = 0; i < 900 && world.getServer().computeOnServer(server -> SparBot.drills().stagesPassed(human(server).getUUID()) < 1); i++) {
			context.runOnClient(client -> client.player.setSprinting(true));
			context.waitTick();
			if (i == 120) {
				context.takeScreenshot("drill-jumpreset");
			}
		}
		JUMP_ON_HIT = false;
		context.runOnClient(client -> {
			client.options.keyUp.setDown(false);
			client.options.keyJump.setDown(false);
		});
		int passed = world.getServer().computeOnServer(server -> SparBot.drills().stagesPassed(human(server).getUUID()));
		if (passed < 1) {
			throw new AssertionError("jumping as each hit lands didn't pass the jump reset drill's first stage (good reps: "
				+ world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID())) + "; verdicts: "
				+ world.getServer().computeOnServer(server -> SparBot.drills().recentVerdicts(human(server).getUUID())) + ")");
		}
		// Nobody dies in a drill.
		boolean alive = world.getServer().computeOnServer(server -> {
			net.minecraft.server.level.ServerPlayer p = human(server);
			p.setHealth(1);
			p.invulnerableTime = 0;
			p.hurtServer((net.minecraft.server.level.ServerLevel) p.level(), p.damageSources().generic(), 100);
			return p.isAlive() && p.getHealth() >= p.getMaxHealth() - 0.01F;
		});
		if (!alive) {
			throw new AssertionError("a lethal hit in a drill killed the player");
		}
		stopDrill(context, world);

		// Whiff punish: step in and hit the moment the whiffer's swing misses.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill punish"));
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		for (int i = 0; i < 900 && world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()) < 2); i++) {
			boolean open = world.getServer().computeOnServer(server -> SparBot.drills().punishWindowOpen(human(server).getUUID()));
			if (open) {
				world.getServer().runOnServer(server -> {
					net.minecraft.server.level.ServerPlayer p = human(server);
					SparBot.bots().get("Drill" + (SparBot.drills().bayOf(p.getUUID()) + 1)).map(io.github.flick256.sparbot.bot.Bot::body).ifPresent(b -> {
						Vec3Holder.step(p, b);
					});
				});
				context.waitTicks(2);
				context.runOnClient(client -> {
					net.minecraft.world.entity.Entity bot = drillBot(client);
					if (bot != null) {
						client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, bot.getEyePosition());
						client.gameMode.attack(client.player, bot);
						client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				context.waitTicks(30);
			}
			context.waitTick();
		}
		int punished = world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()));
		if (punished < 2) {
			throw new AssertionError("hitting the whiffer right after its missed swing didn't count as a punish (good reps: " + punished + ")");
		}
		context.takeScreenshot("drill-punish");
		stopDrill(context, world);

		// A sword duel against an intermediate bot in the practice kit, with the live coach on: the client swings
		// at half charge now and then, which the coach points out.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight sword_duel intermediate"));
		world.getServer().waitFor(server -> SparBot.matches().inMatch(human(server).getUUID()), 600);
		for (int i = 0; i < 240; i++) {
			if (i % 7 == 0) {
				context.runOnClient(client -> {
					for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.player.Player p && p != client.player && p.distanceTo(client.player) < 4) {
							client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
							client.gameMode.attack(client.player, p);
							client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						}
					}
				});
			}
			context.waitTick();
			if (i == 160) {
				context.takeScreenshot("fight-coach");
			}
		}
		boolean kit = world.getServer().computeOnServer(server -> human(server).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
			.getEnchantments().toString().contains("protection"));
		if (!kit) {
			throw new AssertionError("the sword duel didn't give the practice kit (Protection IV armor)");
		}
		world.getServer().runOnServer(server -> SparBot.matches().stopAll(server));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 600);
	}

	/** Moves a player to just inside reach of the bot, facing it (a player stepping in to punish). */
	private static final class Vec3Holder {
		static void step(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.entity.Entity bot) {
			net.minecraft.world.phys.Vec3 away = p.position().subtract(bot.position()).multiply(1, 0, 1).normalize();
			net.minecraft.world.phys.Vec3 to = bot.position().add(away.scale(2.3));
			float yaw = (float) Math.toDegrees(Math.atan2(-(bot.getX() - to.x), bot.getZ() - to.z));
			p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), to.x, to.y, to.z, java.util.Set.of(), yaw, 0, true);
		}
	}

	private static void stopDrill(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill stop"));
		world.getServer().waitFor(server -> !SparBot.drills().inDrill(human(server).getUUID()), 200);
	}

	private static net.minecraft.world.entity.Entity drillBot(net.minecraft.client.Minecraft client) {
		for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.player.Player p && p.getName().getString().startsWith("Drill")) {
				return e;
			}
		}
		return null;
	}

	private static void at(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch, net.minecraft.world.level.GameType mode) {
		world.getServer().runOnServer(server -> {
			var player = human(server);
			player.setGameMode(mode);
			player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
		});
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		world.getConnection().waitForChunksRender(false, 600);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(4);
		context.takeScreenshot(name);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
	}

	/** A screenshot of Vaelor: the camera nine blocks from him, toward the middle of the arena, looking at him. */
	private static void shotBoss(ClientGameTestContext context, TestSingleplayerContext world, String name, int cx, int cz) {
		world.getServer().runOnServer(server -> {
			var boss = encounter().boss();
			var p = human(server);
			if (boss == null) {
				return;
			}
			double bx = boss.getX();
			double bz = boss.getZ();
			double ox = cx + 0.5 - bx;
			double oz = cz + 0.5 - bz;
			double len = Math.max(0.1, Math.hypot(ox, oz));
			double px = bx + ox / len * 9;
			double pz = bz + oz / len * 9;
			float yaw = (float) Math.toDegrees(Math.atan2(-(bx - px), bz - pz));
			p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), px, boss.getY() + 2.5, pz, java.util.Set.of(), yaw, 8, true);
		});
		context.waitTicks(3);
		shot(context, world, name);
	}

	private static io.github.flick256.sparbot.practice.colosseum.DeepEncounter encounter() {
		return SparBot.practice().crown().deep();
	}

	/** Hurts Vaelor as the player would (a player's blow), by {@code amount}. */
	private static void strike(TestSingleplayerContext world, float amount) {
		world.getServer().runOnServer(server -> {
			var p = human(server);
			var boss = encounter().boss();
			if (boss != null) {
				boss.hurtServer((net.minecraft.server.level.ServerLevel) p.level(), p.damageSources().playerAttack(p), amount);
			}
		});
	}

	/**
	 * Down the Well into the arena, Vaelor rising, his three phases (struck down to two thirds, then a third, then out),
	 * his kneeling, the Gate of Triumph, every reward taken in the Hall of Triumph, the climb, the Laurel Door, the Heart
	 * breaking and the rise into the colosseum. The champion must end up with all seven of his things.
	 */
	private static void deep(ClientGameTestContext context, TestSingleplayerContext world, int cx, int cz) {
		double[] pf = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.platform();
		at(world, cx + pf[0], pf[1], cz + pf[2], 0, 30, net.minecraft.world.level.GameType.SURVIVAL);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().waiting(), 400);
		double[] well = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.inTheWell();
		at(world, cx + well[0], well[1], cz + well[2], 0, 70, net.minecraft.world.level.GameType.SURVIVAL);
		world.getServer().waitFor(server -> encounter().fighting(), 200);
		context.waitTicks(70);
		shot(context, world, "deep-leap");
		world.getServer().waitFor(server -> encounter().fighting() && encounter().boss() != null && !encounter().boss().waiting(), 900);
		world.getServer().runOnServer(server -> human(server).setGameMode(net.minecraft.world.level.GameType.CREATIVE));
		double[] bt = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.beforeThrone();
		at(world, cx + bt[0], bt[1], cz + bt[2], (float) bt[3], -8, net.minecraft.world.level.GameType.CREATIVE);
		context.waitTicks(16);
		shotBoss(context, world, "deep-vaelor-rises", cx, cz);
		context.waitTicks(80);
		shotBoss(context, world, "deep-fight", cx, cz);
		strike(world, 260);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().action() == io.github.flick256.sparbot.content.VaelorBoss.Action.ROAR,
			400);
		context.waitTicks(25);
		shotBoss(context, world, "deep-phase-two", cx, cz);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().phase() == 2
			&& encounter().boss().action() != io.github.flick256.sparbot.content.VaelorBoss.Action.ROAR, 400);
		context.waitTicks(70);
		shotBoss(context, world, "deep-phase-two-fight", cx, cz);
		strike(world, 260);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().phase() == 3, 600);
		shotBoss(context, world, "deep-phase-three", cx, cz);
		context.waitTicks(90);
		shotBoss(context, world, "deep-phase-three-fight", cx, cz);
		strike(world, 3000);
		context.waitTicks(40);
		shotBoss(context, world, "deep-vaelor-kneels", cx, cz);
		world.getServer().waitFor(server -> encounter().victorious(), 400);
		context.waitTicks(70);
		double[] av = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.avenue();
		at(world, cx + av[0], av[1], cz + av[2], (float) av[3], 0, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(40);
		shot(context, world, "deep-gate-of-triumph");
		double[] hall = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.hall();
		at(world, cx + hall[0], hall[1], cz + hall[2], (float) hall[3], 4, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(40);
		shot(context, world, "deep-hall-of-triumph");
		for (int i = 0; i < 7; i++) {
			double[] by = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.byPedestal(i);
			at(world, cx + by[0], by[1], cz + by[2], 0, 0, net.minecraft.world.level.GameType.SURVIVAL);
			context.waitTicks(10);
		}
		world.getServer().runOnServer(server -> {
			var p = human(server);
			for (net.minecraft.world.item.Item item : io.github.flick256.sparbot.content.SparBotContent.REWARDS) {
				if (!p.getInventory().contains(new net.minecraft.world.item.ItemStack(item))) {
					throw new AssertionError("the champion didn't get " + item + " from its pedestal");
				}
			}
		});
		double[] door = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.byLaurelDoor();
		at(world, cx + door[0], door[1], cz + door[2], 0, 0, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(30);
		at(world, cx + pf[0], pf[1], cz + pf[2], 0, -70, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(50);
		shot(context, world, "deep-ascension");
		world.getServer().waitFor(server -> human(server).getY() > io.github.flick256.sparbot.practice.colosseum.DeepSurvey.fieldTop() + 3, 600);
		world.getServer().runOnServer(server -> {
			var p = human(server);
			p.connection.teleport(p.getX(), p.getY(), p.getZ(), 180, -15);
		});
		context.waitTicks(30);
		shot(context, world, "deep-champion");
		world.getServer().waitFor(server -> !encounter().running(), 800);
		world.getServer().runOnServer(server -> {
			var player = human(server);
			var champion = server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("sparbot", "hollow_crown/champion"));
			if (champion == null || !player.getAdvancements().getOrStartProgress(champion).isDone()) {
				throw new AssertionError("the champion should have the advancement Unbroken No More");
			}
			var top = player.level().getBlockState(new net.minecraft.core.BlockPos(cx, io.github.flick256.sparbot.practice.colosseum.DeepSurvey.fieldTop(), cz));
			if (top.isAir()) {
				throw new AssertionError("the hatch in the field was left open");
			}
		});
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
