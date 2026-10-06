package io.github.flick256.sparbot.core.ui;

import java.util.regex.Pattern;

/**
 * The {@code /sparbot} commands the menu sends (without the leading slash, as a client sends them).
 * Each argument is checked here first, so a typo in the menu can't turn into a different command.
 */
public final class MenuCommands {
	/** Player names: 1-16 letters, digits and underscores. */
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	/** Profile, kit, playstyle and setting ids. */
	private static final Pattern ID = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");
	/** A playstyle mix: style:share,style:share. */
	private static final Pattern STYLE = Pattern.compile("[a-z0-9_\\-]+(:[0-9.]+)?(,[a-z0-9_\\-]+(:[0-9.]+)?)*");

	private MenuCommands() {
	}

	public static String spawn(String name, String profile, String kit) {
		return "sparbot spawn " + name(name) + " " + id(profile) + " " + id(kit);
	}

	public static String style(String bot, String style) {
		if (!STYLE.matcher(style).matches()) {
			throw new IllegalArgumentException("Not a playstyle: " + style);
		}
		return "sparbot style set " + name(bot) + " " + style;
	}

	public static String fight(String bot, String target) {
		return "sparbot fight " + name(bot) + " " + name(target);
	}

	/** Equips the player who sends it with a kit (replacing their inventory). */
	public static String giveKit(String kit) {
		return "sparbot kit give " + id(kit);
	}

	/** Saves the sender's current inventory arrangement as their own layout for a kit. */
	public static String saveLayout(String kit) {
		return "sparbot layout save " + id(kit);
	}

	/** Drops the sender's own layout for a kit (back to the kit's default arrangement). */
	public static String resetLayout(String kit) {
		return "sparbot layout reset " + id(kit);
	}

	/** Fully heals the sender (outside matches). */
	public static String heal() {
		return "sparbot heal";
	}

	/** Switches one of a bot's techniques on or off. */
	public static String technique(String bot, String technique, boolean on) {
		return "sparbot technique " + name(bot) + " " + id(technique) + (on ? " on" : " off");
	}

	/** Has a bot fight in melee with a learned model ({@code off}: the scripted melee). */
	public static String model(String bot, String model) {
		return "sparbot model " + name(bot) + " " + id(model);
	}

	public static String remove(String bot) {
		return "sparbot remove " + name(bot);
	}

	public static String kill(String bot) {
		return "sparbot kill " + name(bot);
	}

	public static String respawn(String bot) {
		return "sparbot respawn " + name(bot);
	}

	public static String setConfig(String key, String value) {
		if (value.isBlank() || value.chars().anyMatch(ch -> ch < 0x20)) {
			throw new IllegalArgumentException("Not a value: " + value);
		}
		return "sparbot config set " + id(key) + " " + value.trim();
	}

	public static boolean validName(String name) {
		return NAME.matcher(name).matches();
	}

	private static String name(String name) {
		if (!validName(name)) {
			throw new IllegalArgumentException("Names are 1-16 letters, digits or _: " + name);
		}
		return name;
	}

	private static String id(String id) {
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("Not an id: " + id);
		}
		return id;
	}

	/** To the practice world's hub. */
	public static String practiceHub() {
		return "sparbot practice";
	}

	/** To the Arcane Colosseum, the free-for-all stadium. */
	public static String practiceColosseum() {
		return "sparbot practice colosseum";
	}

	/** Back from the practice world. */
	public static String practiceLeave() {
		return "sparbot practice leave";
	}

	/** A practice match against a bot of {@code profile} in the arena for {@code mode}. */
	public static String practiceFight(String mode, String profile) {
		return "sparbot practice fight " + id(mode) + " " + id(profile);
	}

	/** Starts a skill drill in the training hall. */
	public static String drill(String id) {
		return "sparbot drill " + id(id);
	}

	public static String drillStop() {
		return "sparbot drill stop";
	}

	public static String practiceHall() {
		return "sparbot practice hall";
	}

	public static String colosseumWaves() {
		return "sparbot practice colosseum waves";
	}

	public static String colosseumHill(String profile) {
		return "sparbot practice colosseum hill " + id(profile);
	}

	public static String colosseumStop() {
		return "sparbot practice colosseum stop";
	}
}
