package io.github.flick256.sparbot.core.kit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A personal arrangement of a kit's items: which item id goes in which inventory slot (and in the
 * offhand). Lets a bot copy someone's own hotbar setup without changing what the kit contains.
 *
 * @param kit id of the kit this layout was made for
 * @param slots inventory index (0-35) to item key (see {@link #key}: the item id, plus {@code #golden_head} or
 *     {@code #<potion>} where items of one id differ)
 * @param offhand item id to hold in the offhand, or null to keep the kit's offhand
 */
public record Layout(String id, String kit, Map<String, String> slots, String offhand) {
	public List<String> validate() {
		List<String> errors = new ArrayList<>();
		if (id == null || !id.matches("[a-z0-9_\\-]+")) {
			errors.add("id must be lowercase [a-z0-9_-]+, was " + id);
		}
		if (kit == null || kit.isBlank()) {
			errors.add("kit is required");
		}
		if (slots != null) {
			for (Map.Entry<String, String> e : slots.entrySet()) {
				try {
					int slot = Integer.parseInt(e.getKey());
					if (slot < 0 || slot >= KitValidator.INVENTORY_SLOTS) {
						errors.add("slot " + slot + " is outside 0-35");
					}
				} catch (NumberFormatException ex) {
					errors.add("slot key '" + e.getKey() + "' is not a number");
				}
				if (e.getValue() == null || !e.getValue().contains(":")) {
					errors.add("slot " + e.getKey() + " needs a namespaced item id");
				}
			}
		}
		return errors;
	}

	/**
	 * Returns the kit with its items moved to this layout's slots. Item stacks are never created or
	 * removed, only moved: items the layout does not mention keep their slot when it is still free,
	 * otherwise they take the first free slot.
	 */
	public Kit arrange(Kit source) {
		List<Placed> pool = new ArrayList<>();
		if (source.slots() != null) {
			new TreeMap<>(asInts(source.slots())).forEach((slot, item) -> pool.add(new Placed(slot, item)));
		}
		Kit.KitItem offhandItem = source.offhand();
		if (offhand != null && (offhandItem == null || !matches(offhandItem, offhand))) {
			for (Placed p : pool) {
				if (matches(p.item, offhand)) {
					Kit.KitItem previous = offhandItem;
					offhandItem = p.item;
					p.item = previous; // the old offhand item takes the vacated slot (or nothing)
					break;
				}
			}
			pool.removeIf(p -> p.item == null);
		}

		Map<Integer, Kit.KitItem> result = new TreeMap<>();
		if (slots != null) {
			for (Map.Entry<Integer, String> wanted : new TreeMap<>(asInts(slots)).entrySet()) {
				for (Placed p : pool) {
					if (!p.assigned && matches(p.item, wanted.getValue()) && !result.containsKey(wanted.getKey())) {
						result.put(wanted.getKey(), p.item);
						p.assigned = true;
						break;
					}
				}
			}
		}
		for (Placed p : pool) {
			if (!p.assigned && !result.containsKey(p.slot)) {
				result.put(p.slot, p.item);
				p.assigned = true;
			}
		}
		for (Placed p : pool) {
			if (!p.assigned) {
				int free = 0;
				while (result.containsKey(free)) {
					free++;
				}
				result.put(free, p.item);
				p.assigned = true;
			}
		}

		Map<String, Kit.KitItem> arranged = new LinkedHashMap<>();
		result.forEach((slot, item) -> arranged.put(Integer.toString(slot), item));
		return new Kit(source.id(), source.displayName(), source.mode(), source.provenance(), source.armor(), offhandItem, arranged, source.effects());
	}

	/**
	 * How a layout names a kit item: its id, and for items that share an id but play differently, what
	 * sets them apart (a golden head is a golden apple underneath; potions differ by their effect).
	 */
	public static String key(Kit.KitItem item) {
		return key(item.id(), item.components() != null && item.components().contains("golden_head"), item.potion());
	}

	public static String key(String id, boolean goldenHead, String potion) {
		return id + (goldenHead ? "#golden_head" : "") + (potion != null && !potion.isBlank() ? "#" + potion : "");
	}

	/** Whether a kit item is the one a layout slot asks for (a plain id, from older layouts, matches any item of that id). */
	static boolean matches(Kit.KitItem item, String wanted) {
		return wanted.contains("#") ? key(item).equals(wanted) : item.id().equals(wanted);
	}

	private static <V> Map<Integer, V> asInts(Map<String, V> map) {
		Map<Integer, V> out = new LinkedHashMap<>();
		map.forEach((k, v) -> out.put(Integer.parseInt(k), v));
		return out;
	}

	private static final class Placed {
		final int slot;
		Kit.KitItem item;
		boolean assigned;

		Placed(int slot, Kit.KitItem item) {
			this.slot = slot;
			this.item = item;
		}
	}
}
