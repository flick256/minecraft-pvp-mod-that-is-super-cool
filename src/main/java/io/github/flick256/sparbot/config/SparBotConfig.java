package io.github.flick256.sparbot.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.flick256.sparbot.SparBot;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-owner settings, stored in {@code config/sparbot.json}. Created with defaults on first run.
 * Note there is intentionally no option to make bots invulnerable or to modify the damage they take.
 */
public final class SparBotConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Master switch. When false, bots cannot be spawned and existing ones are removed. */
	public boolean enabled = true;
	/** Who may use /sparbot: "all", "moderators", "gamemasters", "admins" or "owners". */
	public String commandPermission = "gamemasters";
	public int maxBots = 8;
	/** Bots only perceive opponents within this many blocks (and in line of sight). */
	public double awarenessRadius = 32.0;
	/** Without an assigned target, fight the nearest player in awareness. */
	public boolean autoTarget = true;
	/** Whether auto-targeting may pick other bots (assigned targets always may). */
	public boolean autoTargetBots = false;
	/** Server-wide click rate cap applied on top of each profile; never above 20. */
	public double maxCps = 20.0;
	/** "off" (bot is removed after dying), "manual" (/sparbot respawn) or "auto". */
	public String respawnMode = "auto";
	public int autoRespawnDelayTicks = 60;
	/** Re-equip the bot's kit when it respawns (a kit reset, like the start of a new round). */
	public boolean kitOnRespawn = true;
	public String defaultProfile = "intermediate";
	public String defaultKit = "basic_sword";
	/** Playstyle (or mix, e.g. "aggressive_rusher:0.7,kiter:0.3") new bots start with. */
	public String defaultPlaystyle = "balanced";
	/** Log every tactic change at INFO level (noisy, for debugging). */
	public boolean logDecisions = false;
	/** Record every match to config/sparbot/recordings (for replays and Super Mode training). */
	public boolean recordMatches = false;

	public enum RespawnMode {
		OFF,
		MANUAL,
		AUTO
	}

	public RespawnMode respawnModeEnum() {
		return RespawnMode.valueOf(respawnMode.toUpperCase(Locale.ROOT));
	}

	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (!List.of("all", "moderators", "gamemasters", "admins", "owners").contains(commandPermission)) {
			errors.add("commandPermission must be all/moderators/gamemasters/admins/owners");
		}
		if (maxBots < 0 || maxBots > 256) {
			errors.add("maxBots must be 0-256");
		}
		if (awarenessRadius < 1 || awarenessRadius > 128) {
			errors.add("awarenessRadius must be 1-128");
		}
		if (maxCps < 1 || maxCps > 20) {
			errors.add("maxCps must be 1-20");
		}
		try {
			respawnModeEnum();
		} catch (IllegalArgumentException e) {
			errors.add("respawnMode must be off/manual/auto");
		}
		if (autoRespawnDelayTicks < 1) {
			errors.add("autoRespawnDelayTicks must be >= 1");
		}
		return errors;
	}

	/** Loads the config, writing defaults if missing. Invalid files fall back to defaults with an error log. */
	public static SparBotConfig load(Path file) {
		SparBotConfig config = new SparBotConfig();
		if (Files.exists(file)) {
			try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				SparBotConfig read = GSON.fromJson(reader, SparBotConfig.class);
				if (read != null) {
					List<String> errors = read.validate();
					if (errors.isEmpty()) {
						config = read;
					} else {
						SparBot.LOGGER.error("Invalid {}: {}. Using defaults.", file, errors);
					}
				}
			} catch (IOException | JsonParseException e) {
				SparBot.LOGGER.error("Could not read {}: {}. Using defaults.", file, e.getMessage());
			}
		}
		try {
			Files.createDirectories(file.getParent());
			try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			SparBot.LOGGER.warn("Could not write {}: {}", file, e.getMessage());
		}
		return config;
	}
}
