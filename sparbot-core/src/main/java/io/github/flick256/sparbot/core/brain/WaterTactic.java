package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
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
	/** Scoop from no farther than this (horizontally, eye to the water's centre). */
	private static final double SCOOP_REACH = 4.0;

	private enum Phase {
		IDLE,
		PLACE,
		PICKUP
	}

	private final Decision willWater = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int bucketSlot = -1;
	private BlockSpot water;

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
		if (phase == Phase.IDLE) {
			// Finished one go and chosen again straight away (onEnter only runs on a change of tactic).
			onEnter(c);
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		ticks++;
		int press = bucketSlot >= 0 ? c.memory.hands.request(c, bucketSlot) : -1;
		boolean holding = bucketSlot >= 0 && inv.selectedSlot() == bucketSlot;
		switch (phase) {
			case PLACE -> {
				float[] look = c.lookAt(self.yaw(), 90.0F);
				if (bucketSlot < 0 || inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					phase = Phase.PICKUP;
					ticks = 0;
					return new Inputs(look[0], look[1], 0, 0, false, false, false, false, false, press);
				}
				if (ticks > PLACE_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.WATER_BUCKET)) {
					return finish();
				}
				// Looking straight down the bucket clicks the top of the block under the crosshair: the web
				// the bot is stuck in (the water goes above it, into the bot's own space, and flows into the
				// web), or the ground (the water goes where the bot stands).
				BlockSpot ground = BlockPlay.groundUnder(self.position());
				water = new BlockSpot(ground.x(), ground.y() + (self.inWeb() ? 2 : 1), ground.z());
				boolean use = holding && self.pitch() >= DOWN;
				return new Inputs(look[0], look[1], 0, 0, false, false, false, false, use, press);
			}
			case PICKUP -> {
				if (bucketSlot < 0 || inv.slot(bucketSlot).is(ItemKind.WATER_BUCKET) || ticks > PICKUP_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					return finish();
				}
				// Stand in the water until the fire is out (or the web is washed away), but never for long: a
				// good player scoops it straight back up (and pours again if still stuck).
				boolean out = !self.onFire() && !self.inLava() && !self.inWeb();
				boolean waited = ticks >= maxWait(c);
				Vec3 eye = self.eyePosition();
				Vec3 point = new Vec3(water.x() + 0.5, water.y() + 0.3, water.z() + 0.5);
				float[] look = c.lookAt(Angles.yawTowards(eye, point), Angles.pitchTowards(eye, point));
				boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()), new Vec3(water.x(), water.y(), water.z()),
					new Vec3(water.x() + 1, water.y() + 1, water.z() + 1), BlockPlay.BLOCK_REACH);
				boolean inReach = water.horizontalDistanceTo(eye) <= SCOOP_REACH;
				boolean use = (out || waited) && holding && aimed && inReach;
				Inputs in = new Inputs(look[0], look[1], 0, 0, false, false, false, false, use, press);
				if (!inReach) {
					// Knocked away from it: walk back within reach.
					float to = Angles.yawTowards(self.position(), point);
					float rel = Angles.wrapDegrees(to - (self.yaw() + look[0]));
					in = Movement.guardEdges(c, in.withMovement(Math.abs(rel) < 90 ? 1 : -1, 0));
				}
				return in;
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	/** How long the bot stands in its water before scooping it: a pro a third of a second, a beginner most of a second. */
	private static int maxWait(BrainContext c) {
		return (int) Math.round(16 - 10 * c.profile.items().uhcSkill());
	}

	private Inputs finish() {
		phase = Phase.IDLE;
		willWater.reset();
		return Inputs.IDLE;
	}
}
