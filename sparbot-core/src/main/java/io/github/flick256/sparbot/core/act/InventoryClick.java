package io.github.flick256.sparbot.core.act;

/**
 * One click inside the player's own inventory screen: hovering inventory slot {@code slot} and
 * pressing a hotbar number key ({@code button} 0-8) or the offhand swap key ({@code button} 40).
 * That is vanilla's SWAP container input, the quickest way a human moves an item.
 *
 * @param slot vanilla inventory index of the hovered stack (0-35)
 */
public record InventoryClick(int slot, int button) {
	public InventoryClick {
		if (slot < 0 || slot >= 36) {
			throw new IllegalArgumentException("slot must be 0-35");
		}
		if (!(button >= 0 && button <= 8 || button == 40)) {
			throw new IllegalArgumentException("button must be 0-8 or 40");
		}
	}
}
