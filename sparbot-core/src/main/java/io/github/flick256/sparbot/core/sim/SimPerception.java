package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.Surroundings;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.List;

/**
 * What one simulated fighter knows, built the way the mod's Perception builds it in game: the opponent
 * only while in line of sight (else the last thing seen, with a rough idea of where they went when they
 * are close enough to be heard), and water and lava sources within block reach when it carries a bucket
 * or blocks.
 */
final class SimPerception {
	private static final int BLOCK_SCAN = 5;
	private static final double HEARING_RANGE = 16.0;
	private static final int HEARING_INTERVAL_TICKS = 10;

	private TargetState lastSeen;
	private Vec3 lastSeenPosition;
	private int ticksSinceSeen;
	private boolean seenLastTick;

	Observation observe(long tick, SimFighter self, SimFighter other, int otherId) {
		return new Observation(tick, self.selfState(), target(self, other, otherId), surroundings(self));
	}

	/** Perception#targetState for an assigned opponent (a duel: never forgotten). */
	private TargetState target(SimFighter self, SimFighter other, int otherId) {
		boolean visible = self.world.lineOfSight(self.eye(), other.eye());
		if (visible) {
			TargetState now = other.asTarget(otherId);
			// An onlooker's velocity: the change in position since the last tick it was in sight.
			Vec3 velocity = lastSeenPosition == null || !seenLastTick ? Vec3.ZERO : now.position().subtract(lastSeenPosition);
			lastSeen = new TargetState(now.entityId(), now.name(), now.position(), velocity, now.yaw(), now.health(), now.maxHealth(), now.onGround(),
				now.hurtTime(), now.blocking(), true, 0, now.halfWidth(), now.height(), now.mainHand(), now.offhand(), now.usingKind(),
				now.armorPoints(), now.onFire(), now.inWeb(), now.inWater(), now.ticksSinceSwing());
			lastSeenPosition = now.position();
			ticksSinceSeen = 0;
			seenLastTick = true;
			return lastSeen;
		}
		seenLastTick = false;
		if (lastSeen == null) {
			return null;
		}
		ticksSinceSeen++;
		if (ticksSinceSeen % HEARING_INTERVAL_TICKS == 0 && self.position().distanceTo(other.position()) <= HEARING_RANGE) {
			lastSeen = new TargetState(lastSeen.entityId(), lastSeen.name(), other.position(), Vec3.ZERO, lastSeen.yaw(), lastSeen.health(),
				lastSeen.maxHealth(), lastSeen.onGround(), 0, false, false, ticksSinceSeen, lastSeen.halfWidth(), lastSeen.height(), lastSeen.mainHand(),
				lastSeen.offhand(), ItemKind.EMPTY, lastSeen.armorPoints());
		}
		return new TargetState(lastSeen.entityId(), lastSeen.name(), lastSeen.position(), Vec3.ZERO, lastSeen.yaw(), lastSeen.health(),
			lastSeen.maxHealth(), lastSeen.onGround(), 0, false, false, ticksSinceSeen, lastSeen.halfWidth(), lastSeen.height(), lastSeen.mainHand(),
			lastSeen.offhand(), ItemKind.EMPTY, lastSeen.armorPoints());
	}

	/** Perception#surroundings / #fluidSources: source blocks within block reach, when it has something to use on them. */
	private static Surroundings surroundings(SimFighter self) {
		boolean useful = false;
		for (SimStack s : self.slots) {
			if (s != null && (s.kind() == ItemKind.BUCKET || s.kind() == ItemKind.WATER_BUCKET || s.kind() == ItemKind.LAVA_BUCKET
				|| s.kind() == ItemKind.BLOCK || s.kind() == ItemKind.COBWEB)) {
				useful = true;
				break;
			}
		}
		if (!useful) {
			return Surroundings.EMPTY;
		}
		int fx = (int) Math.floor(self.x);
		int fy = (int) Math.floor(self.y + 1.0E-7);
		int fz = (int) Math.floor(self.z);
		List<BlockSpot> water = new ArrayList<>();
		List<BlockSpot> lava = new ArrayList<>();
		List<BlockSpot> webs = new ArrayList<>();
		self.world.sources(SimWorld.WATER, fx - BLOCK_SCAN, fy - 3, fz - BLOCK_SCAN, fx + BLOCK_SCAN, fy + 2, fz + BLOCK_SCAN, water);
		self.world.sources(SimWorld.LAVA, fx - BLOCK_SCAN, fy - 3, fz - BLOCK_SCAN, fx + BLOCK_SCAN, fy + 2, fz + BLOCK_SCAN, lava);
		self.world.blocks(SimWorld.WEB, fx - BLOCK_SCAN, fy - 3, fz - BLOCK_SCAN, fx + BLOCK_SCAN, fy + 2, fz + BLOCK_SCAN, webs);
		Vec3 eye = self.eye();
		water.removeIf(b -> !withinReach(eye, b));
		lava.removeIf(b -> !withinReach(eye, b));
		webs.removeIf(b -> !withinReach(eye, b));
		if (water.isEmpty() && lava.isEmpty() && webs.isEmpty()) {
			return Surroundings.EMPTY;
		}
		return new Surroundings(List.of(), List.of(), List.of(), List.of(), List.of(), water, lava, webs);
	}

	/** Player#isWithinBlockInteractionRange(pos, 0): the eye within 4.5 of the block's box. */
	private static boolean withinReach(Vec3 eye, BlockSpot b) {
		double dx = Math.max(Math.max(b.x() - eye.x(), 0), eye.x() - (b.x() + 1));
		double dy = Math.max(Math.max(b.y() - eye.y(), 0), eye.y() - (b.y() + 1));
		double dz = Math.max(Math.max(b.z() - eye.z(), 0), eye.z() - (b.z() + 1));
		return dx * dx + dy * dy + dz * dz < SimFighter.BLOCK_REACH * SimFighter.BLOCK_REACH;
	}
}
