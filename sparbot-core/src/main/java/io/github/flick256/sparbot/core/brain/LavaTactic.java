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
 * UHC lava, the way good players use it: pour, scoop it straight back up a few ticks later, pour again.
 * Each pour sets the opponent on fire (and hurts every tick they stand in it), scooping it before it
 * spreads keeps it from turning on the bot, and the rhythm keeps the opponent busy dodging or reaching
 * for water. BucketItem#use puts the lava in the space above the block it clicks, so the bot clicks the
 * ground under the opponent; against an opponent stuck in a cobweb it clicks the web itself, so the
 * lava lands on their head, and leaves it a little longer since they can't step out. The lava is never
 * left behind. Skill decides how often the bot goes for it, how many pours it chains and how quickly
 * it scoops.
 */
public final class LavaTactic implements Tactic {
	/** Never pour closer than this (horizontally): the lava would be at the bot's own feet. */
	private static final double MIN_RANGE = 2.0;
	private static final double MAX_RANGE = 4.3;
	private static final int PLACE_TIMEOUT = 30;
	private static final int SCOOP_TIMEOUT = 25;
	/** Interrupted (by water or eating) for longer than this, the lava is given up on. */
	private static final int GIVE_UP_AFTER = 100;
	/** Keep at least this far (horizontally, from the lava's centre) while it is down. */
	private static final double KEEP_AWAY = 2.0;
	/** Scoop from no farther than this. */
	private static final double SCOOP_REACH = 4.2;
	/** An opponent this far from the lava has left it: scoop at once. */
	private static final double LEFT_LAVA = 1.5;

	private enum Phase {
		IDLE,
		PLACE,
		HOLD,
		SCOOP
	}

