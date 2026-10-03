package io.github.flick256.sparbot.core.aim;

import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;

/**
 * Turns "where I want to look" into human-like mouse movement: exponential smoothing towards the
 * goal, hand jitter, and occasional overshoot on big flicks that is then corrected. The rotation
 * cap itself is enforced later by {@link io.github.flick256.sparbot.core.act.InputShaper}.
 */
public final class AimController {
	/** Flicks larger than this (degrees) may overshoot. */
	private static final float FLICK_THRESHOLD_DEG = 25.0F;

	private final SkillProfile.Aim aim;
	private final Rng rng;
	private float overshootYaw;
	private float overshootPitch;
	private boolean overshooting;

	public AimController(SkillProfile.Aim aim, Rng rng) {
		this.aim = aim;
		this.rng = rng;
	}

	/** Returns {yawDelta, pitchDelta} to move this tick from the current view towards the goal. */
	public float[] step(float currentYaw, float currentPitch, float goalYaw, float goalPitch) {
		float errorYaw = Angles.wrapDegrees(goalYaw - currentYaw);
		float errorPitch = goalPitch - currentPitch;
		float errorMagnitude = (float) Math.sqrt(errorYaw * errorYaw + errorPitch * errorPitch);

		if (!overshooting && errorMagnitude > FLICK_THRESHOLD_DEG && rng.chance(aim.overshootChance())) {
			overshooting = true;
			overshootYaw = (float) (errorYaw * aim.overshootFactor());
			overshootPitch = (float) (errorPitch * aim.overshootFactor());
		}
		if (overshooting) {
			// Aim past the goal until we cross it, then correct back as normal.
			errorYaw += overshootYaw;
			errorPitch += overshootPitch;
			if (Math.abs(Angles.wrapDegrees(goalYaw - currentYaw)) < Math.abs(overshootYaw) * 0.25F + 1.0F) {
				overshooting = false;
			}
		}

		float smoothing = (float) aim.smoothing();
		float jitter = (float) aim.jitterDeg();
		float yawDelta = errorYaw * smoothing + (float) rng.nextGaussian() * jitter;
		float pitchDelta = errorPitch * smoothing + (float) rng.nextGaussian() * jitter * 0.6F;
		return new float[] {yawDelta, pitchDelta};
	}
}
