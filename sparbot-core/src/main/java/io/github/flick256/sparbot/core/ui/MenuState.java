package io.github.flick256.sparbot.core.ui;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.List;
import java.util.Map;

/**
 * What the in-game menu shows, sent by the server when a player opens or refreshes it: the profiles,
 * kits, playstyles and modes to pick from, the bots that exist, and the settings with their values.
 * Everything the menu does goes back through {@code /sparbot} commands, so permissions and
 * validation stay on the server.
 *
 * @param open whether the client should open the menu (false: refresh it if it is open)
 * @param myLayouts the kits the viewing player has their own layout for
 * @param myMedals the viewing player's best medal per drill (stages passed, 0-3)
 * @param myFights the viewing player's last fights against bots, newest last
 */
public record MenuState(boolean open, List<String> profiles, List<String> kits, List<String> styles, List<String> modes, List<BotEntry> bots,
	List<Setting> settings, List<KitEntry> kitInfo, List<String> models, List<String> myLayouts, Map<String, Integer> myMedals,
	List<io.github.flick256.sparbot.core.drill.FightReport> myFights) {
	/** Without fight reports. */
	public MenuState(boolean open, List<String> profiles, List<String> kits, List<String> styles, List<String> modes, List<BotEntry> bots,
		List<Setting> settings, List<KitEntry> kitInfo, List<String> models, List<String> myLayouts, Map<String, Integer> myMedals) {
		this(open, profiles, kits, styles, modes, bots, settings, kitInfo, models, myLayouts, myMedals, List.of());
	}

	/** Without medals. */
	public MenuState(boolean open, List<String> profiles, List<String> kits, List<String> styles, List<String> modes, List<BotEntry> bots,
		List<Setting> settings, List<KitEntry> kitInfo, List<String> models, List<String> myLayouts) {
		this(open, profiles, kits, styles, modes, bots, settings, kitInfo, models, myLayouts, Map.of());
	}

	/** Without per-player layouts. */
	public MenuState(boolean open, List<String> profiles, List<String> kits, List<String> styles, List<String> modes, List<BotEntry> bots,
		List<Setting> settings, List<KitEntry> kitInfo, List<String> models) {
		this(open, profiles, kits, styles, modes, bots, settings, kitInfo, models, List.of());
	}

	private static final Gson GSON = new Gson();

	/** A bot as the menu lists it. */
	/** @param model the learned melee model the bot fights with, "" for the scripted melee */
	public record BotEntry(String name, String profile, String style, boolean alive, float health, List<String> disabledTechniques, String model) {
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
		if (state == null || state.profiles() == null || state.bots() == null || state.settings() == null || state.kitInfo() == null || state.models() == null) {
			throw new JsonParseException("Incomplete SparBot menu state");
		}
		if (state.myLayouts() == null || state.myMedals() == null || state.myFights() == null) {
			return new MenuState(state.open(), state.profiles(), state.kits(), state.styles(), state.modes(), state.bots(), state.settings(),
				state.kitInfo(), state.models(), state.myLayouts() == null ? List.of() : state.myLayouts(),
				state.myMedals() == null ? Map.of() : state.myMedals(), state.myFights() == null ? List.of() : state.myFights());
		}
		return state;
	}
}
