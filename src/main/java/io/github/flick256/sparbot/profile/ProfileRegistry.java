package io.github.flick256.sparbot.profile;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
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

/** Bundled skill presets plus custom profiles from {@code config/sparbot/profiles/*.json}. */
public final class ProfileRegistry {
	private final Map<String, SkillProfile> profiles = new LinkedHashMap<>();

	public void reload(Path customDir) {
		profiles.clear();
		profiles.putAll(SkillProfiles.loadPresets());
		if (Files.isDirectory(customDir)) {
			try (Stream<Path> files = Files.list(customDir)) {
				for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
						SkillProfile profile = SkillProfiles.parse(reader);
						profiles.put(profile.id(), profile);
						SparBot.LOGGER.info("Loaded custom skill profile '{}' from {}", profile.id(), file.getFileName());
					} catch (IllegalArgumentException | IOException e) {
						SparBot.LOGGER.error("Skipping skill profile {}: {}", file.getFileName(), e.getMessage());
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
	}

	public Optional<SkillProfile> get(String id) {
		return Optional.ofNullable(profiles.get(id));
	}

	public Collection<String> ids() {
		return profiles.keySet();
	}
}
