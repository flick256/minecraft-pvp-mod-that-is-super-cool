package io.github.flick256.sparbot.bot;

import com.mojang.authlib.GameProfile;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.config.SparBotConfig;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.kit.KitApplier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** Spawns, tracks, respawns and removes bots. All methods run on the server thread. */
public final class BotManager {
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** With respawn "off", a dead bot lingers this long (death animation) before it leaves. */
	private static final int REMOVE_DEAD_AFTER_TICKS = 40;

	private final Map<String, Bot> bots = new LinkedHashMap<>();

	public Collection<Bot> all() {
		return bots.values();
	}

	public Optional<Bot> get(String name) {
		return Optional.ofNullable(bots.get(name.toLowerCase(Locale.ROOT)));
	}

	/**
	 * Spawns a bot exactly like a player joining: the vanilla login path ({@code PlayerList#placeNewPlayer}),
	 * survival mode, then the "client finished loading" packet a real client sends.
	 */
	public Bot spawn(MinecraftServer server, String name, ServerLevel level, Vec3 pos, float yaw, SkillProfile profile, Kit kit) {
		SparBotConfig config = SparBot.config();
		if (!config.enabled) {
			throw new IllegalStateException("SparBot is disabled on this server (config: enabled=false)");
		}
		if (!NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("Bot names must be 3-16 characters of A-Z, 0-9 or _");
		}
		if (bots.size() >= config.maxBots) {
			throw new IllegalStateException("Bot limit reached (" + config.maxBots + ")");
		}
		if (bots.containsKey(name.toLowerCase(Locale.ROOT)) || server.getPlayerList().getPlayer(name) != null) {
			throw new IllegalArgumentException("A player named " + name + " is already online");
		}

		UUID uuid = UUIDUtil.createOfflinePlayerUUID(name);
		GameProfile gameProfile = new GameProfile(uuid, name);
		BotConnection connection = new BotConnection();
		Bot bot = new Bot(name, uuid, connection, profile, kit, level.dimension(), pos, yaw, uuid.getMostSignificantBits() ^ System.nanoTime());
		try {
			bot.setPlaystyle(SparBot.playstyles().resolve(config.defaultPlaystyle));
		} catch (IllegalArgumentException e) {
			SparBot.LOGGER.error("config defaultPlaystyle is invalid ({}); using balanced", e.getMessage());
		}
		BotPlayer player = new BotPlayer(server, level, gameProfile, ClientInformation.createDefault(), bot);
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);

		// The tab list shows the profile's simulated ping.
		int latency = (int) Math.round(profile.pingMs().mean());
		server.getPlayerList().placeNewPlayer(connection, player, new CommonListenerCookie(gameProfile, latency, player.clientInformation(), false));
		bots.put(name.toLowerCase(Locale.ROOT), bot);
		connection.bindTo(player.getId());
		player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());

		prepareForRound(bot, player);
		SparBot.LOGGER.info("Spawned bot {} (profile {}, kit {}) at {} in {}", name, profile.id(), kit.id(), pos, level.dimension().identifier());
		return bot;
	}

	/** Puts the bot at its spawn point in survival with its kit. Used at spawn and when a new round starts. */
	public static void prepareForRound(Bot bot, BotPlayer player) {
		ServerLevel level = player.level().getServer().getLevel(bot.homeDimension());
		if (level == null) {
			level = player.level();
		}
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, bot.home().x, bot.home().y, bot.home().z, Set.of(), bot.homeYaw(), 0.0F, true);
		KitApplier.apply(player, bot.effectiveKit());
		bot.resetMind();
	}

	/** Disconnects the bot like a player leaving (its data is saved like any player's). */
	public void remove(Bot bot, String reason) {
		bots.remove(bot.name().toLowerCase(Locale.ROOT));
		BotPlayer player = bot.body();
		if (player != null && !player.hasDisconnected()) {
			player.connection.disconnect(Component.literal(reason));
		}
		SparBot.LOGGER.info("Removed bot {}: {}", bot.name(), reason);
	}

	public void removeAll(String reason) {
		new ArrayList<>(bots.values()).forEach(bot -> remove(bot, reason));
	}

	/** Respawns a dead bot through the vanilla respawn packet, then marks it loaded like a client would. */
	public boolean respawn(MinecraftServer server, Bot bot) {
		BotPlayer dead = bot.body();
		if (dead == null || !dead.isDeadOrDying()) {
			return false;
		}
		if (server.isHardcore()) {
			// Vanilla puts hardcore respawns into spectator mode; a spectator bot is not allowed.
			remove(bot, "died in hardcore");
			return false;
		}
		dead.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
		BotPlayer alive = bot.body();
		if (alive == null || alive == dead) {
			SparBot.LOGGER.error("Respawn of bot {} did not produce a new body", bot.name());
			return false;
		}
		bot.connection().bindTo(alive.getId());
		alive.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
		bot.clearDeadTicks();
		if (SparBot.config().kitOnRespawn) {
			prepareForRound(bot, alive);
		} else {
			bot.resetMind();
		}
		SparBot.LOGGER.info("Respawned bot {}", bot.name());
		return true;
	}

	/** End of every server tick: enforce the mortality guard, handle deaths and vanished bots. */
	public void tick(MinecraftServer server) {
		SparBotConfig config = SparBot.config();
		if (!config.enabled && !bots.isEmpty()) {
			removeAll("SparBot disabled");
			return;
		}
		for (Bot bot : new ArrayList<>(bots.values())) {
			BotPlayer player = bot.body();
			ServerPlayer listed = server.getPlayerList().getPlayer(bot.uuid());
			if (player == null || listed != player) {
				// Kicked, banned, or removed by something else.
				bots.remove(bot.name().toLowerCase(Locale.ROOT));
				SparBot.LOGGER.info("Bot {} left the server", bot.name());
				continue;
			}
			List<String> violations = bot.takeViolations();
			if (violations != null) {
				SparBot.LOGGER.error("Bot {} broke the mortality rules ({}); removing it", bot.name(), String.join(", ", violations));
				remove(bot, "mortality rule violated: " + String.join(", ", violations));
				continue;
			}
			if (player.isDeadOrDying()) {
				int dead = bot.incrementDeadTicks();
				switch (config.respawnModeEnum()) {
					case AUTO -> {
						if (dead >= config.autoRespawnDelayTicks) {
							respawn(server, bot);
						}
					}
					case OFF -> {
						if (dead >= REMOVE_DEAD_AFTER_TICKS) {
							remove(bot, "died (respawn is off)");
						}
					}
					case MANUAL -> {
					}
				}
			}
		}
	}
}
