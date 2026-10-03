package io.github.flick256.sparbot.core.kit;

import java.util.List;
import java.util.Map;

/**
 * A complete loadout, slot for slot. {@code slots} keys are vanilla inventory indices:
 * 0-8 hotbar, 9-35 main inventory.
 */
public record Kit(
	String id,
	String displayName,
	String mode,
	Provenance provenance,
	Armor armor,
	KitItem offhand,
	Map<String, KitItem> slots,
	List<KitEffect> effects
) {
	public record Armor(KitItem head, KitItem chest, KitItem legs, KitItem feet) {
	}

	/**
	 * @param count stack size, 1 when omitted
	 * @param enchantments enchantment id to level
	 * @param potion potion id for potions / splash potions / tipped arrows, e.g. {@code minecraft:strong_healing}
	 */
	public record KitItem(String id, Integer count, Map<String, Integer> enchantments, String potion) {
		public int countOrDefault() {
			return count == null ? 1 : count;
		}
	}

	/** @param durationTicks -1 for infinite */
	public record KitEffect(String id, int amplifier, int durationTicks) {
	}

	/**
	 * Where a kit came from and how sure we are that it matches the source.
	 *
	 * @param source "sparbot-original", "user-supplied", or a description of a third-party source
	 * @param server server the layout reproduces, if any
	 * @param minecraftVersion version the source layout was made for
	 * @param confidence "high", "medium" or "low"
	 * @param verified true only when contents come from the user or a primary source
	 * @param reference URL or description of the evidence
	 * @param deviations every change made when translating the layout to 26.2
	 */
	public record Provenance(
		String source,
		String server,
		String mode,
		String minecraftVersion,
		String confidence,
		boolean verified,
		String reference,
		String notes,
		List<String> deviations
	) {
	}
}
