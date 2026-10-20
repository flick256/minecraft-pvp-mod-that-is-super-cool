package io.github.flick256.sparbot.core.career;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CareerStoreTest {
	@TempDir java.nio.file.Path temp;

	@Test void savesPreferencesAndProgressAcrossReload() throws Exception {
		var file = temp.resolve("career.json");
		UUID player = UUID.randomUUID();
		CareerStore store = new CareerStore(file);
		store.load();
		store.setAdaptivePractice(player, true);
		CareerProgress p = store.progress(player, "uhc_duel");
		p.beginSeries();
		p.completeSeries(true, true);
		store.save();

		CareerStore loaded = new CareerStore(file);
		loaded.load();
		assertTrue(loaded.adaptivePractice(player));
		assertEquals(1, loaded.progress(player, "uhc_duel").wins());
		assertTrue(Files.readString(file).contains("\"version\": 1"));
	}

	@Test void rejectsUnknownVersions() throws Exception {
		var file = temp.resolve("bad.json");
		String invalid = "{\"version\":99,\"players\":{}}";
		Files.writeString(file, invalid);
		CareerStore store = new CareerStore(file);
		assertThrows(java.io.IOException.class, store::load);
		assertThrows(java.io.IOException.class, store::save);
		assertEquals(invalid, Files.readString(file), "failed load never overwrites the original save");
	}

	@Test void interruptedSeriesIsClearedWithoutAwardAfterRestart() throws Exception {
		var file = temp.resolve("interrupted.json");
		UUID player = UUID.randomUUID();
		CareerStore store = new CareerStore(file);
		store.load();
		store.progress(player, "sword_duel").beginSeries();
		store.save();
		CareerStore loaded = new CareerStore(file);
		loaded.load();
		assertTrue(loaded.cancelDanglingSeries());
		assertFalse(loaded.progress(player, "sword_duel").seriesActive());
		assertEquals(0, loaded.progress(player, "sword_duel").wins());
		assertEquals(0, loaded.progress(player, "sword_duel").losses());
	}
}
