package io.github.flick256.sparbot.style;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.style.Playstyle;
import io.github.flick256.sparbot.core.style.Playstyles;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Bundled playstyles plus custom ones from {@code config/sparbot/playstyles/*.json}. */
public final class PlaystyleRegistry {
	private final Map<String, Playstyle> styles = new LinkedHashMap<>();

	public void reload(Path customDir) {
		styles.clear();
		styles.putAll(Playstyles.loadPresets());
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
					Playstyle style = Playstyles.parse(reader);
					styles.put(style.id(), style);
					SparBot.LOGGER.info("Loaded custom playstyle '{}' from {}", style.id(), file.getFileName());
				} catch (IllegalArgumentException | IOException e) {
					SparBot.LOGGER.error("Skipping playstyle {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not list {}: {}", customDir, e.getMessage());
		}
	}

	/** Resolves a single id or a mix like {@code aggressive_rusher:0.7,kiter:0.3}. */
	public Playstyle resolve(String spec) {
		return Playstyles.resolve(spec, styles);
	}

	public Collection<String> ids() {
		return styles.keySet();
	}

	public Map<String, Playstyle> all() {
		return styles;
	}
}
