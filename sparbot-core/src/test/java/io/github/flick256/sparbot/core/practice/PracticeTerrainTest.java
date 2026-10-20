package io.github.flick256.sparbot.core.practice;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PracticeTerrainTest {
	@Test
	void supportedLayoutsHaveStableCaseInsensitiveIds() {
		assertEquals(PracticeTerrain.FLAT, PracticeTerrain.byId("flat").orElseThrow());
		assertEquals(PracticeTerrain.UNEVEN, PracticeTerrain.byId("Uneven").orElseThrow());
		assertEquals(PracticeTerrain.OBSTACLES, PracticeTerrain.byId("OBSTACLES").orElseThrow());
		assertTrue(PracticeTerrain.byId("random").isEmpty());
	}
}
