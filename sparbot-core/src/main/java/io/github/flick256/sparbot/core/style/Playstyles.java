package io.github.flick256.sparbot.core.style;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses playstyles and mixes ("a:0.7,b:0.3"), and exposes the bundled presets. */
public final class Playstyles {
	public static final List<String> PRESET_IDS = List.of("balanced", "aggressive_rusher", "wtap_combo", "defensive_shield", "kiter", "pearl_aggro");
	private static final Gson GSON = new Gson();

	private Playstyles() {
	}

	public static Playstyle parse(Reader reader) {
		Playstyle style;
		try {
			style = GSON.fromJson(reader, Playstyle.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Malformed playstyle JSON: " + e.getMessage(), e);
		}
		if (style == null) {
			throw new IllegalArgumentException("Playstyle JSON is empty");
		}
		List<String> errors = style.validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid playstyle '" + style.id() + "': " + String.join("; ", errors));
		}
		return style;
	}

	public static Map<String, Playstyle> loadPresets() {
		Map<String, Playstyle> presets = new LinkedHashMap<>();
		for (String id : PRESET_IDS) {
			String path = "/sparbot/playstyles/" + id + ".json";
			try (InputStream in = Playstyles.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Missing bundled playstyle " + path);
				}
				presets.put(id, parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return presets;
	}

	/**
	 * Resolves "kiter" or a mix like "aggressive_rusher:0.7,kiter:0.3" against the known styles.
	 * Shares default to 1 and need not add up to 1.
	 */
	public static Playstyle resolve(String spec, Map<String, Playstyle> known) {
		List<Playstyle.Share> shares = new ArrayList<>();
		for (String part : spec.split(",")) {
			String[] kv = part.trim().split(":");
			Playstyle style = known.get(kv[0].trim());
			if (style == null) {
				throw new IllegalArgumentException("Unknown playstyle '" + kv[0].trim() + "' (known: " + known.keySet() + ")");
			}
			double share;
			try {
				share = kv.length > 1 ? Double.parseDouble(kv[1].trim()) : 1.0;
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Share for " + kv[0] + " is not a number: " + kv[1]);
			}
			if (share <= 0) {
				throw new IllegalArgumentException("Share for " + kv[0] + " must be positive");
			}
			shares.add(new Playstyle.Share(style, share));
		}
		return Playstyle.blend(shares);
	}
}
