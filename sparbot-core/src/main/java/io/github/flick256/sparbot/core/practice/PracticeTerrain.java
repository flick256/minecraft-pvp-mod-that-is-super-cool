package io.github.flick256.sparbot.core.practice;

import java.util.Arrays;
import java.util.Optional;

/** Selectable ground layouts for repeatable practice fights. */
public enum PracticeTerrain {
	FLAT("flat"), UNEVEN("uneven"), OBSTACLES("obstacles");

	private final String id;

	PracticeTerrain(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static Optional<PracticeTerrain> byId(String id) {
		return Arrays.stream(values()).filter(terrain -> terrain.id.equalsIgnoreCase(id)).findFirst();
	}
}
