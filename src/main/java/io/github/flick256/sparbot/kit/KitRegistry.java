package io.github.flick256.sparbot.kit;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import io.github.flick256.sparbot.core.kit.Layout;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;

/**
 * Bundled kits plus custom ones from {@code config/sparbot/kits/*.json}. Every kit is checked twice:
 * structurally by the core validator, and against the live 26.2 registries by {@link KitApplier}.
 */
public final class KitRegistry {
	private final Map<String, Kit> kits = new LinkedHashMap<>();
	private final Map<String, Layout> layouts = new LinkedHashMap<>();
	private Path customDir;
	private Path layoutDir;

	public void reload(Path customDir, Path layoutDir, HolderLookup.Provider registries) {
		this.customDir = customDir;
		this.layoutDir = layoutDir;
		kits.clear();
		layouts.clear();
		Kits.loadBundled().values().forEach(kit -> register(kit, "bundled", registries));
		if (Files.isDirectory(customDir)) {
			try (Stream<Path> files = Files.list(customDir)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						register(Kits.parse(reader), file.getFileName().toString(), registries);
					} catch (IllegalArgumentException | IOException e) {
						SparBot.LOGGER.error("Skipping kit {}: {}", file.getFileName(), e.getMessage());
					}
				}
			} catch (IOException e) {
				SparBot.LOGGER.error("Could not list {}: {}", customDir, e.getMessage());
			}
		} else {
			try {
				Files.createDirectories(customDir);
			} catch (IOException e) {
				SparBot.LOGGER.warn("Could not create {}: {}", customDir, e.getMessage());
			}
		}
		loadLayouts();
	}

	private void loadLayouts() {
		if (!Files.isDirectory(layoutDir)) {
			try {
				Files.createDirectories(layoutDir);
			} catch (IOException e) {
				SparBot.LOGGER.warn("Could not create {}: {}", layoutDir, e.getMessage());
			}
			return;
		}
		try (Stream<Path> files = Files.list(layoutDir)) {
			for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					Layout layout = Kits.parseLayout(reader);
					if (!kits.containsKey(layout.kit())) {
						SparBot.LOGGER.error("Skipping layout {}: kit '{}' is not loaded", file.getFileName(), layout.kit());
						continue;
					}
					layouts.put(layout.id(), layout);
				} catch (IllegalArgumentException | IOException e) {
					SparBot.LOGGER.error("Skipping layout {}: {}", file.getFileName(), e.getMessage());
				}
			}
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not list {}: {}", layoutDir, e.getMessage());
		}
	}

	/** Validates, stores and writes a new kit to {@code config/sparbot/kits/<id>.json}. */
	public void save(Kit kit, HolderLookup.Provider registries) throws IOException {
		List<String> errors = io.github.flick256.sparbot.core.kit.KitValidator.validate(kit);
		errors.addAll(KitApplier.resolveErrors(kit, registries));
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Kit '" + kit.id() + "' is invalid: " + errors);
		}
		Files.createDirectories(customDir);
		Files.writeString(customDir.resolve(kit.id() + ".json"), Kits.toJson(kit), StandardCharsets.UTF_8);
		kits.put(kit.id(), kit);
	}

	public void save(Layout layout) throws IOException {
		List<String> errors = layout.validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Layout '" + layout.id() + "' is invalid: " + errors);
		}
		Files.createDirectories(layoutDir);
		Files.writeString(layoutDir.resolve(layout.id() + ".json"), Kits.toJson(layout), StandardCharsets.UTF_8);
		layouts.put(layout.id(), layout);
	}

	public Optional<Layout> layout(String id) {
		return Optional.ofNullable(layouts.get(id));
	}

	public Collection<String> layoutIds() {
		return layouts.keySet();
	}

	private void register(Kit kit, String origin, HolderLookup.Provider registries) {
		List<String> problems = KitApplier.resolveErrors(kit, registries);
		if (!problems.isEmpty()) {
			SparBot.LOGGER.error("Kit '{}' ({}) does not resolve in this game version: {}", kit.id(), origin, problems);
			return;
		}
		kits.put(kit.id(), kit);
		if (!kit.provenance().verified()) {
			SparBot.LOGGER.info("Kit '{}' is UNVERIFIED (confidence {}): {}", kit.id(), kit.provenance().confidence(), kit.provenance().source());
		}
	}

	public Optional<Kit> get(String id) {
		return Optional.ofNullable(kits.get(id));
	}

	public Collection<String> ids() {
		return kits.keySet();
	}
}
