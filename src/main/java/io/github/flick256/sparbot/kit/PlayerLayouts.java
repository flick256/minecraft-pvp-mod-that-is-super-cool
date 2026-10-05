package io.github.flick256.sparbot.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.kit.Layout;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Each player's own hotbar arrangement per kit, saved with {@code /sparbot kit layout save <kit>} and
 * applied whenever that player equips the kit, so nobody has to sort their hotbar every time. Kept in
 * {@code config/sparbot/my_layouts/<uuid>.json} (kit id to layout).
 */
public final class PlayerLayouts {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final java.lang.reflect.Type TYPE = new TypeToken<TreeMap<String, Layout>>() {
	}.getType();

	private final Path directory;

	public PlayerLayouts(Path directory) {
		this.directory = directory;
	}

	public Optional<Layout> get(UUID player, String kit) {
		return Optional.ofNullable(load(player).get(kit));
	}

	/** The kits this player has their own layout for. */
	public List<String> kits(UUID player) {
		return List.copyOf(load(player).keySet());
	}

	public void save(UUID player, Layout layout) throws IOException {
		Map<String, Layout> all = load(player);
		all.put(layout.kit(), layout);
		write(player, all);
	}

	public boolean remove(UUID player, String kit) throws IOException {
		Map<String, Layout> all = load(player);
		if (all.remove(kit) == null) {
			return false;
		}
		write(player, all);
		return true;
	}

	private Map<String, Layout> load(UUID player) {
		Path file = file(player);
		if (!Files.isRegularFile(file)) {
			return new TreeMap<>();
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			Map<String, Layout> all = GSON.fromJson(reader, TYPE);
			return all == null ? new TreeMap<>() : new TreeMap<>(all);
		} catch (IOException | RuntimeException e) {
			SparBot.LOGGER.warn("Could not read {}: {}", file, e.getMessage());
			return new TreeMap<>();
		}
	}

	private void write(UUID player, Map<String, Layout> all) throws IOException {
		Files.createDirectories(directory);
		try (Writer writer = Files.newBufferedWriter(file(player), StandardCharsets.UTF_8)) {
			GSON.toJson(all, TYPE, writer);
		}
	}

	private Path file(UUID player) {
		return directory.resolve(player + ".json");
	}
}
