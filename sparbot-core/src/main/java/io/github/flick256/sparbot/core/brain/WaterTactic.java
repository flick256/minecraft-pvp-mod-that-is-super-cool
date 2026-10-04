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
 *   <li>Caught in a cobweb: empty it onto the top of the web it is caught in. The water lands above the
 *       web and falls into it five ticks later (a water tick), and flowing water washes a web away (26.2: a
 *       web doesn't block movement, so FlowingFluid#canHoldAnyFluid lets water replace it). The web may
 *       be in any of the blocks the body overlaps, not under the eyes: water poured at the feet would take
 *       ten ticks to flow round to a web diagonally next to it. The bot waits until it is free, then
 *       scoops the water back. If the opponent drains the water, it does it again with its next bucket.</li>
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
	/** Still in the web this long after pouring: the water didn't reach it; scoop it and pour again. */
	private static final int WEB_WAIT = 14;

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
				Inputs onWeb = self.inWeb() ? pourOnWeb(c, look, press, holding) : null;
				if (onWeb != null) {
					return onWeb.withMovement(0, 0).withSprint(false).withJump(false);
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
				// good player scoops it straight back up (and pours again if still stuck). Washing a web away
				// takes a water tick (5 ticks) and a moment to step out of it.
				boolean out = !self.onFire() && !self.inLava() && !self.inWeb();
				boolean waited = ticks >= (self.inWeb() ? WEB_WAIT : maxWait(c));
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

	/**
	 * Pours the water where it washes away the web the bot is caught in (BucketItem#use puts it next to the
	 * face the crosshair is on):
	 * <ul>
	 *   <li>eyes inside a web: looking straight down, the crosshair is on that web from inside, on its top
	 *       side, so the water goes above it and falls through it (and through a web under it);</li>
	 *   <li>the highest web on the body is below the eyes: on its top, the same way;</li>
	 *   <li>a web at head height in the next block: on its side facing the bot, so the water goes into the
	 *       bot's own block, flows into the web and falls to the feet.</li>
	 * </ul>
	 * Returns null when no web is known (the caller pours at its feet).
	 */
	private Inputs pourOnWeb(BrainContext c, float[] downLook, int press, boolean holding) {
		SelfState self = c.self;
		Vec3 eye = self.eyePosition();
		BlockSpot eyeBlock = new BlockSpot((int) Math.floor(eye.x()), (int) Math.floor(eye.y()), (int) Math.floor(eye.z()));
		if (c.world.webs().contains(eyeBlock)) {
			water = new BlockSpot(eyeBlock.x(), eyeBlock.y() + 1, eyeBlock.z());
			return new Inputs(downLook[0], downLook[1], 0, 0, false, false, false, false, holding && self.pitch() >= DOWN, press);
		}
		BlockSpot web = caughtIn(c);
		if (web == null) {
			return null;
		}
		if (web.y() + 1 < eye.y() - 0.05) {
			water = new BlockSpot(web.x(), web.y() + 1, web.z());
			return BlockPlay.clickTop(c, web, 1.0, bucketSlot);
		}
		// Its side facing the bot: along whichever horizontal axis the bot is farther off it.
		double dx = self.position().x() - (web.x() + 0.5);
		double dz = self.position().z() - (web.z() + 0.5);
		int fx = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0;
		int fz = fx == 0 ? (dz > 0 ? 1 : -1) : 0;
		water = new BlockSpot(web.x() + fx, web.y(), web.z() + fz);
		Vec3 face = new Vec3(web.x() + 0.5 + fx * 0.5, web.y() + 0.5, web.z() + 0.5 + fz * 0.5);
		float[] look = c.lookAt(Angles.yawTowards(eye, face), Angles.pitchTowards(eye, face));
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		double entry = BrainContext.rayEntry(eye, dir, new Vec3(web.x(), web.y(), web.z()), new Vec3(web.x() + 1, web.y() + 1, web.z() + 1),
			BlockPlay.BLOCK_REACH);
		boolean onFace = entry >= 0 && (fx != 0 ? Math.abs(eye.x() + dir.x() * entry - (web.x() + (fx > 0 ? 1 : 0))) < 1e-3
			: Math.abs(eye.z() + dir.z() * entry - (web.z() + (fz > 0 ? 1 : 0))) < 1e-3);
		return new Inputs(look[0], look[1], 0, 0, false, false, false, false, holding && onFace, press);
	}

	/**
	 * The cobweb the bot is caught in: of the webs overlapping its body, the highest (water poured on top of
	 * a stack falls through all of it). Null if none is known.
	 */
	static BlockSpot caughtIn(BrainContext c) {
		Vec3 p = c.self.position();
		BlockSpot best = null;
		for (BlockSpot w : c.world.webs()) {
			boolean overlaps = p.x() + BODY > w.x() && p.x() - BODY < w.x() + 1 && p.z() + BODY > w.z() && p.z() - BODY < w.z() + 1
				&& p.y() + 1.8 > w.y() && p.y() < w.y() + 1;
			if (overlaps && (best == null || w.y() > best.y())) {
				best = w;
			}
		}
		return best;
	}

	private static final double BODY = 0.3;

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
