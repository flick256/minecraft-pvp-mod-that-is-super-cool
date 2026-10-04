package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Explosions;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;

/** What the block-placing modes (crystals, carts) share: clicking block tops, spacing, judging a blast. */
final class BlockPlay {
	/** Vanilla block_interaction_range. */
	static final double BLOCK_REACH = 4.5;
	/** Rails are 2/16 of a block tall. */
	static final double RAIL_HEIGHT = 0.125;
	/** Something placed next to the opponent: centre within this horizontal distance of their feet. */
	static final double NEAR_TARGET = 2.6;
	/** Aim this far inside a block face's edge so aim jitter stays on the face. */
	private static final double FACE_INSET = 0.15;
	private static final double TOO_CLOSE = 2.5;
	private static final double TOO_FAR = 4.5;

	private BlockPlay() {
	}

	/**
	 * Holds {@code slot}, looks at the top of {@code block} ({@code height} tall) and right-clicks once
	 * the crosshair is on it. It aims at the part of the top nearest to it, which may be in reach when
	 * the centre isn't.
	 */
	static Inputs clickTop(BrainContext c, BlockSpot block, double height, int slot) {
		SelfState self = c.self;
		Vec3 eye = self.eyePosition();
		double top = block.y() + height;
		Vec3 point = new Vec3(Math.max(block.x() + FACE_INSET, Math.min(eye.x(), block.x() + 1 - FACE_INSET)), top,
			Math.max(block.z() + FACE_INSET, Math.min(eye.z(), block.z() + 1 - FACE_INSET)));
		float[] look = c.lookAt(Angles.yawTowards(eye, point), Angles.pitchTowards(eye, point));
		int press = c.memory.hands.request(c, slot);
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		double entry = BrainContext.rayEntry(eye, dir, new Vec3(block.x(), block.y(), block.z()), new Vec3(block.x() + 1, top, block.z() + 1),
			BLOCK_REACH);
		boolean onTop = entry >= 0 && eye.y() + dir.y() * entry >= top - 1e-3;
		// The highlighted block must be this one: a wall or another block in front would take the click.
		boolean highlighted = self.crosshairBlockDistance() >= entry - 0.05;
		boolean click = onTop && highlighted && self.inventory().selectedSlot() == slot && !self.inventory().usingItem();
		return spacing(c, look, press).withUse(click);
	}

	/** The block someone standing at {@code feet} stands on. */
	static BlockSpot groundUnder(Vec3 feet) {
		return new BlockSpot((int) Math.floor(feet.x()), (int) Math.floor(feet.y() - 1.0E-3), (int) Math.floor(feet.z()));
	}

	/**
	 * The ground block under the opponent; while they are in the air (a jump, a crit, knockback), the one
	 * under where they will come down, on the ground they last stood on. Clicking the block under an
	 * airborne opponent's feet clicks air.
	 */
	static BlockSpot groundBelow(BrainContext c, io.github.flick256.sparbot.core.sense.TargetState t) {
		if (t.onGround()) {
			return groundUnder(t.position());
		}
		double groundTop = Double.isNaN(c.memory.targetGroundY) ? Math.floor(c.self.position().y() + 1.0E-3) : c.memory.targetGroundY;
		if (t.position().y() < groundTop) {
			// Below the last ground they stood on: falling off it, onto something lower.
			groundTop = Math.floor(t.position().y());
		}
		Vec3 land = landing(t.position(), t.velocity(), groundTop);
		return new BlockSpot((int) Math.floor(land.x()), (int) Math.floor(groundTop - 1.0E-3), (int) Math.floor(land.z()));
	}

	/**
	 * Where a player in the air at {@code pos} moving at {@code velocity} (blocks per tick) touches down
	 * on ground whose top is at {@code groundTop}: vanilla's airborne physics, gravity 0.08 then drag
	 * 0.98 vertically and 0.91 horizontally per tick. Gives up after 40 ticks.
	 */
	static Vec3 landing(Vec3 pos, Vec3 velocity, double groundTop) {
		double x = pos.x();
		double y = pos.y();
		double z = pos.z();
		double vx = velocity.x();
		double vy = velocity.y();
		double vz = velocity.z();
		for (int t = 0; t < 40; t++) {
			x += vx;
			y += vy;
			z += vz;
			vy = (vy - 0.08) * 0.98;
			vx *= 0.91;
			vz *= 0.91;
			if (y <= groundTop && vy < 0) {
				break;
			}
		}
		return new Vec3(x, Math.max(y, groundTop), z);
	}

	/** Holds 2.5-4.5 blocks from the opponent, strafing a little, with the given look and hotbar press. */
	static Inputs spacing(BrainContext c, float[] look, int press) {
		double d = c.targetDistance();
		int forward = d > TOO_FAR ? 1 : d < TOO_CLOSE ? -1 : 0;
		DuelMemory m = c.memory;
		if (--m.ticksUntilStrafeSwitch <= 0) {
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 25);
			// Block-mode players keep moving while they hold their distance (standing still gets them hit).
			m.strafeActive = c.rng.chance(0.2 + c.skill(Technique.STRAFE) * 0.5);
		}
		int strafe = m.strafeActive ? m.strafeDirection : 0;
		boolean jump = c.self.horizontalCollision() && c.self.onGround() && forward > 0;
		return Movement.guardEdges(c, new Inputs(look[0], look[1], forward, strafe, jump, false, d > TOO_FAR + 2, false, false, press));
	}

	/** Spacing while looking at the opponent's chest. */
	static Inputs position(BrainContext c) {
		Vec3 eye = c.self.eyePosition();
		Vec3 chest = c.seen().chest();
		return spacing(c, c.lookAt(Angles.yawTowards(eye, chest), Angles.pitchTowards(eye, chest)), -1);
	}

	/**
	 * Whether a blast of {@code power} at {@code center} is worth setting off: it must hurt the
	 * opponent, and the bot's own share must stay under a limit that shrinks with {@code skill}. A
	 * skilled player only sets off blasts closer to the opponent than to themselves; a beginner blows
	 * themselves up.
	 */
	static boolean worth(BrainContext c, Vec3 center, double power, double skill) {
		double toTarget = Explosions.rawDamage(center, c.seen().position(), power);
		double toSelf = Explosions.rawDamage(center, c.self.position(), power);
		double maxSelfShare = 1.25 - 0.35 * skill;
		return toTarget >= 12.0 && toSelf <= toTarget * maxSelfShare;
	}
}
