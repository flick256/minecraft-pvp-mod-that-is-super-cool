package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.match.GameModes;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** Bundled game modes plus custom ones from {@code config/sparbot/modes/*.json}. */
public final class GameModeRegistry {
	private final Map<String, GameMode> modes = new LinkedHashMap<>();

	public void reload(Path customDir) {
		modes.clear();
		modes.putAll(GameModes.loadBundled());
		if (!Files.isDirectory(customDir)) {
			try {
				Files.createDirectories(customDir);
			} catch (IOException e) {
				SparBot.LOGGER.warn("Could not create {}: {}", customDir, e.getMessage());
			}
			return;
		}
		try (Stream<Path> files = Files.list(customDir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					GameMode mode = GameModes.parse(reader);
					modes.put(mode.id(), mode);
				} catch (IllegalArgumentException | IOException e) {
					SparBot.LOGGER.error("Skipping game mode {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not list {}: {}", customDir, e.getMessage());
		}
	}

	public Optional<GameMode> get(String id) {
		return Optional.ofNullable(modes.get(id));
	}

	public Collection<String> ids() {
		return modes.keySet();
	}
}
