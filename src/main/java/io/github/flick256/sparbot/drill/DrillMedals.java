package io.github.flick256.sparbot.drill;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.flick256.sparbot.SparBot;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Each player's best medal per drill (stages passed, 0-3), kept in {@code config/sparbot/drill-medals.json}. */
public final class DrillMedals {
	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<Map<String, Map<String, Integer>>>() { }.getType();
	private final Path file;
	private Map<String, Map<String, Integer>> medals = new HashMap<>();

	public DrillMedals(Path file) {
		this.file = file;
		if (Files.exists(file)) {
			try {
				Map<String, Map<String, Integer>> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
				if (read != null) {
					medals = new HashMap<>(read);
				}
			} catch (IOException | RuntimeException e) {
				SparBot.LOGGER.error("Could not read {}: {}; starting without medals", file, e.getMessage());
			}
		}
	}

	/** The player's medals: drill id to stages passed. */
	public Map<String, Integer> of(UUID player) {
		return Map.copyOf(medals.getOrDefault(player.toString(), Map.of()));
	}

	/** Records a result; returns whether it beat the player's best. */
	public boolean record(UUID player, String drill, int stagesPassed) {
		Map<String, Integer> mine = medals.computeIfAbsent(player.toString(), k -> new HashMap<>());
		if (stagesPassed <= mine.getOrDefault(drill, 0)) {
			return false;
		}
		mine.put(drill, stagesPassed);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(medals), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not write {}: {}", file, e.getMessage());
		}
		return true;
	}
}
