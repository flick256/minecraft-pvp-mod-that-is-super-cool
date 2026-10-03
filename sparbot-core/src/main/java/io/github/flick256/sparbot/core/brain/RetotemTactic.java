package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;

/**
 * Keeps a totem in the offhand. After a pop (offhand empty) the bot puts in a new one the way a
 * player does: hotbar slot + swap-hands key when the totem is on the hotbar, otherwise open the
 * inventory, hover the totem and press the swap key (taking the profile's inventory time, during which
 * it cannot move or fight). Low on health with a shield in the offhand, it swaps the shield for a totem.
 */
public final class RetotemTactic implements Tactic {
	private enum Phase {
		IDLE,
		HOTBAR,
		OPEN,
		CLOSE
	}

	/** Longest the bot keeps the screen open waiting for its click to land (covers simulated latency). */
	private static final int CLOSE_WAIT_TICKS = 12;

	private final Decision willRetotem = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticksLeft;
	private int totemSlot = -1;

	@Override
	public String name() {
		return "retotem";
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return 0.97; // never abandon an open inventory mid-move
		}
		InventoryState inv = c.self.inventory();
		if (inv.offhand().is(ItemKind.TOTEM) || inv.usingItem() || totemSlot(inv) < 0) {
			return 0;
		}
		boolean offhandFree = inv.offhand().isEmpty();
		boolean swapShieldForTotem = inv.offhand().is(ItemKind.SHIELD) && c.self.healthFraction() < 0.4;
		if (!offhandFree && !swapShieldForTotem) {
			return 0;
		}
		return willRetotem.get(c.rng, c.profile.items().retotemSkill(), 100) ? 0.93 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		InventoryState inv = c.self.inventory();
		totemSlot = totemSlot(inv);
		if (totemSlot < InventoryState.HOTBAR_SIZE && inv.offhand().isEmpty()) {
			phase = Phase.HOTBAR;
		} else {
			phase = Phase.OPEN;
			// The screen must be open for a few ticks before a click can land (InputShaper rule).
			ticksLeft = Math.max(InputShaper.MIN_TICKS_OPEN_BEFORE_CLICK + 1,
				(int) Math.round(c.profile.items().inventoryMs().sample(c.rng) / 50.0));
		}
	}

	@Override
	public void reset() {
		willRetotem.reset();
		phase = Phase.IDLE;
		totemSlot = -1;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		switch (phase) {
			case HOTBAR -> {
				if (inv.offhand().is(ItemKind.TOTEM) || totemSlot < 0 || !inv.slot(totemSlot).is(ItemKind.TOTEM)) {
					phase = Phase.IDLE;
					return Inputs.IDLE;
				}
				int press = c.memory.hands.request(c, totemSlot);
				if (inv.selectedSlot() == totemSlot) {
					phase = Phase.IDLE;
					return Inputs.IDLE.withSwapOffhand(true);
				}
				return Inputs.IDLE.withHotbarSlot(press);
			}
			case OPEN -> {
				if (--ticksLeft > 0) {
					return Inputs.inInventory(null);
				}
				phase = Phase.CLOSE;
				ticksLeft = CLOSE_WAIT_TICKS;
				return Inputs.inInventory(new InventoryClick(totemSlot, InventoryState.OFFHAND_BUTTON));
			}
			case CLOSE -> {
				// Click exactly once (a second SWAP would move the totem back out); wait for it to arrive
				// over the simulated latency, then close the screen.
				if (!inv.offhand().is(ItemKind.TOTEM) && --ticksLeft > 0) {
					return Inputs.inInventory(null);
				}
				phase = Phase.IDLE;
				return Inputs.IDLE;
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	static int totemSlot(InventoryState inv) {
		int hotbar = inv.hotbarSlot(ItemKind.TOTEM);
		return hotbar >= 0 ? hotbar : inv.mainInventorySlot(i -> i.kind() == ItemKind.TOTEM);
	}
}
