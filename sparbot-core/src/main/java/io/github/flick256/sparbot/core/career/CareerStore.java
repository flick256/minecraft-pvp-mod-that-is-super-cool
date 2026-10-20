package io.github.flick256.sparbot.core.career;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Validated, versioned career save with atomic replacement. */
public final class CareerStore {
	public static final int VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private CareerData data = new CareerData();
	private boolean writable;

	public CareerStore(Path file) { this.file = file; }

	public synchronized void load() throws IOException {
		writable = false;
		if (!Files.exists(file)) { data = new CareerData(); writable = true; return; }
		CareerData loaded;
		try { loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), CareerData.class); }
		catch (JsonParseException e) { throw new IOException("Invalid career data: " + e.getMessage(), e); }
		if (loaded == null || loaded.version != VERSION || loaded.players == null) throw new IOException("Unsupported or invalid career data version");
		for (var entry : loaded.players.entrySet()) {
			try { UUID.fromString(entry.getKey()); }
			catch (IllegalArgumentException e) { throw new IOException("Invalid player id in career data", e); }
			var player = entry.getValue();
			if (player == null || player.disciplines == null || player.disciplines.entrySet().stream().anyMatch(e -> e.getValue() == null || !e.getValue().isValidFor(e.getKey())))
				throw new IOException("Invalid career progress data");
			if (player.disciplines.keySet().stream().anyMatch(id -> id == null || !id.matches("[a-z0-9_-]{1,64}")))
				throw new IOException("Invalid discipline id in career data");
			if (player.adaptiveStreaks == null) player.adaptiveStreaks = new java.util.LinkedHashMap<>();
			if (player.adaptiveStreaks.entrySet().stream().anyMatch(e -> e.getKey() == null || !e.getKey().matches("[a-z0-9_-]{1,64}")
				|| e.getValue() == null || e.getValue() < -2 || e.getValue() > 2)) throw new IOException("Invalid adaptive practice data");
			if (player.rivals == null) player.rivals = new java.util.LinkedHashMap<>();
			if (player.rivals.entrySet().stream().anyMatch(e -> e.getKey() == null || !e.getKey().matches("[a-z0-9_-]{1,64}")
				|| e.getValue() == null || !e.getValue().isValid())) throw new IOException("Invalid rival results data");
		}
		data = loaded;
		writable = true;
	}

	public synchronized CareerProgress progress(UUID player, String discipline) {
		return data.players.computeIfAbsent(player.toString(), ignored -> new CareerData.PlayerCareer())
			.disciplines.computeIfAbsent(discipline, ignored -> new CareerProgress());
	}

	public synchronized boolean cancelDanglingSeries() {
		boolean changed = false;
		for (CareerData.PlayerCareer player : data.players.values()) {
			for (CareerProgress progress : player.disciplines.values()) changed |= progress.cancelDanglingSeries();
		}
		return changed;
	}

	public synchronized boolean adaptivePractice(UUID player) {
		return data.players.getOrDefault(player.toString(), new CareerData.PlayerCareer()).adaptivePractice;
	}

	public synchronized void setAdaptivePractice(UUID player, boolean enabled) {
		data.players.computeIfAbsent(player.toString(), ignored -> new CareerData.PlayerCareer()).adaptivePractice = enabled;
	}

	public synchronized int adaptiveStreak(UUID player, String mode) {
		CareerData.PlayerCareer career = data.players.get(player.toString());
		return career == null || career.adaptiveStreaks == null ? 0 : career.adaptiveStreaks.getOrDefault(mode, 0);
	}

	public synchronized void recordPracticeResult(UUID player, String mode, boolean won) {
		CareerData.PlayerCareer career = data.players.computeIfAbsent(player.toString(), ignored -> new CareerData.PlayerCareer());
		if (career.adaptiveStreaks == null) career.adaptiveStreaks = new java.util.LinkedHashMap<>();
		int old = career.adaptiveStreaks.getOrDefault(mode, 0);
		career.adaptiveStreaks.put(mode, won ? Math.min(2, old >= 0 ? old + 1 : 1) : Math.max(-2, old <= 0 ? old - 1 : -1));
	}

	public synchronized RivalRecord rivalRecord(UUID player, String rival) {
		CareerData.PlayerCareer career = data.players.get(player.toString());
		return career == null || career.rivals == null ? new RivalRecord() : career.rivals.getOrDefault(rival, new RivalRecord());
	}

	public synchronized void recordRivalResult(UUID player, String rival, boolean won) {
		CareerData.PlayerCareer career = data.players.computeIfAbsent(player.toString(), ignored -> new CareerData.PlayerCareer());
		if (career.rivals == null) career.rivals = new java.util.LinkedHashMap<>();
		career.rivals.computeIfAbsent(rival, ignored -> new RivalRecord()).record(won);
	}

	public synchronized void save() throws IOException {
		if (!writable) throw new IOException("Career save was not loaded successfully; refusing to overwrite it");
		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
		} finally { Files.deleteIfExists(temp); }
	}
}
