package io.github.flick256.sparbot.gametest;

import com.mojang.authlib.GameProfile;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Shared helpers for SparBot GameTests. */
final class TestSupport {
	private static final AtomicInteger NAMES = new AtomicInteger();

	private TestSupport() {
	}

	/** Tests drive targets explicitly so concurrently running tests never pick each other's players. */
	static void isolate() {
		SparBot.config().autoTarget = false;
		SparBot.config().respawnMode = "manual";
		SparBot.config().kitOnRespawn = true;
		SparBot.config().maxBots = 64;
	}

	static Kit kit(String id) {
		return Kits.loadBundled().get(id);
	}

	static String uniqueName(String prefix) {
		return prefix + NAMES.incrementAndGet();
	}

	/** Stone floor over the whole 8x8 test area, with a 3-high glass ring so fighters stay inside. */
	static void arena(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				if (x == 0 || z == 0 || x == 7 || z == 7) {
					for (int y = 1; y <= 3; y++) {
						helper.setBlock(x, y, z, Blocks.GLASS);
					}
				}
			}
		}
	}

	static Bot spawnBot(GameTestHelper helper, String prefix, double x, double y, double z, float yaw, String profileId, String kitId) {
		return spawnBotNamed(helper, uniqueName(prefix), x, y, z, yaw, profileId, kitId);
	}

	static Bot spawnBotNamed(GameTestHelper helper, String name, double x, double y, double z, float yaw, String profileId, String kitId) {
		isolate();
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		ServerLevel level = helper.getLevel();
		return SparBot.bots().spawn(level.getServer(), name, level, pos, yaw, SparBot.profiles().get(profileId).orElseThrow(), kit(kitId));
	}

	static BotPlayer body(Bot bot) {
		BotPlayer body = bot.body();
		if (body == null) {
			throw new IllegalStateException("bot has no body");
		}
		return body;
	}

	/**
	 * A plain vanilla ServerPlayer joined through the normal login path, standing in for a human. Used
	 * as the reference for "the bot is treated exactly like a real player".
	 */
	static ServerPlayer spawnRealPlayer(GameTestHelper helper, double x, double y, double z) {
		ServerLevel level = helper.getLevel();
		String name = uniqueName("Human");
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		player.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), 0, 0, true);
		return player;
	}

	static void removeRealPlayer(ServerPlayer player) {
		player.level().getServer().getPlayerList().remove(player);
	}

	static void remove(Bot... bots) {
		for (Bot bot : bots) {
			SparBot.bots().remove(bot, "test finished");
		}
	}

	/** The whole 8x8x8 test volume in world coordinates. */
	static net.minecraft.world.phys.AABB area(GameTestHelper helper) {
		return new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)),
			Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(8, 8, 8))));
	}
}
