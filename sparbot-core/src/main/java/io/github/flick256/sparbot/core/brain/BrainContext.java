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
	/** The opponent as of one reaction time ago: used for decisions (what to do next). */
	public final TargetState target;
	/**
	 * The opponent as the eyes currently track it: only network and visual-motor delay. Humans
	 * follow a moving target continuously; reaction time delays decisions, not tracking.
	 */
	public final TargetState tracked;
	/** How many ticks old {@link #tracked} is, used to lead the aim. */
	public final int trackingDelayTicks;
	public final SkillProfile profile;
	public final Rng rng;
	public final AimController aim;
	public final DuelMemory memory;

	BrainContext(Observation observation, TargetState tracked, int trackingDelayTicks, SkillProfile profile, Rng rng, AimController aim, DuelMemory memory) {
		this.observation = observation;
		this.self = observation.self();
		this.target = observation.target();
		this.tracked = tracked;
		this.trackingDelayTicks = trackingDelayTicks;
		this.profile = profile;
		this.rng = rng;
		this.aim = aim;
		this.memory = memory;
	}

	/** The freshest view of the opponent the bot has (tracking view, else the decision view). */
	public TargetState seen() {
		return tracked != null ? tracked : target;
	}

	/** Distance from the bot's eyes to the target's hitbox, as the bot currently sees it. */
	public double targetDistance() {
		TargetState t = seen();
		return t == null ? Double.POSITIVE_INFINITY : t.hitboxDistance(self.eyePosition());
	}

	/** Whether the bot's crosshair ray, as it currently sees things, passes through the target's hitbox. */
	public boolean crosshairOnTarget(double maxDistance) {
		TargetState t = seen();
		if (t == null) {
			return false;
		}
		Vec3 eye = self.eyePosition();
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		Vec3 min = new Vec3(t.position().x() - t.halfWidth(), t.position().y(), t.position().z() - t.halfWidth());
		Vec3 max = new Vec3(t.position().x() + t.halfWidth(), t.position().y() + t.height(), t.position().z() + t.halfWidth());
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
