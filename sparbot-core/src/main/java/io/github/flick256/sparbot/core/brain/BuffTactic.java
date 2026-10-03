package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.item.Potions;

/**
 * Keeps speed / fire resistance / strength up: when one has worn off and the opponent is not close,
 * drink the potion (vanilla drink time, slowed movement) or splash it at the feet.
 */
public final class BuffTactic implements Tactic {
	private static final double SAFE_DISTANCE = 7.0;

	private int slot = -1;
	private int startCount;
	private boolean started;

	@Override
	public String name() {
		return "buff";
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (started && inv.usingItem() && inv.usingKind() == ItemKind.DRINK_POTION) {
			return Scores.SURVIVAL;
		}
		if (c.targetDistance() < SAFE_DISTANCE) {
			return 0;
		}
		return missingBuffSlot(c) >= 0 ? Scores.SPECIALIST + 0.02 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		slot = missingBuffSlot(c);
		started = false;
	}

	@Override
	public void reset() {
		slot = -1;
		started = false;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (slot < 0 || !Potions.isBuff(inv.slot(slot))) {
			slot = missingBuffSlot(c);
			if (slot < 0) {
				return Inputs.IDLE;
			}
		}
		ItemInfo potion = inv.slot(slot);
		int press = c.memory.hands.request(c, slot);
		boolean inHand = inv.selectedSlot() == slot;
		if (potion.is(ItemKind.SPLASH_POTION)) {
			float[] look = c.lookAt(c.self.yaw(), 90.0F);
			boolean throwNow = inHand && c.self.pitch() >= 80.0F;
			return new Inputs(look[0], look[1], 0, 0, false, false, false, false, throwNow, press);
		}
		if (inHand && !started) {
			started = true;
			startCount = potion.count();
		}
		if (started && potion.count() < startCount) {
			started = false; // drunk
		}
		return new Inputs(0, 0, 0, 0, false, false, false, false, inHand, press);
	}

	/** Hotbar slot of a buff potion whose effect the bot does not currently have, or -1. */
	static int missingBuffSlot(BrainContext c) {
		InventoryState inv = c.self.inventory();
		for (int i = 0; i < InventoryState.HOTBAR_SIZE; i++) {
			ItemInfo item = inv.slot(i);
			if (Potions.isBuff(item) && !c.self.hasEffect(Potions.effectOf(item.potion()))) {
				return i;
			}
		}
		return -1;
	}
}