	private final Decision willLava = new Decision();
	private Phase phase = Phase.IDLE;
	private int ticks;
	private int holdTicks;
	private int pours;
	private int poursPlanned;
	private int bucketSlot = -1;
	private BlockSpot lava;
	private long lastActed;
	private long lastSpam = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "lava";
	}

	@Override
	public String detail() {
		return phase == Phase.IDLE ? "" : phase.name().toLowerCase() + " " + pours + "/" + poursPlanned;
	}

	@Override
	public double score(BrainContext c) {
		if (phase != Phase.IDLE) {
			if (c.observation.tick() - lastActed > GIVE_UP_AFTER) {
				finish(c);
			} else {
				return Scores.SPECIALIST + 0.1;
			}
		}
		TargetState t = c.seen();
		InventoryState inv = c.self.inventory();
		if (c.target == null || t == null || !t.visible() || inv.hotbarSlot(ItemKind.LAVA_BUCKET) < 0 || !inRange(c, t)) {
			return 0;
		}
		double skill = c.profile.items().uhcSkill();
		if (t.inWeb() && !t.inWater()) {
			// Webbed: they can't step out of it. Any player who has the lava goes for it.
			return willLava.get(c.rng, skill, 10) ? Scores.SPECIALIST + 0.1 : 0;
		}
		// Between spams a pause: shorter the better the player.
		if (c.observation.tick() - lastSpam < cooldown(skill) || !t.onGround() || t.inWater()) {
			return 0;
		}
		return willLava.get(c.rng, skill, 40) ? Scores.SPECIALIST + 0.03 : 0;
	}

	private static boolean inRange(BrainContext c, TargetState t) {
		double h = horizontal(c.self.position(), t.position());
		return h >= MIN_RANGE && h <= MAX_RANGE;
	}

	/** Ticks between lava spams: 2 s for a pro, 6 s for a beginner. */
	private static int cooldown(double skill) {
		return (int) Math.round(120 - 80 * skill);
	}

	@Override
	public void onEnter(BrainContext c) {
		if (phase == Phase.IDLE) {
			double skill = c.profile.items().uhcSkill();
			TargetState t = c.seen();
			boolean webbed = t != null && t.inWeb();
			phase = Phase.PLACE;
			ticks = 0;
			pours = 0;
			poursPlanned = 1 + (int) Math.round(skill * (webbed ? 3 : 2));
			bucketSlot = c.self.inventory().hotbarSlot(ItemKind.LAVA_BUCKET);
		}
	}

	@Override
	public void reset() {
		phase = Phase.IDLE;
		willLava.reset();
		lava = null;
		lastSpam = Long.MIN_VALUE / 2;
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
		lastActed = c.observation.tick();
		ticks++;
		if (bucketSlot < 0 || bucketSlot >= InventoryState.HOTBAR_SIZE) {
			return finish(c);
		}
		switch (phase) {
			case PLACE -> {
				if (inv.slot(bucketSlot).is(ItemKind.BUCKET) && lava != null) {
					// The bucket emptied: the lava is down. Leave it a moment, then take it back.
					phase = Phase.HOLD;
					ticks = 0;
					double skill = c.profile.items().uhcSkill();
					boolean webbed = t != null && t.inWeb();
					holdTicks = (int) Math.round((webbed ? 14 : 7) - 4 * skill) + c.rng.nextInt(0, 2);
					return aimAtLava(c, inv);
				}
				if (t == null || ticks > PLACE_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.LAVA_BUCKET) || !inRange(c, t)) {
					return finish(c);
				}
				// On a webbed opponent the bucket is emptied onto the web: the lava lands above it, on their head.
				BlockSpot ground = BlockPlay.groundUnder(t.position());
				BlockSpot clicked = t.inWeb() ? new BlockSpot(ground.x(), ground.y() + 1, ground.z()) : ground;
				lava = new BlockSpot(clicked.x(), clicked.y() + 1, clicked.z());
				return BlockPlay.clickTop(c, clicked, 1.0, bucketSlot);
			}
			case HOLD -> {
				boolean left = t == null || lava.horizontalDistanceTo(t.position()) > LEFT_LAVA;
				if (ticks >= holdTicks || left) {
					phase = Phase.SCOOP;
					ticks = 0;
				}
				return aimAtLava(c, inv);
			}
			case SCOOP -> {
				if (inv.slot(bucketSlot).is(ItemKind.LAVA_BUCKET)) {
					pours++;
					lava = null;
					if (pours < poursPlanned && t != null && t.visible() && inRange(c, t)) {
						// Again, wherever they are now.
						phase = Phase.PLACE;
						ticks = 0;
						return BlockPlay.position(c);
					}
					return finish(c);
				}
				if (ticks > SCOOP_TIMEOUT || !inv.slot(bucketSlot).is(ItemKind.BUCKET)) {
					// Gone (water turned it to stone, or the opponent scooped it): nothing left to take back.
					return finish(c);
				}
				Inputs aim = aimAtLava(c, inv);
				Vec3 eye = self.eyePosition();
				boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()), new Vec3(lava.x(), lava.y(), lava.z()),
					new Vec3(lava.x() + 1, lava.y() + 0.9, lava.z() + 1), BlockPlay.BLOCK_REACH);
				boolean inReach = lava.horizontalDistanceTo(eye) <= SCOOP_REACH;
				Inputs move = aim.withUse(aimed && inReach && inv.selectedSlot() == bucketSlot);
				return inReach ? move : Movement.guardEdges(c, towards(c, move, lava));
			}
			default -> {
				return Inputs.IDLE;
			}
		}
	}

	/** Keeps the empty bucket in hand and the crosshair on the lava, ready to scoop, at a safe distance from it. */
	private Inputs aimAtLava(BrainContext c, InventoryState inv) {
		Vec3 eye = c.self.eyePosition();
		Vec3 point = new Vec3(lava.x() + 0.5, lava.y() + 0.4, lava.z() + 0.5);
		float[] look = c.lookAt(Angles.yawTowards(eye, point), Angles.pitchTowards(eye, point));
		int press = c.memory.hands.request(c, bucketSlot);
		return awayFromLava(c, BlockPlay.spacing(c, look, press), KEEP_AWAY);
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

	private Inputs finish(BrainContext c) {
		phase = Phase.IDLE;
		lava = null;
		willLava.reset();
		lastSpam = c.observation.tick();
		return Inputs.IDLE;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x() - b.x();
		double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
