package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.sense.SelfState;

/**
 * Water to get out of trouble, then scoop it back up:
 * <ul>
 *   <li>Burning or in lava: empty the bucket at its own feet (BucketItem#use places it in the space
 *       above the ground it clicks), which puts the fire out at once.</li>
 *   <li>Caught in a cobweb: look down and empty it onto the web. The water lands above the web and flows
 *       into it, and flowing water washes a web away (26.2: a web doesn't block movement, so
 *       FlowingFluid#canHoldAnyFluid lets water replace it). If the opponent drains the water, the bot
 *       does it again with its next bucket.</li>
 * </ul>
 * Skill decides how reliably and how fast the bot reacts.
 */
public final class WaterTactic implements Tactic {
	private static final int PLACE_TIMEOUT = 30;
	private static final int PICKUP_TIMEOUT = 40;
	/** Looking at least this far down, the crosshair is on the ground under the bot. */
	private static final float DOWN = 80.0F;

	private enum Phase {
		IDLE,
		PLACE,
		PICKUP
	}

	private final Decision willWater = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int bucketSlot = -1;

	@Override
	public String name() {
		return "water";
	}

	@Override
	public String detail() {
		return phase == Phase.IDLE ? "" : phase.name().toLowerCase();
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return Scores.SURVIVAL + 0.12;
		}
		SelfState self = c.self;
		boolean burning = (self.onFire() || self.inLava()) && !self.inWater();
		if (!(burning || self.inWeb()) || self.inventory().hotbarSlot(ItemKind.WATER_BUCKET) < 0) {
			return 0;
		}
		return willWater.get(c.rng, c.profile.items().uhcSkill(), 40) ? Scores.SURVIVAL + 0.12 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		if (phase == Phase.IDLE) {
			phase = Phase.PLACE;
			ticks = 0;
			bucketSlot = c.self.inventory().hotbarSlot(ItemKind.WATER_BUCKET);
		}
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		willWater.reset();
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		ticks++;
		float[] look = c.lookAt(self.yaw(), 90.0F);
		int press = bucketSlot >= 0 ? c.memory.hands.request(c, bucketSlot) : -1;
		boolean holding = bucketSlot >= 0 && inv.selectedSlot() == bucketSlot;
		switch (phase) {
			case PLACE -> {
				if (bucketSlot < 0 || inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					phase = Phase.PICKUP;
					ticks = 0;
					return new Inputs(look[0], look[1], 0, 0, false, false, false, false, false, press);
				}
				if (ticks > PLACE_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.WATER_BUCKET)) {
					return finish();
				}
				boolean use = holding && self.pitch() >= DOWN;
				return new Inputs(look[0], look[1], 0, 0, false, false, false, false, use, press);
			}
			case PICKUP -> {
				if (bucketSlot < 0 || inv.slot(bucketSlot).is(ItemKind.WATER_BUCKET) || ticks > PICKUP_TIMEOUT) {
					return finish();
				}
				// Stand in the water until the fire is out (or the web is washed away), then scoop it up.
				boolean out = !self.onFire() && !self.inLava() && !self.inWeb();
				boolean use = out && holding && self.pitch() >= DOWN && inv.slot(bucketSlot).is(ItemKind.BUCKET);
				return new Inputs(look[0], look[1], 0, 0, false, false, false, false, use, press);
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	private Inputs finish() {
		phase = Phase.IDLE;
		willWater.reset();
		return Inputs.IDLE;
	}
}
