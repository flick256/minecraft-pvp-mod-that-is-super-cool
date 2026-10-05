package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;

/** Movement helpers shared by tactics. */
public final class Movement {
	/** Drops of this many blocks or more are avoided (vanilla fall damage starts above 3 blocks). */
	public static final int DANGEROUS_DROP = 4;

	private Movement() {
	}

	/**
	 * World yaw the bot would move towards with these keys, using the same rotation as vanilla's
	 * Entity#getInputVector. Returns NaN when no movement key is pressed.
	 */
	public static float movementYaw(float yaw, int forward, int strafe) {
		if (forward == 0 && strafe == 0) {
			return Float.NaN;
		}
		double sin = Math.sin(Math.toRadians(yaw));
		double cos = Math.cos(Math.toRadians(yaw));
		double worldX = strafe * cos - forward * sin;
		double worldZ = forward * cos + strafe * sin;
		return Angles.yawTowards(Vec3.ZERO, new Vec3(worldX, 0, worldZ));
	}

	/** Index into {@link SelfState#dropDepth()} for a world yaw. */
	public static int directionIndex(float worldYaw) {
		int index = Math.round(Angles.wrapDegrees(worldYaw) / 45.0F);
		return Math.floorMod(index, SelfState.DIRECTIONS);
	}

	public static boolean isSafe(SelfState self, float yaw, int forward, int strafe) {
		float moveYaw = movementYaw(yaw, forward, strafe);
		if (Float.isNaN(moveYaw) || self.dropDepth() == null) {
			return true;
		}
		return self.dropDepth()[directionIndex(moveYaw)] < DANGEROUS_DROP;
	}

	/** Walking into a wall this long (ticks) means it is too high to jump: go around it. */
	private static final int BLOCKED_BEFORE_DETOUR = 4;
	/** Sidestepping one way this long without getting past means try the other way. */
	private static final int GIVE_UP_DIRECTION = 40;
	private static final int CLEAR_CORNER_TICKS = 6;

	/**
	 * Walking towards something on the other side of a wall (an opponent behind obsidian, say): jump
	 * one-block steps, and when the wall is too high, sidestep along it until past its corner, trying the
	 * other way if one side goes on too long.
	 */
	public static Inputs navigate(BrainContext context, Inputs in) {
		DuelMemory m = context.memory;
		SelfState self = context.self;
		if (in.forward() > 0 && self.horizontalCollision()) {
			m.blockedTicks++;
			if (self.onGround()) {
				in = in.withJump(true);
			}
			if (m.blockedTicks > GIVE_UP_DIRECTION) {
				m.detourStrafe = -m.detourStrafe;
				m.blockedTicks = BLOCKED_BEFORE_DETOUR + 1;
			}
			if (m.blockedTicks > BLOCKED_BEFORE_DETOUR) {
				m.detourTicks = CLEAR_CORNER_TICKS;
			}
		} else if (m.blockedTicks > 0 && m.detourTicks == 0) {
			m.blockedTicks = 0;
		}
		if (m.detourTicks > 0) {
			m.detourTicks--;
			in = in.withMovement(in.forward(), m.detourStrafe);
		}
		return guardEdges(context, in);
	}

	/** A detour may turn this far from the intended direction: enough to walk around a hazard, not back off. */
	private static final float MAX_DETOUR = 100.0F;

	/**
	 * Replaces movement keys that would walk the bot off a dangerous drop or into lava with the safe
	 * keys closest to where it meant to go, so it walks around a lava pool instead of freezing in front
	 * of it. Stops only when every way forward is unsafe. Skipped while the bot is making an
	 * {@link Mistake#EDGE_BLIND} mistake.
	 */
	public static Inputs guardEdges(BrainContext context, Inputs in) {
		if (context.mistake(Mistake.EDGE_BLIND) || (in.forward() == 0 && in.strafe() == 0)) {
			return in;
		}
		SelfState self = context.self;
		float yaw = self.yaw() + in.yawDelta();
		if (isSafe(self, yaw, in.forward(), in.strafe())) {
			return in;
		}
		float intended = movementYaw(yaw, in.forward(), in.strafe());
		int bestForward = 0;
		int bestStrafe = 0;
		float bestTurn = MAX_DETOUR;
		for (int forward = -1; forward <= 1; forward++) {
			for (int strafe = -1; strafe <= 1; strafe++) {
				if ((forward != 0 || strafe != 0) && isSafe(self, yaw, forward, strafe)) {
					float turn = Angles.yawDistance(movementYaw(yaw, forward, strafe), intended);
					if (turn < bestTurn) {
						bestTurn = turn;
						bestForward = forward;
						bestStrafe = strafe;
					}
				}
			}
		}
		if (bestForward == 0 && bestStrafe == 0) {
			return in.withMovement(0, 0).withSprint(false).withJump(false);
		}
		Inputs detour = in.withMovement(bestForward, bestStrafe);
		return bestForward > 0 ? detour : detour.withSprint(false);
	}

	/** Sprints away from {@code danger} (only its horizontal position counts) while looking at {@code facing}. */
	static Inputs awayFrom(BrainContext c, Vec3 danger, Vec3 facing) {
		SelfState self = c.self;
		Vec3 eye = self.eyePosition();
		float[] look = c.lookAt(Angles.yawTowards(eye, facing), Angles.pitchTowards(eye, facing));
		float away = Angles.yawTowards(new Vec3(danger.x(), self.position().y(), danger.z()), self.position());
		float rel = Angles.wrapDegrees(away - (self.yaw() + look[0]));
		int forward = Math.abs(rel) < 67.5F ? 1 : Math.abs(rel) > 112.5F ? -1 : 0;
		int strafe = rel < -22.5F && rel > -157.5F ? 1 : rel > 22.5F && rel < 157.5F ? -1 : 0;
		return guardEdges(c, new Inputs(look[0], look[1], forward, strafe, false, false, forward > 0, false, false, -1));
	}
}
