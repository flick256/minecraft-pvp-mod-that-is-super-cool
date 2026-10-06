package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Water and lava lying around, whoever put it there: with an empty bucket, scoop it up (its own water
 * back into its kit, the opponent's taken away from them, lava out of the way), unless that means
 * standing in the opponent's reach or walking into lava. Lava close by that it can't scoop gets blocked
 * up with a block instead. This is what catches water a pickup missed (knocked away, a block in the
 * way) and lava that turned to stone around a source.
 *
 * <p>At a human pace: a new source has to be noticed first (the bot's reaction time plus a moment to take
 * it in, shorter the more skilled it is: about 0.45 s for a Demon, 0.7 s for an advanced player), and after
 * each scoop it takes a moment before the next (0.5 s for a Demon, about 1 s for an advanced player), so
 * a run of the opponent's water and lava isn't drained near instantly. Lava right at its feet skips the
 * pause between scoops, but still has to be noticed.
 */
public final class CleanupTactic implements Tactic {
	/** Scoop from no farther than this (eye to the source block's centre, horizontally). */
	private static final double REACH = 4.0;
	/** Lava this close (horizontally) gets blocked up if it can't be scooped. */
	private static final double LAVA_THREAT = 3.0;
	/** Closer than this the opponent can hit the bot while it scoops. */
	private static final double UNSAFE = 3.2;
	private static final int TIMEOUT = 20;
	/** A spot that couldn't be cleaned up is left alone this long. */
	private static final int GIVE_UP_TICKS = 200;

	private final Map<BlockSpot, Long> givenUp = new HashMap<>();
	/** When each source lying around was first seen. */
	private final Map<BlockSpot, Long> firstSeen = new HashMap<>();
	private long lastScoop = Long.MIN_VALUE / 2;
	/** Lava this close (horizontally) is dealt with without the pause between scoops. */
	private static final double AT_FEET = 1.6;
	private BlockSpot spot;
	private boolean lava;
	private boolean blockUp;
	private int ticks;
	private int bucketsBefore;

	@Override
	public String name() {
		return "cleanup";
	}

	@Override
	public String detail() {
		return spot == null ? "" : (blockUp ? "block lava" : lava ? "scoop lava" : "scoop water");
	}

	/** Ticks a new source must have been in sight before the bot reacts to it. */
	static int noticeTicks(BrainContext c) {
		return c.memory.reactionDelayTicks + (int) Math.round(6 + 10 * (1 - c.profile.items().uhcSkill()));
	}

	/** Ticks between one scoop and the next. */
	static int pauseTicks(BrainContext c) {
		return (int) Math.round(10 + 20 * (1 - c.profile.items().uhcSkill()));
	}

	private void see(BrainContext c) {
		long now = c.observation.tick();
		java.util.Set<BlockSpot> present = new java.util.HashSet<>(c.world.waterSources());
		present.addAll(c.world.lavaSources());
		firstSeen.keySet().retainAll(present);
		for (BlockSpot s : present) {
			firstSeen.putIfAbsent(s, now);
		}
	}

	/** Noticed long enough ago, and (unless it is lava at its feet) the pause since the last scoop is over. */
	private boolean ready(BrainContext c, BlockSpot s, boolean isLava) {
		long now = c.observation.tick();
		if (now - firstSeen.getOrDefault(s, now) < noticeTicks(c)) {
			return false;
		}
		boolean atFeet = isLava && s.horizontalDistanceTo(c.self.position()) < AT_FEET;
		return atFeet || now - lastScoop >= pauseTicks(c);
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		see(c);
		givenUp.values().removeIf(t -> c.observation.tick() - t > GIVE_UP_TICKS);
		if (spot != null) {
			return Scores.SPECIALIST + 0.04;
		}
		if (unsafe(c)) {
			return 0;
		}
		return choose(c, inv) ? Scores.SPECIALIST + 0.04 : 0;
	}

	/** Not with the opponent in reach, and not at low health with them close. */
	private static boolean unsafe(BrainContext c) {
		TargetState t = c.seen();
		if (t == null || !t.visible()) {
			return false;
		}
		double d = c.targetDistance();
		return d < UNSAFE || c.self.healthFraction() < 0.3 && d < 6;
	}

	/** Picks what to clean up; returns whether there is anything. */
	private boolean choose(BrainContext c, InventoryState inv) {
		boolean emptyBucket = inv.hotbarSlot(ItemKind.BUCKET) >= 0;
		Vec3 eye = c.self.eyePosition();
		if (emptyBucket) {
			Optional<BlockSpot> water = c.world.waterSources().stream().filter(s -> ok(c, s, eye) && !standingIn(c.self, s) && ready(c, s, false))
				.min(java.util.Comparator.comparingDouble(s -> s.horizontalDistanceTo(eye)));
			Optional<BlockSpot> lavaSpot = c.world.lavaSources().stream().filter(s -> ok(c, s, eye) && ready(c, s, true))
				.min(java.util.Comparator.comparingDouble(s -> s.horizontalDistanceTo(eye)));
			// Lava next to the bot first, then water (a bucket of water back), then other lava.
			if (lavaSpot.isPresent() && lavaSpot.get().horizontalDistanceTo(c.self.position()) < LAVA_THREAT) {
				return start(c, lavaSpot.get(), true, false);
			}
			if (water.isPresent()) {
				return start(c, water.get(), false, false);
			}
			if (lavaSpot.isPresent()) {
				return start(c, lavaSpot.get(), true, false);
			}
		}
		if (blockSlot(inv) >= 0) {
			Optional<BlockSpot> near = c.world.lavaSources().stream()
				.filter(s -> s.horizontalDistanceTo(c.self.position()) < LAVA_THREAT && !givenUp.containsKey(s) && ready(c, s, true))
				.min(java.util.Comparator.comparingDouble(s -> s.horizontalDistanceTo(c.self.position())));
			if (near.isPresent()) {
				return start(c, near.get(), true, true);
			}
		}
		return false;
	}

	private boolean ok(BrainContext c, BlockSpot s, Vec3 eye) {
		return !givenUp.containsKey(s) && s.horizontalDistanceTo(eye) <= REACH + 1.5;
	}

	private static boolean standingIn(SelfState self, BlockSpot s) {
		// Its own water while it is still burning or webbed belongs to the water tactic.
		return (self.onFire() || self.inWeb()) && s.horizontalDistanceTo(self.position()) < 1.0;
	}

	private boolean start(BrainContext c, BlockSpot s, boolean isLava, boolean block) {
		spot = s;
		lava = isLava;
		blockUp = block;
		ticks = 0;
		bucketsBefore = filled(c.self.inventory());
		return true;
	}

	@Override
	public void reset() {
		spot = null;
		givenUp.clear();
		firstSeen.clear();
		lastScoop = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (spot == null && !choose(c, inv)) {
			return Inputs.IDLE;
		}
		boolean stillThere = (lava ? c.world.lavaSources() : c.world.waterSources()).contains(spot);
		if (!stillThere || filled(inv) > bucketsBefore) {
			lastScoop = c.observation.tick();
			return done();
		}
		if (++ticks > TIMEOUT || unsafe(c)) {
			givenUp.put(spot, c.observation.tick());
			return done();
		}
		if (blockUp) {
			int slot = blockSlot(inv);
			if (slot < 0) {
				return done();
			}
			// A block placed into the lava's space replaces it (lava is replaceable).
			return BlockPlay.clickTop(c, new BlockSpot(spot.x(), spot.y() - 1, spot.z()), 1.0, slot);
		}
		int bucket = inv.hotbarSlot(ItemKind.BUCKET);
		if (bucket < 0) {
			return done();
		}
		SelfState self = c.self;
		Vec3 eye = self.eyePosition();
		Vec3 point = new Vec3(spot.x() + 0.5, spot.y() + 0.4, spot.z() + 0.5);
		float[] look = c.lookAt(Angles.yawTowards(eye, point), Angles.pitchTowards(eye, point));
		int press = c.memory.hands.request(c, bucket);
		boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()), new Vec3(spot.x(), spot.y(), spot.z()),
			new Vec3(spot.x() + 1, spot.y() + 0.9, spot.z() + 1), BlockPlay.BLOCK_REACH);
		boolean inReach = spot.horizontalDistanceTo(eye) <= REACH;
		Inputs in = new Inputs(look[0], look[1], 0, 0, false, false, false, false, aimed && inReach && inv.selectedSlot() == bucket, press);
		if (!inReach) {
			float rel = Angles.wrapDegrees(Angles.yawTowards(self.position(), point) - (self.yaw() + look[0]));
			in = Movement.navigate(c, in.withMovement(Math.abs(rel) < 90 ? 1 : -1, 0));
		}
		return in;
	}

	private Inputs done() {
		spot = null;
		return Inputs.IDLE;
	}

	private static int blockSlot(InventoryState inv) {
		return inv.hotbarSlot(WallTactic::wallBlock);
	}

	/** Filled buckets in the whole inventory (a scoop turns an empty bucket into a full one). */
	private static int filled(InventoryState inv) {
		return inv.count(ItemKind.WATER_BUCKET) + inv.count(ItemKind.LAVA_BUCKET);
	}
}
