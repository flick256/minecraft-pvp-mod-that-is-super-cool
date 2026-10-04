package io.github.flick256.sparbot.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reads and changes {@link SparBotConfig} settings by name, for {@code /sparbot config} and the menu.
 * A change that fails validation is undone. {@code commandPermission} can only be changed in the file,
 * so nobody locks themselves (or everyone else) out from inside the game.
 */
public final class ConfigEditor {
	/** One setting: its name, type ("boolean", "int", "double" or "string") and current value. */
	public record Setting(String key, String type, String value) {
	}

	private static final List<String> FILE_ONLY = List.of("commandPermission");

	private ConfigEditor() {
	}

	public static List<Setting> settings(SparBotConfig config) {
		List<Setting> settings = new ArrayList<>();
		for (Field field : fields()) {
			settings.add(new Setting(field.getName(), type(field), read(config, field)));
		}
		return settings;
	}

	public static List<String> keys() {
		return fields().stream().map(Field::getName).toList();
	}

	public static Optional<String> get(SparBotConfig config, String key) {
		return field(key).map(f -> read(config, f));
	}

	/** Sets {@code key} to {@code value}; throws IllegalArgumentException (and changes nothing) if it isn't valid. */
	public static void set(SparBotConfig config, String key, String value) {
		Field field = field(key).orElseThrow(() -> new IllegalArgumentException(FILE_ONLY.contains(key)
			? key + " can only be changed in config/sparbot.json" : "Unknown setting " + key + " (settings: " + String.join(", ", keys()) + ")"));
		Object old = readRaw(config, field);
		Object parsed = parse(field, value.trim());
		write(config, field, parsed);
		List<String> errors = config.validate();
		if (!errors.isEmpty()) {
			write(config, field, old);
			throw new IllegalArgumentException(String.join("; ", errors));
		}
	}

	private static List<Field> fields() {
		List<Field> fields = new ArrayList<>();
		for (Field field : SparBotConfig.class.getFields()) {
			int mods = field.getModifiers();
			if (!Modifier.isStatic(mods) && !Modifier.isFinal(mods) && !FILE_ONLY.contains(field.getName())) {
				fields.add(field);
			}
		}
		return fields;
	}

	private static Optional<Field> field(String key) {
		return fields().stream().filter(f -> f.getName().equals(key)).findFirst();
	}

	private static String type(Field field) {
		Class<?> t = field.getType();
		return t == boolean.class ? "boolean" : t == int.class ? "int" : t == double.class ? "double" : "string";
	}

	private static Object parse(Field field, String value) {
		try {
			return switch (type(field)) {
				case "boolean" -> {
					String v = value.toLowerCase(Locale.ROOT);
					if (!v.equals("true") && !v.equals("false")) {
						throw new IllegalArgumentException(field.getName() + " must be true or false");
					}
					yield Boolean.parseBoolean(v);
				}
				case "int" -> Integer.parseInt(value);
				case "double" -> Double.parseDouble(value);
				default -> value;
			};
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(field.getName() + " must be a " + type(field) + ", not " + value);
		}
	}

	private static String read(SparBotConfig config, Field field) {
		return String.valueOf(readRaw(config, field));
	}

	private static Object readRaw(SparBotConfig config, Field field) {
		try {
			return field.get(config);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void write(SparBotConfig config, Field field, Object value) {
		try {
			field.set(config, value);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException(e);
		}
	}
}
