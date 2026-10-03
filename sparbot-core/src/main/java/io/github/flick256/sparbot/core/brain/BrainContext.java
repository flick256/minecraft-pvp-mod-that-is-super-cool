package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.aim.AimController;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/** Per-tick view shared by all tactics: the (delayed) observation plus the brain's persistent memory. */
public final class BrainContext {
	public final Observation observation;
	public final SelfState self;
	public final TargetState target;
	public final SkillProfile profile;
	public final Rng rng;
	public final AimController aim;
	public final DuelMemory memory;

	BrainContext(Observation observation, SkillProfile profile, Rng rng, AimController aim, DuelMemory memory) {
		this.observation = observation;
		this.self = observation.self();
		this.target = observation.target();
		this.profile = profile;
		this.rng = rng;
		this.aim = aim;
		this.memory = memory;
	}

	/** Distance from the bot's eyes to the target's hitbox, as the bot perceives it. */
	public double targetDistance() {
		return target == null ? Double.POSITIVE_INFINITY : target.hitboxDistance(self.eyePosition());
	}

	/** Whether the bot's crosshair ray, as it perceives things, passes through the target's hitbox. */
	public boolean crosshairOnTarget(double maxDistance) {
		if (target == null) {
			return false;
		}
		Vec3 eye = self.eyePosition();
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		Vec3 min = new Vec3(target.position().x() - target.halfWidth(), target.position().y(), target.position().z() - target.halfWidth());
		Vec3 max = new Vec3(target.position().x() + target.halfWidth(), target.position().y() + target.height(), target.position().z() + target.halfWidth());
		return rayHitsBox(eye, dir, min, max, maxDistance);
	}

	public boolean mistake(Mistake kind) {
		return memory.mistake == kind;
	}

	static boolean rayHitsBox(Vec3 origin, Vec3 dir, Vec3 min, Vec3 max, double maxDistance) {
		double tMin = 0;
		double tMax = maxDistance;
		double[] o = {origin.x(), origin.y(), origin.z()};
		double[] d = {dir.x(), dir.y(), dir.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1e-9) {
				if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
					return false;
				}
			} else {
				double t1 = (lo[axis] - o[axis]) / d[axis];
				double t2 = (hi[axis] - o[axis]) / d[axis];
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
				if (tMin > tMax) {
					return false;
				}
			}
		}
		return true;
	}
}
