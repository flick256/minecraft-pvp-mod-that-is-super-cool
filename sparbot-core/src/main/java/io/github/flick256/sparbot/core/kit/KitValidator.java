package io.github.flick256.sparbot.core.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Structural validation of a kit (registry checks, e.g. "does this item exist in 26.2", happen in
 * the mod where the registries are available). Also enforces the provenance rules: a kit may only
 * claim to be verified when its contents came from the user or a primary source.
 */
public final class KitValidator {
	public static final int INVENTORY_SLOTS = 36;
	private static final Pattern KIT_ID = Pattern.compile("[a-z0-9_\\-]+");
	private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+");
	private static final Set<String> CONFIDENCE = Set.of("high", "medium", "low");
	private static final Set<String> FIRST_HAND_SOURCES = Set.of("sparbot-original", "user-supplied");

	private KitValidator() {
	}

	public static List<String> validate(Kit kit) {
		List<String> errors = new ArrayList<>();
		if (kit.id() == null || !KIT_ID.matcher(kit.id()).matches()) {
			errors.add("id must be lowercase [a-z0-9_-]+, was " + kit.id());
		}
		if (kit.displayName() == null || kit.displayName().isBlank()) {
			errors.add("displayName is required");
		}
		validateProvenance(kit.provenance(), errors);

		if (kit.armor() != null) {
			item("armor.head", kit.armor().head(), errors);
			item("armor.chest", kit.armor().chest(), errors);
			item("armor.legs", kit.armor().legs(), errors);
			item("armor.feet", kit.armor().feet(), errors);
		}
		item("offhand", kit.offhand(), errors);

		if (kit.slots() != null) {
			Set<Integer> seen = new HashSet<>();
			for (Map.Entry<String, Kit.KitItem> entry : kit.slots().entrySet()) {
				int slot;
				try {
					slot = Integer.parseInt(entry.getKey());
				} catch (NumberFormatException e) {
					errors.add("slot key '" + entry.getKey() + "' is not a number");
					continue;
				}
				if (slot < 0 || slot >= INVENTORY_SLOTS) {
					errors.add("slot " + slot + " is outside 0-35");
				}
				if (!seen.add(slot)) {
					errors.add("slot " + slot + " is defined twice");
				}
				if (entry.getValue() == null) {
					errors.add("slot " + slot + " has no item");
				} else {
					item("slots." + slot, entry.getValue(), errors);
				}
			}
		}

		if (kit.effects() != null) {
			for (Kit.KitEffect effect : kit.effects()) {
				if (effect.id() == null || !RESOURCE_ID.matcher(effect.id()).matches()) {
					errors.add("effect id must be namespaced (e.g. minecraft:speed), was " + effect.id());
				}
				if (effect.amplifier() < 0 || effect.amplifier() > 255) {
					errors.add("effect " + effect.id() + " amplifier must be 0-255");
				}
				if (effect.durationTicks() != -1 && effect.durationTicks() <= 0) {
					errors.add("effect " + effect.id() + " duration must be -1 (infinite) or positive");
				}
			}
		}
		return errors;
	}

	private static void validateProvenance(Kit.Provenance p, List<String> errors) {
		if (p == null) {
			errors.add("provenance is required");
			return;
		}
		if (p.source() == null || p.source().isBlank()) {
			errors.add("provenance.source is required");
		}
		if (p.confidence() == null || !CONFIDENCE.contains(p.confidence())) {
			errors.add("provenance.confidence must be one of " + CONFIDENCE);
		}
		if (p.minecraftVersion() == null || p.minecraftVersion().isBlank()) {
			errors.add("provenance.minecraftVersion is required");
		}
		if (p.verified()) {
			boolean firstHand = p.source() != null && FIRST_HAND_SOURCES.contains(p.source());
			boolean evidenced = p.reference() != null && !p.reference().isBlank();
			if (!firstHand && !evidenced) {
				errors.add("a verified kit needs source sparbot-original/user-supplied or a reference to its evidence");
			}
			if (!"high".equals(p.confidence())) {
				errors.add("a verified kit must have confidence 'high'");
			}
		}
		boolean translated = p.minecraftVersion() != null && !p.minecraftVersion().startsWith("26.2");
		if (translated && (p.deviations() == null || p.deviations().isEmpty())) {
			errors.add("kit translated from " + p.minecraftVersion() + " must list its deviations from the source (or [\"none\"])");
		}
	}

	private static void item(String where, Kit.KitItem item, List<String> errors) {
		if (item == null) {
			return;
		}
		if (item.id() == null || !RESOURCE_ID.matcher(item.id()).matches()) {
			errors.add(where + ": item id must be namespaced (e.g. minecraft:diamond_sword), was " + item.id());
		}
		int count = item.countOrDefault();
		if (count < 1 || count > 99) {
			errors.add(where + ": count must be 1-99, was " + count);
		}
		if (item.enchantments() != null) {
			for (Map.Entry<String, Integer> e : item.enchantments().entrySet()) {
				if (!RESOURCE_ID.matcher(e.getKey()).matches()) {
					errors.add(where + ": enchantment id must be namespaced, was " + e.getKey());
				}
				if (e.getValue() == null || e.getValue() < 1 || e.getValue() > 255) {
					errors.add(where + ": enchantment " + e.getKey() + " level must be 1-255");
				}
			}
		}
		if (item.potion() != null && !RESOURCE_ID.matcher(item.potion()).matches()) {
			errors.add(where + ": potion id must be namespaced, was " + item.potion());
		}
	}
}
