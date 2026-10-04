package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * UHC lava: empty a lava bucket onto the block the opponent stands on (BucketItem#use places the lava in
 * the space above the clicked face, where the opponent is), let them burn, then scoop it back up with
 * the now-empty bucket so it can be used again and doesn't spread. While it burns, the bot backs off
 * from it: lava spreads a block every 1.5 s on flat ground. An opponent stuck in a cobweb is the best
 * moment: the bot pours the lava onto the web itself, so it lands on their head while they can't move.
 * Skill decides how often the bot goes for it and whether it remembers to pick the lava back up.
 */
public final class LavaTactic implements Tactic {
	/** Never this close: the lava would be at the bot's own feet. */
	private static final double MIN_RANGE = 2.0;
	private static final double MAX_RANGE = 4.0;
	private static final int PLACE_TIMEOUT = 40;
	/** Leave the lava this long before scooping it up, unless the opponent has left it. */
	private static final int BURN_TICKS = 30;
	private static final int PICKUP_TIMEOUT = 40;
	/** Keep at least this far (horizontally, from the lava's centre) while it burns and spreads. */
	private static final double KEEP_AWAY = 3.0;
	/** Scoop from no closer than this, and no farther than the eye can reach. */
	private static final double SCOOP_REACH = 4.2;

	private enum Phase {
		IDLE,
		PLACE,
		BURN,
		PICKUP
	}

	private final Decision willLava = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int bucketSlot = -1;
	private BlockSpot lava;
	private long placedAt;

	@Override
	public String name() {
		return "lava";
	}

	@Override
	public String detail() {
		return phase == Phase.IDLE ? "" : phase.name().toLowerCase();
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			return Scores.SPECIALIST + 0.08;
		}
		TargetState t = c.seen();
		InventoryState inv = c.self.inventory();
		if (c.target == null || t == null || !t.visible() || inv.hotbarSlot(ItemKind.LAVA_BUCKET) < 0) {
			return 0;
		}
		double d = c.targetDistance();
		if (d > MAX_RANGE || horizontal(c.self.position(), t.position()) < MIN_RANGE) {
			return 0;
		}
		if (t.inWeb() && !t.onFire()) {
			// Webbed: they can't step out of it. Any player who has the lava goes for it.
			return willLava.get(c.rng, c.profile.items().uhcSkill(), 20) ? Scores.SPECIALIST + 0.1 : 0;
		}
		if (!t.onGround() || t.onFire()) {
			return 0;
		}
		return willLava.get(c.rng, c.profile.items().uhcSkill(), 60) ? Scores.SPECIALIST + 0.03 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		if (phase == Phase.IDLE) {
			phase = Phase.PLACE;
			ticks = 0;
			bucketSlot = c.self.inventory().hotbarSlot(ItemKind.LAVA_BUCKET);
		}
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		willLava.reset();
		lava = null;
	}

	@Override
	public Inputs act(BrainContext c) {
		if (phase == Phase.IDLE) {
			// Finished one go and chosen again straight away (onEnter only runs on a change of tactic).
			onEnter(c);
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		ticks++;
		switch (phase) {
			case PLACE -> {
				if (bucketSlot >= 0 && inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					// The bucket emptied: the lava is down. Fight on while it burns.
					phase = Phase.BURN;
					placedAt = c.observation.tick();
					return BlockPlay.position(c);
				}
				if (t == null || ticks > PLACE_TIMEOUT || bucketSlot < 0 || !inv.slot(bucketSlot).is(ItemKind.LAVA_BUCKET)) {
					return finish();
				}
				// On a webbed opponent the bucket is emptied onto the web: the lava lands above it, on their head.
				BlockSpot ground = BlockPlay.groundUnder(t.position());
				BlockSpot clicked = t.inWeb() ? new BlockSpot(ground.x(), ground.y() + 1, ground.z()) : ground;
				lava = new BlockSpot(clicked.x(), clicked.y() + 1, clicked.z());
				return BlockPlay.clickTop(c, clicked, 1.0, bucketSlot);
			}
			case BURN -> {
				boolean left = t == null || lava.horizontalDistanceTo(t.position()) > 1.5;
				if (c.observation.tick() - placedAt >= BURN_TICKS || left) {
					// A careless player leaves the lava where it is.
					if (!c.rng.chance(c.profile.items().uhcSkill())) {
						return finish();
					}
					phase = Phase.PICKUP;
					ticks = 0;
				}
				return awayFromLava(c, BlockPlay.position(c), KEEP_AWAY);
			}
			case PICKUP -> {
				if (inv.slot(bucketSlot).is(ItemKind.LAVA_BUCKET) || ticks > PICKUP_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					return finish();
				}
				// Look into the lava: the empty bucket scoops the source the crosshair passes through.
				Vec3 eye = self.eyePosition();
				Vec3 point = new Vec3(lava.x() + 0.5, lava.y() + 0.4, lava.z() + 0.5);
				float[] look = c.lookAt(Angles.yawTowards(eye, point), Angles.pitchTowards(eye, point));
				int press = c.memory.hands.request(c, bucketSlot);
				boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()), new Vec3(lava.x(), lava.y(), lava.z()),
					new Vec3(lava.x() + 1, lava.y() + 0.9, lava.z() + 1), BlockPlay.BLOCK_REACH);
				boolean inReach = lava.horizontalDistanceTo(eye) <= SCOOP_REACH;
				boolean use = aimed && inReach && inv.selectedSlot() == bucketSlot;
				Inputs move = BlockPlay.spacing(c, look, press).withUse(use);
				// Step up to scooping reach, but never into the spreading lava.
				return awayFromLava(c, inReach ? move : towards(c, move, lava), KEEP_AWAY - 0.5);
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	/** Overrides the movement keys to back away from the lava while closer than {@code keep}. */
	private Inputs awayFromLava(BrainContext c, Inputs in, double keep) {
		if (lava.horizontalDistanceTo(c.self.position()) >= keep) {
			return in;
		}
		float away = Angles.yawTowards(new Vec3(lava.x() + 0.5, c.self.position().y(), lava.z() + 0.5), c.self.position());
		return Movement.guardEdges(c, steer(in, c.self.yaw() + in.yawDelta(), away));
	}

	private static Inputs towards(BrainContext c, Inputs in, BlockSpot block) {
		float to = Angles.yawTowards(c.self.position(), new Vec3(block.x() + 0.5, c.self.position().y(), block.z() + 0.5));
		return steer(in, c.self.yaw() + in.yawDelta(), to);
	}

	/** Movement keys that walk towards world yaw {@code goal} while facing {@code facing}. */
	private static Inputs steer(Inputs in, float facing, float goal) {
		float rel = Angles.wrapDegrees(goal - facing);
		int forward = Math.abs(rel) < 67.5F ? 1 : Math.abs(rel) > 112.5F ? -1 : 0;
		int strafe = rel < -22.5F && rel > -157.5F ? 1 : rel > 22.5F && rel < 157.5F ? -1 : 0;
		return in.withMovement(forward, strafe).withSprint(false);
	}

	private Inputs finish() {
		phase = Phase.IDLE;
		willLava.reset();
		return Inputs.IDLE;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x() - b.x();
		double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
