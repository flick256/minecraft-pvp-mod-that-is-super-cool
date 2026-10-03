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
	/** The in-memory channel of each test "human", so tests can read what the server sent them. */
	private static final java.util.Map<String, EmbeddedChannel> CHANNELS = new java.util.concurrent.ConcurrentHashMap<>();

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

	/**
	 * Gives the bot a copy of its profile whose item skills are all 1: a player who always reaches for
	 * the right tool. Mechanic tests use it so they test that the mechanic works, not a dice roll (the
	 * probabilities themselves are covered by core unit tests).
	 */
	static Bot certain(Bot bot) {
		io.github.flick256.sparbot.core.profile.SkillProfile p = bot.profile();
		io.github.flick256.sparbot.core.profile.SkillProfile.ItemSkills i = p.items();
		bot.setProfile(new io.github.flick256.sparbot.core.profile.SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(),
			p.pingMs(), p.aim(), p.clicking(), p.reach(), p.technique(),
			new io.github.flick256.sparbot.core.profile.SkillProfile.ItemSkills(i.hotbarSwitchMs(), i.inventoryMs(), 1, i.gappleHealthFraction(),
				i.eatHungerBelow(), 1, 1, 1, 1, 1),
			p.mistakeRate(), p.panicHealthFraction()));
		return bot;
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
		CHANNELS.put(name, new EmbeddedChannel(connection));
		level.getServer().getPlayerList().placeNewPlayer(connection, player, CommonListenerCookie.createInitial(profile, false));
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		player.teleportTo(level, pos.x, pos.y, pos.z, java.util.Set.of(), 0, 0, true);
		return player;
	}

	/** Every packet the server has sent this test player so far (drains the channel). */
	static java.util.List<Object> sentTo(ServerPlayer player) {
		EmbeddedChannel channel = CHANNELS.get(player.getPlainTextName());
		java.util.List<Object> packets = new java.util.ArrayList<>();
		Object msg;
		while ((msg = channel.readOutbound()) != null) {
			packets.add(msg);
		}
		return packets;
	}

	static void removeRealPlayer(ServerPlayer player) {
		CHANNELS.remove(player.getPlainTextName());
		player.level().getServer().getPlayerList().remove(player);
	}

	static void remove(Bot... bots) {
		for (Bot bot : bots) {
			SparBot.bots().remove(bot, "test finished");
		}
	}

	/**
	 * A stone strip {@code width} x {@code length} (relative x, z from 0), for long-range tests. 26.2
	 * encloses every test area in barrier blocks, so the strip also opens a corridor through the
	 * barrier walls it crosses (use with a large {@code padding} so no neighbouring test is in the way).
	 */
	static void platform(GameTestHelper helper, int width, int length) {
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < length; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y <= 12; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (helper.getLevel().getBlockState(helper.absolutePos(pos)).is(Blocks.BARRIER)) {
						helper.setBlock(pos, Blocks.AIR);
					}
				}
			}
		}
	}

	/** Spawns a bot whose brain is switched off: a target dummy with full vanilla physics and damage. */
	static Bot dummy(GameTestHelper helper, String prefix, double x, double y, double z, float yaw, String kitId) {
		Bot bot = spawnBot(helper, prefix, x, y, z, yaw, "intermediate", kitId);
		io.github.flick256.sparbot.bot.BotTestAccess.pauseBrain(bot, true);
		return bot;
	}

	/** The whole 8x8x8 test volume in world coordinates. */
	static net.minecraft.world.phys.AABB area(GameTestHelper helper) {
		return new net.minecraft.world.phys.AABB(Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)),
			Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(8, 8, 8))));
	}
}
