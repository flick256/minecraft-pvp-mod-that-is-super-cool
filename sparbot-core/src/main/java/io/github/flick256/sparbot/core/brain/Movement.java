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

	/**
	 * Removes movement keys that would walk the bot off a dangerous drop, preferring to keep as much
	 * of the intended movement as possible. Skipped while the bot is making an {@link Mistake#EDGE_BLIND} mistake.
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
		if (in.strafe() != 0 && isSafe(self, yaw, in.forward(), 0)) {
			return in.withMovement(in.forward(), 0);
		}
		if (in.forward() != 0 && isSafe(self, yaw, 0, in.strafe())) {
			return in.withMovement(0, in.strafe()).withSprint(false);
		}
		return in.withMovement(0, 0).withSprint(false).withJump(false);
	}
}
