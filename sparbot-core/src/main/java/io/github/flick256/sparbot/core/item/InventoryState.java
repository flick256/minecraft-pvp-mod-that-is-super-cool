package io.github.flick256.sparbot.core.item;

import java.util.function.Predicate;

/**
 * The bot's own inventory and item-use state.
 *
 * @param slots 36 entries, vanilla indices: 0-8 hotbar, 9-35 main inventory
 * @param armor head, chest, legs, feet
 * @param useTicks how long the current item has been in use (0 when not using)
 * @param usingKind kind of the item in use, {@link ItemKind#EMPTY} when not using one
 */
public record InventoryState(
	ItemInfo[] slots,
	ItemInfo offhand,
	ItemInfo[] armor,
	int selectedSlot,
	boolean usingItem,
	boolean usingOffhand,
	int useTicks,
	ItemKind usingKind,
	boolean blocking,
	boolean fishingHookOut,
	boolean fishingHookOnTarget
) {
	public static final int HOTBAR_SIZE = 9;
	public static final int SIZE = 36;
	/** Container-click button that targets the offhand (vanilla AbstractContainerMenu SWAP semantics). */
	public static final int OFFHAND_BUTTON = 40;

	public static InventoryState empty() {
		ItemInfo[] slots = new ItemInfo[SIZE];
		java.util.Arrays.fill(slots, ItemInfo.EMPTY);
		ItemInfo[] armor = {ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY, ItemInfo.EMPTY};
		return new InventoryState(slots, ItemInfo.EMPTY, armor, 0, false, false, 0, ItemKind.EMPTY, false, false, false);
	}

	public ItemInfo mainHand() {
		return slots[selectedSlot];
	}

	public ItemInfo slot(int index) {
		return slots[index];
	}

	/** Best hotbar slot holding a matching item, or -1. Prefers the selected slot, then the lowest index. */
	public int hotbarSlot(Predicate<ItemInfo> matcher) {
		if (matcher.test(mainHand()) && !mainHand().isEmpty()) {
			return selectedSlot;
		}
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (!slots[i].isEmpty() && matcher.test(slots[i])) {
				return i;
			}
		}
		return -1;
	}

	public int hotbarSlot(ItemKind kind) {
		return hotbarSlot(i -> i.kind() == kind);
	}

	/** First main-inventory (9-35) slot holding a matching item, or -1. */
	public int mainInventorySlot(Predicate<ItemInfo> matcher) {
		for (int i = HOTBAR_SIZE; i < SIZE; i++) {
			if (!slots[i].isEmpty() && matcher.test(slots[i])) {
				return i;
			}
		}
		return -1;
	}

	public int count(ItemKind kind) {
		int total = offhand.is(kind) ? offhand.count() : 0;
		for (ItemInfo item : slots) {
			if (item.is(kind)) {
				total += item.count();
			}
		}
		return total;
	}

	/** Hotbar slot of the strongest melee weapon of the given kind (by attack damage), or -1. */
	public int bestHotbarWeapon(ItemKind kind) {
		int best = -1;
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (slots[i].is(kind) && (best < 0 || slots[i].attackDamage() > slots[best].attackDamage())) {
				best = i;
			}
		}
		return best;
	}

	/** A free hotbar slot, or -1. */
	public int emptyHotbarSlot() {
		for (int i = 0; i < HOTBAR_SIZE; i++) {
			if (slots[i].isEmpty()) {
				return i;
			}
		}
		return -1;
	}
}
