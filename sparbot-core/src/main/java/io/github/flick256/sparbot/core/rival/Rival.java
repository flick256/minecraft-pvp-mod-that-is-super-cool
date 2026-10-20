package io.github.flick256.sparbot.core.rival;

/** A named, authored opponent built entirely from normal SparBot mode/profile/style settings. */
public record Rival(String id, String name, String mode, String profile, String style, String description,
	String strength, String weakness, int tier, boolean champion) {
	public Rival {
		if (id == null || !id.matches("[a-z0-9_-]+") || name == null || name.isBlank() || mode == null || mode.isBlank()
			|| profile == null || profile.isBlank() || style == null || style.isBlank() || tier < 0) {
			throw new IllegalArgumentException("Invalid rival definition");
		}
	}
}
