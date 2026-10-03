package io.github.flick256.sparbot.core.kit;

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

/** Parses kits from JSON and exposes the bundled ones. */
public final class Kits {
	/** Bundled kits. See docs/kit-research.md for why no MCPVP kit is bundled yet. */
	public static final List<String> BUNDLED_IDS = List.of("basic_sword", "mctiers_sword_recreation", "sparbot_combat", "sparbot_ranged", "sparbot_nodebuff", "sparbot_mace", "sparbot_spear");

	private static final Gson GSON = new com.google.gson.GsonBuilder().setPrettyPrinting().create();

	private Kits() {
	}

	public static Kit parse(Reader reader) {
		Kit kit;
		try {
			kit = GSON.fromJson(reader, Kit.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Malformed kit JSON: " + e.getMessage(), e);
		}
		if (kit == null) {
			throw new IllegalArgumentException("Kit JSON is empty");
		}
		List<String> errors = KitValidator.validate(kit);
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid kit '" + kit.id() + "': " + String.join("; ", errors));
		}
		return kit;
	}

	public static String toJson(Kit kit) {
		return GSON.toJson(kit);
	}

	public static Layout parseLayout(Reader reader) {
		Layout layout;
		try {
			layout = GSON.fromJson(reader, Layout.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Malformed layout JSON: " + e.getMessage(), e);
		}
		if (layout == null) {
			throw new IllegalArgumentException("Layout JSON is empty");
		}
		List<String> errors = layout.validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid layout '" + layout.id() + "': " + String.join("; ", errors));
		}
		return layout;
	}

	public static String toJson(Layout layout) {
		return GSON.toJson(layout);
	}

	public static Map<String, Kit> loadBundled() {
		Map<String, Kit> kits = new LinkedHashMap<>();
		for (String id : BUNDLED_IDS) {
			String path = "/sparbot/kits/" + id + ".json";
			try (InputStream in = Kits.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Missing bundled kit " + path);
				}
				kits.put(id, parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return kits;
	}
}
