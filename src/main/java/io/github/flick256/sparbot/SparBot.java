package io.github.flick256.sparbot;

import io.github.flick256.sparbot.bot.BotManager;
import io.github.flick256.sparbot.command.SparBotCommand;
import io.github.flick256.sparbot.config.SparBotConfig;
import io.github.flick256.sparbot.debug.DebugOverlay;
import io.github.flick256.sparbot.style.PlaystyleRegistry;
import io.github.flick256.sparbot.kit.KitRegistry;
import io.github.flick256.sparbot.profile.ProfileRegistry;
import io.github.flick256.sparbot.stats.StatsTracker;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SparBot: server-side PvP sparring bots that behave like real, mortal players. Works in singleplayer
 * (the integrated server) and on dedicated servers; no client install is required.
 */
public final class SparBot implements ModInitializer {
	public static final String MOD_ID = "sparbot";
	public static final Logger LOGGER = LoggerFactory.getLogger("SparBot");

	private static SparBotConfig config = new SparBotConfig();
	private static final ProfileRegistry PROFILES = new ProfileRegistry();
	private static final KitRegistry KITS = new KitRegistry();
	private static final BotManager BOTS = new BotManager();
	private static final PlaystyleRegistry STYLES = new PlaystyleRegistry();
	private static final DebugOverlay DEBUG = new DebugOverlay();

	@Override
	public void onInitialize() {
		reloadConfigAndProfiles();
		StatsTracker.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SparBotCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(SparBot::reloadKits);
		ServerTickEvents.END_SERVER_TICK.register(BOTS::tick);
		ServerTickEvents.END_SERVER_TICK.register(DEBUG::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> BOTS.removeAll("server stopping"));
		LOGGER.info("SparBot initialised: {} skill profiles, enabled={}", PROFILES.ids().size(), config.enabled);
	}

	public static void reloadConfigAndProfiles() {
		config = SparBotConfig.load(configDir().resolve("sparbot.json"));
		PROFILES.reload(configDir().resolve("sparbot").resolve("profiles"));
		STYLES.reload(configDir().resolve("sparbot").resolve("playstyles"));
	}

	public static void reloadKits(MinecraftServer server) {
		KITS.reload(configDir().resolve("sparbot").resolve("kits"), configDir().resolve("sparbot").resolve("layouts"), server.registryAccess());
		LOGGER.info("SparBot kits loaded: {}; layouts: {}", KITS.ids(), KITS.layoutIds());
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	public static SparBotConfig config() {
		return config;
	}

	public static ProfileRegistry profiles() {
		return PROFILES;
	}

	public static KitRegistry kits() {
		return KITS;
	}

	public static PlaystyleRegistry playstyles() {
		return STYLES;
	}

	public static DebugOverlay debug() {
		return DEBUG;
	}

	public static BotManager bots() {
		return BOTS;
	}
}
