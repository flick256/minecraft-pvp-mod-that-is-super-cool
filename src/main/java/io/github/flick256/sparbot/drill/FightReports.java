package io.github.flick256.sparbot.drill;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.drill.FightReport;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Each player's last ten fight reports against bots, kept in {@code config/sparbot/fight-reports.json}. */
public final class FightReports {
	public static final int KEEP = 10;
	private static final Gson GSON = new Gson();
	private static final Type TYPE = new TypeToken<Map<String, List<FightReport>>>() { }.getType();
	private final Path file;
	private Map<String, List<FightReport>> reports = new HashMap<>();

	public FightReports(Path file) {
		this.file = file;
		if (Files.exists(file)) {
			try {
				Map<String, List<FightReport>> read = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), TYPE);
				if (read != null) {
					reports = new HashMap<>(read);
				}
			} catch (IOException | RuntimeException e) {
				SparBot.LOGGER.error("Could not read {}: {}; starting without fight reports", file, e.getMessage());
			}
		}
	}

	/** The player's reports, newest last. */
	public List<FightReport> of(UUID player) {
		return List.copyOf(reports.getOrDefault(player.toString(), List.of()));
	}

	public void add(UUID player, FightReport report) {
		List<FightReport> mine = new ArrayList<>(reports.getOrDefault(player.toString(), List.of()));
		mine.add(report);
		while (mine.size() > KEEP) {
			mine.remove(0);
		}
		reports.put(player.toString(), mine);
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(reports), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not write {}: {}", file, e.getMessage());
		}
	}
}
