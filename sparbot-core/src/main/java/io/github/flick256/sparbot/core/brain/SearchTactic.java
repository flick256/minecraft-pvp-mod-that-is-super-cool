package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Fallback when the opponent is out of sight: walk to where they were last seen, then turn on the spot
 * looking for them. With no opponent at all, stand still and glance around.
 */
public final class SearchTactic implements Tactic {
	/** Close enough to the last-seen spot to start looking around. */
	private static final double ARRIVED = 1.5;
	private static final float LOOK_AROUND_DEG_PER_TICK = 9.0F;

	@Override
	public String name() {
		return "search";
	}

	@Override
	public double score(BrainContext c) {
		return 0.1;
	}

	@Override
	public Inputs act(BrainContext c) {
		TargetState last = c.target;
		if (last == null) {
			float yaw = c.rng.chance(0.03) ? (float) c.rng.gaussian(0, 40) : 0.0F;
			float pitch = -c.self.pitch() * 0.1F;
			return new Inputs(yaw, pitch, 0, 0, false, false, false, false, false, -1);
		}
		Vec3 here = c.self.position();
		double dx = last.position().x() - here.x();
		double dz = last.position().z() - here.z();
		if (Math.sqrt(dx * dx + dz * dz) < ARRIVED) {
			return new Inputs(LOOK_AROUND_DEG_PER_TICK, -c.self.pitch() * 0.2F, 0, 0, false, false, false, false, false, -1);
		}
		Vec3 eye = c.self.eyePosition();
		float[] look = c.lookAt(Angles.yawTowards(eye, last.chest()), Angles.pitchTowards(eye, last.chest()));
		return Movement.navigate(c, new Inputs(look[0], look[1], 1, 0, false, false, true, false, false, -1));
	}
}
