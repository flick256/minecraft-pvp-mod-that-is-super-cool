package io.github.flick256.sparbot.core.act;

/**
 * Everything the bot may do in one tick, expressed purely as human inputs: mouse movement and key /
 * button state. There is deliberately no "teleport", "set health" or "hit entity X" command: attacks
 * are left clicks resolved by the same crosshair raycast a vanilla client performs.
 *
 * @param forward +1 forward key, -1 back key, 0 neither
 * @param strafe +1 left key, -1 right key, 0 neither (vanilla's leftImpulse convention)
 * @param hotbarSlot 0-8 to scroll to that slot, -1 to keep the current one
 */
public record Inputs(
	float yawDelta,
	float pitchDelta,
	int forward,
	int strafe,
	boolean jump,
	boolean sneak,
	boolean sprint,
	boolean attack,
	boolean use,
	int hotbarSlot
) {
	public static final Inputs IDLE = new Inputs(0, 0, 0, 0, false, false, false, false, false, -1);

	public Inputs withRotation(float yaw, float pitch) {
		return new Inputs(yaw, pitch, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot);
	}

	public Inputs withAttack(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, value, use, hotbarSlot);
	}

	public Inputs withMovement(int newForward, int newStrafe) {
		return new Inputs(yawDelta, pitchDelta, newForward, newStrafe, jump, sneak, sprint, attack, use, hotbarSlot);
	}

	public Inputs withJump(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, value, sneak, sprint, attack, use, hotbarSlot);
	}

	public Inputs withSprint(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, value, attack, use, hotbarSlot);
	}
}
