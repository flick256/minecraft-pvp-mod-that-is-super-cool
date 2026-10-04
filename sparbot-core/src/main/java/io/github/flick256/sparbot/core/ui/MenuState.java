package io.github.flick256.sparbot.core.ui;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.List;

/**
 * What the in-game menu shows, sent by the server when a player opens or refreshes it: the profiles,
 * kits, playstyles and modes to pick from, the bots that exist, and the settings with their values.
 * Everything the menu does goes back through {@code /sparbot} commands, so permissions and
 * validation stay on the server.
 *
 * @param open whether the client should open the menu (false: refresh it if it is open)
 */
public record MenuState(boolean open, List<String> profiles, List<String> kits, List<String> styles, List<String> modes, List<BotEntry> bots,
	List<Setting> settings, List<KitEntry> kitInfo) {
	private static final Gson GSON = new Gson();

	/** A bot as the menu lists it. */
	public record BotEntry(String name, String profile, String style, boolean alive, float health) {
	}

	/** A kit as the Kits tab lists it; unverified layouts are marked. */
	public record KitEntry(String id, String displayName, boolean verified) {
	}

	/** A setting: name, type ("boolean", "int", "double" or "string") and current value. */
	public record Setting(String key, String type, String value) {
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static MenuState fromJson(String json) {
		MenuState state = GSON.fromJson(json, MenuState.class);
		if (state == null || state.profiles() == null || state.bots() == null || state.settings() == null || state.kitInfo() == null) {
			throw new JsonParseException("Incomplete SparBot menu state");
		}
		return state;
	}
}
