package io.github.flick256.sparbot.core.match;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses game modes and exposes the bundled ones. */
public final class GameModes {
	public static final List<String> BUNDLED_IDS = List.of("sword_duel", "combat_duel", "ranged_duel", "benchmark", "nodebuff", "mace_duel");
	private static final Gson GSON = new Gson();

	private GameModes() {
	}

	public static GameMode parse(Reader reader) {
		GameMode mode;
		try {
			mode = GSON.fromJson(reader, GameMode.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Malformed game mode JSON: " + e.getMessage(), e);
		}
		if (mode == null) {
			throw new IllegalArgumentException("Game mode JSON is empty");
		}
		List<String> errors = mode.validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid game mode '" + mode.id() + "': " + String.join("; ", errors));
		}
		return mode;
	}

	public static Map<String, GameMode> loadBundled() {
		Map<String, GameMode> modes = new LinkedHashMap<>();
		for (String id : BUNDLED_IDS) {
			try (InputStream in = GameModes.class.getResourceAsStream("/sparbot/modes/" + id + ".json")) {
				if (in == null) {
					throw new IllegalStateException("Missing bundled game mode " + id);
				}
				modes.put(id, parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return modes;
	}
}
