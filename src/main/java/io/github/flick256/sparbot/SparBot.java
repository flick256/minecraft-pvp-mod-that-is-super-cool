package io.github.flick256.sparbot;

import io.github.flick256.sparbot.bot.BotManager;
import io.github.flick256.sparbot.command.SparBotCommand;
import io.github.flick256.sparbot.config.SparBotConfig;
import io.github.flick256.sparbot.debug.DebugOverlay;
import io.github.flick256.sparbot.kit.KitRegistry;
import io.github.flick256.sparbot.match.MatchManager;
import io.github.flick256.sparbot.menu.MenuSync;
import io.github.flick256.sparbot.profile.ProfileRegistry;
import io.github.flick256.sparbot.record.Recorder;
import io.github.flick256.sparbot.record.ReplayManager;
import io.github.flick256.sparbot.stats.StatsTracker;
import io.github.flick256.sparbot.style.PlaystyleRegistry;
import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
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
	private static final io.github.flick256.sparbot.model.ModelRegistry MODELS = new io.github.flick256.sparbot.model.ModelRegistry();
	private static final DebugOverlay DEBUG = new DebugOverlay();
	private static final MatchManager MATCHES = new MatchManager();
	private static final Recorder RECORDER = new Recorder();
	private static final ReplayManager REPLAYS = new ReplayManager();
	private static final io.github.flick256.sparbot.practice.PracticeWorld PRACTICE = new io.github.flick256.sparbot.practice.PracticeWorld();
	private static final io.github.flick256.sparbot.kit.PlayerLayouts PLAYER_LAYOUTS = new io.github.flick256.sparbot.kit.PlayerLayouts(
		FabricLoader.getInstance().getConfigDir().resolve("sparbot").resolve("my_layouts"));

	@Override
	public void onInitialize() {
		reloadConfigAndProfiles();
		StatsTracker.register();
		MenuSync.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> SparBotCommand.register(dispatcher));
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			reloadKits(server);
			MATCHES.reload(server, configDir().resolve("sparbot"));
			PRACTICE.onServerStarted(server);
		});
		ServerTickEvents.END_SERVER_TICK.register(BOTS::tick);
		ServerTickEvents.END_SERVER_TICK.register(DEBUG::tick);
		ServerTickEvents.END_SERVER_TICK.register(MATCHES::tick);
		ServerTickEvents.END_SERVER_TICK.register(RECORDER::tick);
		ServerTickEvents.END_SERVER_TICK.register(REPLAYS::tick);
		ServerTickEvents.END_SERVER_TICK.register(PRACTICE::tick);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> MATCHES.onJoin(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MATCHES.stopAll(server);
			RECORDER.stopAll();
			REPLAYS.stopAll();
			BOTS.removeAll("server stopping");
		});
		LOGGER.info("SparBot {} initialised: {} skill profiles, enabled={}", FabricLoader.getInstance().getModContainer(MOD_ID)
			.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("?"), PROFILES.ids().size(), config.enabled);
	}

	public static void reloadConfigAndProfiles() {
		config = SparBotConfig.load(configDir().resolve("sparbot.json"));
		PROFILES.reload(configDir().resolve("sparbot").resolve("profiles"));
		STYLES.reload(configDir().resolve("sparbot").resolve("playstyles"));
		MODELS.reload(configDir().resolve("sparbot").resolve("models"));
		RECORDER.setDirectory(configDir().resolve("sparbot").resolve("recordings"));
	}

	public static void saveConfig() {
		config.save(configDir().resolve("sparbot.json"));
	}

	public static void reloadKits(MinecraftServer server) {
		KITS.reload(configDir().resolve("sparbot").resolve("kits"), configDir().resolve("sparbot").resolve("layouts"), server.registryAccess());
		LOGGER.info("SparBot kits loaded: {}; layouts: {}", KITS.ids(), KITS.layoutIds());
	}

	/** Where trained models are saved and loaded from. */
	public static Path modelsDir() {
		return configDir().resolve("sparbot").resolve("models");
	}

	private static Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	/** The kit each player last equipped (for bots to fight them in the same kit). */
	private static final java.util.Map<java.util.UUID, String> PLAYER_KITS = new java.util.concurrent.ConcurrentHashMap<>();

	public static java.util.Map<java.util.UUID, String> playerKits() {
		return PLAYER_KITS;
	}

	/** Players' own hotbar layouts per kit. */
	public static io.github.flick256.sparbot.kit.PlayerLayouts playerLayouts() {
		return PLAYER_LAYOUTS;
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

	public static io.github.flick256.sparbot.model.ModelRegistry models() {
		return MODELS;
	}

	public static Recorder recorder() {
		return RECORDER;
	}

	public static ReplayManager replays() {
		return REPLAYS;
	}

	public static io.github.flick256.sparbot.practice.PracticeWorld practice() {
		return PRACTICE;
	}

	public static MatchManager matches() {
		return MATCHES;
	}

	public static DebugOverlay debug() {
		return DEBUG;
	}

	public static BotManager bots() {
		return BOTS;
	}
}
