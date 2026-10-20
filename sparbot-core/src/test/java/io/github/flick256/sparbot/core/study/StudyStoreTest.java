package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StudyStoreTest {
	@TempDir Path dir;
	@Test void persistsSettingsAndProgressAndRejectsCorruptOverwrite() throws Exception {
		Path file = dir.resolve("study.json");
		var store = new StudyStore(file); store.load(); assertTrue(store.writable());
		store.setSelectedSubjects(Set.of("Biology", "Maths", "History: Revolutions")); store.setExamDate("Biology", "2027-11-02");
		store.progress("biology.topic.one").record(true, false, 42); store.save();
		var loaded = new StudyStore(file); loaded.load();
		assertTrue(loaded.settings().enabled()); assertEquals(Set.of("Biology", "Maths", "History: Revolutions"), loaded.settings().selectedSubjects());
		assertEquals("2027-11-02", loaded.examDates().get("Biology")); assertEquals(1, loaded.progress("biology.topic.one").attempts());
		loaded.setExamDate("Biology", ""); assertFalse(loaded.examDates().containsKey("Biology"));
		Files.writeString(file, "{broken"); var corrupt = new StudyStore(file); assertThrows(java.io.IOException.class, corrupt::load);
		assertFalse(corrupt.writable()); assertThrows(java.io.IOException.class, corrupt::save);
	}
}
