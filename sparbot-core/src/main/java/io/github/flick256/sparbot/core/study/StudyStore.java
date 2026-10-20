package io.github.flick256.sparbot.core.study;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Versioned, fail-closed study settings and question history storage. */
public final class StudyStore {
	public static final int VERSION = 1;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path file;
	private Data data = new Data();
	private boolean writable;
	public StudyStore(Path file) { this.file = file; }

	public synchronized void load() throws IOException {
		writable = false;
		if (!Files.exists(file)) { data = new Data(); writable = true; return; }
		Data loaded;
		try { loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class); }
		catch (JsonParseException e) { throw new IOException("Invalid study data: " + e.getMessage(), e); }
		if (loaded == null || loaded.version != VERSION || loaded.settings == null || loaded.progress == null) throw new IOException("Unsupported or invalid study data version");
		try { loaded.settings.validate(); }
		catch (IllegalArgumentException e) { throw new IOException("Invalid study settings", e); }
		for (var entry : loaded.progress.entrySet()) if (entry.getKey() == null || !entry.getKey().matches("[a-zA-Z0-9._:-]{1,96}") || entry.getValue() == null || !entry.getValue().isValid()) throw new IOException("Invalid study progress data");
		data = loaded; writable = true;
	}
	public synchronized boolean writable() { return writable; }
	public synchronized Settings settings() { return data.settings; }
	public synchronized void setEnabled(boolean enabled) { data.settings.enabled = enabled; }
	public synchronized void setSelectedSubjects(Set<String> subjects) {
		if (subjects == null || subjects.stream().anyMatch(s -> s == null || !s.matches("[a-zA-Z0-9 _&+':-]{1,96}"))) throw new IllegalArgumentException("invalid subject selection");
		data.settings.selectedSubjects = new LinkedHashSet<>(subjects);
	}
	public synchronized void setExamDate(String subject, String isoDate) {
		if (subject == null || !subject.matches("[a-zA-Z0-9 _&+':-]{1,96}")) throw new IllegalArgumentException("invalid subject");
		if (isoDate == null || isoDate.isBlank()) { data.settings.examDates.remove(subject); return; }
		try { LocalDate.parse(isoDate); } catch (DateTimeParseException | NullPointerException e) { throw new IllegalArgumentException("exam date must be ISO yyyy-MM-dd", e); }
		data.settings.examDates.put(subject, isoDate);
	}
	public synchronized void clearExamDate(String subject) { data.settings.examDates.remove(subject); }
	public synchronized Map<String, String> examDates() { return Map.copyOf(data.settings.examDates); }
	public synchronized StudyProgress progress(String questionId) {
		if (questionId == null || !questionId.matches("[a-zA-Z0-9._:-]{1,96}")) throw new IllegalArgumentException("invalid question id");
		return data.progress.computeIfAbsent(questionId, ignored -> new StudyProgress());
	}
	public synchronized Map<String, StudyProgress> allProgress() { return Map.copyOf(data.progress); }
	public synchronized void save() throws IOException {
		if (!writable) throw new IOException("Study save was not loaded successfully; refusing to overwrite it");
		data.settings.validate();
		Path parent = file.toAbsolutePath().getParent(); Files.createDirectories(parent);
		Path temp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		try {
			Files.writeString(temp, GSON.toJson(data), StandardCharsets.UTF_8);
			try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
			catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
		} finally { Files.deleteIfExists(temp); }
	}
	private static final class Data {
		int version = VERSION;
		Settings settings = new Settings();
		Map<String, StudyProgress> progress = new LinkedHashMap<>();
	}
	public static final class Settings {
		private boolean enabled = true;
		private Set<String> selectedSubjects = new LinkedHashSet<>();
		private Map<String, String> examDates = new LinkedHashMap<>();
		public boolean enabled() { return enabled; }
		public Set<String> selectedSubjects() { return Set.copyOf(selectedSubjects); }
		public Map<String, String> examDates() { return Map.copyOf(examDates); }
		private void validate() {
			if (selectedSubjects == null || examDates == null || selectedSubjects.stream().anyMatch(s -> s == null || !s.matches("[a-zA-Z0-9 _&+':-]{1,96}"))) throw new IllegalArgumentException();
			for (var e : examDates.entrySet()) {
				if (e.getKey() == null || !e.getKey().matches("[a-zA-Z0-9 _&+':-]{1,96}")) throw new IllegalArgumentException();
				try { LocalDate.parse(e.getValue()); } catch (DateTimeParseException | NullPointerException ex) { throw new IllegalArgumentException(); }
			}
		}
	}
}
