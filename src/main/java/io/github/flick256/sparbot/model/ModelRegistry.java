package io.github.flick256.sparbot.model;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.brain.MeleeFeatures;
import io.github.flick256.sparbot.core.ml.Mlp;
import io.github.flick256.sparbot.core.ml.Models;
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

/**
 * Bundled learned models plus ones from {@code config/sparbot/models/*.json} (the file name is the id),
 * for example a model trained with {@code ./gradlew :sparbot-core:trainSword}.
 */
public final class ModelRegistry {
	private final Map<String, Mlp> models = new LinkedHashMap<>();

	public void reload(Path customDir) {
		models.clear();
		models.putAll(Models.loadBundled());
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
				String id = file.getFileName().toString().replaceFirst("\\.json$", "");
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					Mlp net = Models.parse(reader);
					if (net.inputs() != MeleeFeatures.COUNT || net.outputs() != MeleeFeatures.OUTPUTS) {
						throw new IllegalArgumentException("not a melee model (" + net + ")");
					}
					models.put(id, net);
					SparBot.LOGGER.info("Loaded model '{}' from {}: {}", id, file.getFileName(), net);
				} catch (IllegalArgumentException | IOException | com.google.gson.JsonParseException e) {
					SparBot.LOGGER.error("Skipping model {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not list {}: {}", customDir, e.getMessage());
		}
	}

	/** Adds or replaces a model (a training's newest best). */
	public void put(String id, Mlp net) {
		models.put(id, net);
	}

	public boolean bundled(String id) {
		return Models.BUNDLED_IDS.contains(id);
	}

	public Optional<Mlp> get(String id) {
		return Optional.ofNullable(models.get(id));
	}

	public Collection<String> ids() {
		return models.keySet();
	}
}
