package io.github.flick256.sparbot.match;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.stats.EloLadder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The Elo ladder, persisted to {@code config/sparbot/elo.json} after every match. */
public final class EloStore {
	private EloLadder ladder = new EloLadder();
	private Path file;

	public void load(Path file) {
		this.file = file;
		ladder = new EloLadder();
		if (Files.exists(file)) {
			try {
				ladder = EloLadder.fromJson(Files.readString(file, StandardCharsets.UTF_8));
			} catch (IOException | RuntimeException e) {
				SparBot.LOGGER.error("Could not read {}: {}; starting a fresh ladder", file, e.getMessage());
			}
		}
	}

	public EloLadder ladder() {
		return ladder;
	}

	public void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, ladder.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			SparBot.LOGGER.error("Could not write {}: {}", file, e.getMessage());
		}
	}
}
