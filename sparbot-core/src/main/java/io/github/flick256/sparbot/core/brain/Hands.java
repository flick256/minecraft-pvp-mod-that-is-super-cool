package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;

/**
 * Hotbar switching with human timing: asking for a different slot takes the profile's
 * {@code hotbarSwitchMs} (deciding + pressing the number key) before the switch is emitted.
 * Note that in vanilla switching to a different item also resets the attack charge.
 */
public final class Hands {
	private int pendingSlot = -1;
	private int ticksUntilSwitch;

	/**
	 * Requests that {@code slot} be held. Returns the hotbar slot to press this tick, or -1 when no
	 * key press is due (already held, or still "reaching" for the key).
	 */
	public int request(BrainContext c, int slot) {
		return request(c.self.inventory(), slot, c.profile, c.rng);
	}

	int request(InventoryState inventory, int slot, SkillProfile profile, Rng rng) {
		if (slot < 0 || slot >= InventoryState.HOTBAR_SIZE || slot == inventory.selectedSlot()) {
			pendingSlot = -1;
			return -1;
		}
		if (pendingSlot != slot) {
			pendingSlot = slot;
			ticksUntilSwitch = (int) Math.round(profile.items().hotbarSwitchMs().sample(rng) / 50.0);
		}
		if (ticksUntilSwitch-- <= 0) {
			pendingSlot = -1;
			return slot;
		}
		return -1;
	}

	public void reset() {
		pendingSlot = -1;
		ticksUntilSwitch = 0;
	}

	/** The hotbar slot of the melee weapon the bot prefers to fight with, or -1 for none. */
	public static int preferredMeleeSlot(InventoryState inventory) {
		for (ItemKind kind : new ItemKind[] {ItemKind.SWORD, ItemKind.AXE, ItemKind.MACE, ItemKind.TRIDENT}) {
			int slot = inventory.bestHotbarWeapon(kind);
			if (slot >= 0) {
				return slot;
			}
		}
		return -1;
	}

	public static boolean holds(InventoryState inventory, ItemKind kind) {
		ItemInfo main = inventory.mainHand();
		return main.is(kind);
	}
}
