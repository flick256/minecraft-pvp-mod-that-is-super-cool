package io.github.flick256.sparbot.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

/** Parses skill profiles from JSON and exposes the bundled presets. */
public final class SkillProfiles {
	/** Bundled presets, weakest first. */
	public static final List<String> PRESET_IDS = List.of("beginner", "casual", "intermediate", "advanced", "pro", "demon");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private SkillProfiles() {
	}

	/** Parses and validates a profile, throwing {@link IllegalArgumentException} listing every problem. */
	public static SkillProfile parse(Reader reader) {
		SkillProfile profile;
		try {
			profile = GSON.fromJson(reader, SkillProfile.class);
		} catch (JsonParseException e) {
			throw new IllegalArgumentException("Malformed skill profile JSON: " + e.getMessage(), e);
		}
		if (profile == null) {
			throw new IllegalArgumentException("Skill profile JSON is empty");
		}
		List<String> errors = profile.validate();
		if (!errors.isEmpty()) {
			throw new IllegalArgumentException("Invalid skill profile '" + profile.id() + "': " + String.join("; ", errors));
		}
		return profile;
	}

	public static String toJson(SkillProfile profile) {
		return GSON.toJson(profile);
	}

	/** Loads every bundled preset, keyed by id, in {@link #PRESET_IDS} order. */
	public static Map<String, SkillProfile> loadPresets() {
		Map<String, SkillProfile> presets = new LinkedHashMap<>();
		for (String id : PRESET_IDS) {
			String path = "/sparbot/profiles/" + id + ".json";
			try (InputStream in = SkillProfiles.class.getResourceAsStream(path)) {
				if (in == null) {
					throw new IllegalStateException("Missing bundled profile " + path);
				}
				presets.put(id, parse(new InputStreamReader(in, StandardCharsets.UTF_8)));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return presets;
	}
}
