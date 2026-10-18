package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.act.InventoryClick;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.item.Potions;

/** Uses missing buffs between engagements, temporarily staging inventory potions on a full hotbar. */
public final class BuffTactic implements Tactic {
	private static final double SAFE_DISTANCE = 7.0;
	private enum Phase {
		IDLE, OPEN, STAGE_ACK, USE, RESTORE_OPEN, RESTORE_ACK
	}
	private Phase phase = Phase.IDLE;
	private int slot = -1;
	private int source = -1;
	private int ticksLeft;
	private ItemInfo original = ItemInfo.EMPTY;
	private ItemInfo potion = ItemInfo.EMPTY;
	private boolean started;

	@Override
	public boolean requiresFocus() {
		return true;
	}

	@Override
	public String name() {
		return "buff";
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (phase == Phase.USE && started && inv.usingItem() && inv.usingKind() == ItemKind.DRINK_POTION) {
			return Scores.SURVIVAL;
		}
		if (c.targetDistance() < SAFE_DISTANCE) {
			return 0;
		}
		return phase != Phase.IDLE || missingBuffSlot(c) >= 0 ? Scores.SPECIALIST + 0.02 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		// Resume a staged potion after an interruption.
		if (phase != Phase.IDLE) {
			return;
		}
		int found = missingBuffSlot(c);
		if (found < 0) {
			return;
		}
		InventoryState inv = c.self.inventory();
		potion = inv.slot(found);
		started = false;
		if (found < InventoryState.HOTBAR_SIZE) {
			slot = found;
			phase = Phase.USE;
		} else {
			source = found;
			slot = inv.emptyHotbarSlot();
			if (slot < 0) {
				slot = InventoryState.HOTBAR_SIZE - 1;
			}
			original = inv.slot(slot);
			phase = Phase.OPEN;
			ticksLeft = inventoryTicks(c);
		}
	}

	@Override
	public void onExit(BrainContext c) {
		// An interrupted opening must allow time to reopen the screen before the number-key click.
		if (phase == Phase.OPEN || phase == Phase.RESTORE_OPEN) {
			ticksLeft = inventoryTicks(c);
		}
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		slot = source = -1;
		started = false;
		original = potion = ItemInfo.EMPTY;
	}

	@Override
	public Inputs act(BrainContext c) {
		if (phase == Phase.IDLE) {
			onEnter(c);
		}
		InventoryState inv = c.self.inventory();
		switch (phase) {
			case OPEN, RESTORE_OPEN -> {
				if (--ticksLeft > 0) {
					return Inputs.inInventory(null);
				}
				boolean restoring = phase == Phase.RESTORE_OPEN;
				// Never swap an unrelated stack after an external inventory change.
				boolean unchanged = restoring ? inv.slot(source).equals(original)
					: inv.slot(source).equals(potion) && inv.slot(slot).equals(original);
				if (!unchanged) {
					reset();
					return Inputs.IDLE;
				}
				phase = restoring ? Phase.RESTORE_ACK : Phase.STAGE_ACK;
				ticksLeft = 40;
				return Inputs.inInventory(new InventoryClick(source, slot));
			}
			case STAGE_ACK, RESTORE_ACK -> {
				boolean restoring = phase == Phase.RESTORE_ACK;
				if (restoring ? inv.slot(slot).equals(original) : inv.slot(slot).equals(potion) && inv.slot(source).equals(original)) {
					if (restoring) {
						reset();
					} else {
						phase = Phase.USE;
					}
				} else if (--ticksLeft <= 0) {
					reset(); // An unacknowledged click must never be repeated blindly.
				}
				return Inputs.IDLE; // Close the screen before using an item.
			}
			case USE -> {
				ItemInfo held = inv.slot(slot);
				boolean consumed = !held.id().equals(potion.id()) || !java.util.Objects.equals(held.potion(), potion.potion()) || held.count() < potion.count();
				if (consumed || c.self.hasEffect(Potions.effectOf(potion.potion()))) {
					if (source >= 0) {
						phase = Phase.RESTORE_OPEN;
						ticksLeft = inventoryTicks(c);
					} else {
						reset();
					}
					return Inputs.IDLE;
				}
				int press = c.memory.hands.request(c, slot);
				boolean inHand = inv.selectedSlot() == slot;
				if (held.is(ItemKind.SPLASH_POTION)) {
					float[] look = c.lookAt(c.self.yaw(), 90.0F);
					return new Inputs(look[0], look[1], 0, 0, false, false, false, false, inHand && c.self.pitch() >= 80.0F, press);
				}
				started |= inHand;
				return new Inputs(0, 0, 0, 0, false, false, false, false, inHand, press);
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	private static int inventoryTicks(BrainContext c) {
		return Math.max(InputShaper.MIN_TICKS_OPEN_BEFORE_CLICK + 1, (int) Math.round(c.profile.items().inventoryMs().sample(c.rng) / 50.0));
	}

	/** Search the hotbar first, then the main inventory, without changing the supplied kit layout. */
	static int missingBuffSlot(BrainContext c) {
		for (int i = 0; i < InventoryState.SIZE; i++) {
			ItemInfo item = c.self.inventory().slot(i);
			if (Potions.isBuff(item) && !c.self.hasEffect(Potions.effectOf(item.potion()))) {
				return i;
			}
		}
		return -1;
	}
}
