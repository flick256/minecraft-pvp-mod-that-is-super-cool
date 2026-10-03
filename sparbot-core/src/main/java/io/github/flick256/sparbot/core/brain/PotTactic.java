package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.Potions;

/**
 * NoDebuff healing: throw splash healing potions at your own feet. Vanilla 26.2 throws a splash
 * potion 20 degrees above where you look at speed 0.5 with gravity 0.05, and scales its effect by
 * (1 - distance / 4) from the impact, so looking straight down gives the full heal. Pros pot early and
 * double-pot when still low; each potion is consumed.
 */
public final class PotTactic implements Tactic {
	/** Throw once the view is this far down (90 = straight down). */
	private static final float POT_PITCH = 80.0F;
	private static final int SETTLE_TICKS = 6;
	private static final int MAX_POTS_IN_A_ROW = 2;

	private enum Phase {
		IDLE,
		AIM,
		WAIT
	}

	private Phase phase = Phase.IDLE;
	private int waitTicks;
	private int thrown;

	@Override
	public String name() {
		return "pot";
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return Scores.SURVIVAL + 0.1;
		}
		InventoryState inv = c.self.inventory();
		if (inv.hotbarSlot(Potions::isHealingSplash) < 0) {
			return 0;
		}
		double threshold = c.profile.items().potHealthFraction();
		double health = c.self.health() / c.self.maxHealth();
		boolean desperate = health < threshold * 0.6;
		if (health < threshold && (c.targetDistance() > 2.5 || desperate)) {
			return Scores.SURVIVAL + 0.06;
		}
		return 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		phase = Phase.AIM;
		thrown = 0;
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		thrown = 0;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		int slot = inv.hotbarSlot(Potions::isHealingSplash);
		if (slot < 0) {
			phase = Phase.IDLE;
			return Inputs.IDLE;
		}
		int press = c.memory.hands.request(c, slot);
		float[] look = c.lookAt(c.self.yaw(), 90.0F);
		boolean throwNow = false;
		switch (phase) {
			case AIM -> {
				if (inv.selectedSlot() == slot && c.self.pitch() >= POT_PITCH) {
					throwNow = true;
					thrown++;
					phase = Phase.WAIT;
					waitTicks = SETTLE_TICKS;
				}
			}
			case WAIT -> {
				if (--waitTicks <= 0) {
					double health = c.self.health() / c.self.maxHealth();
					phase = health < c.profile.items().potHealthFraction() && thrown < MAX_POTS_IN_A_ROW ? Phase.AIM : Phase.IDLE;
				}
			}
			default -> {
			}
		}
		// Back off a little while potting (the potion still lands at the feet when looking straight down).
		int forward = c.targetDistance() < 5 ? -1 : 0;
		return new Inputs(look[0], look[1], forward, c.memory.strafeDirection, false, false, false, false, throwNow, press);
	}
}
