package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.aim.AimController;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.Surroundings;
import io.github.flick256.sparbot.core.sense.TargetState;
import io.github.flick256.sparbot.core.style.Playstyle;

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
	/** The playstyle the bot fights with (weights are already applied to scores by the brain). */
	public final Playstyle style;
	/** Crystals and placeable blocks around the bot (empty unless it carries crystals). */
	public final Surroundings world;
	/** Techniques switched off for this bot. */
	public final java.util.Set<Technique> disabled;

	BrainContext(Observation observation, TargetState tracked, int trackingDelayTicks, SkillProfile profile, Playstyle style, Rng rng, AimController aim,
		DuelMemory memory) {
		this(observation, tracked, trackingDelayTicks, profile, style, rng, aim, memory, java.util.Set.of());
	}

	BrainContext(Observation observation, TargetState tracked, int trackingDelayTicks, SkillProfile profile, Playstyle style, Rng rng, AimController aim,
		DuelMemory memory, java.util.Set<Technique> disabled) {
		this.disabled = disabled;
		this.style = style;
		this.observation = observation;
		this.world = observation.surroundings();
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

	/**
	 * Whether a click now would hit the opponent: the crosshair is on them within {@code maxDistance}
	 * and no end crystal or TNT minecart is in front of them (the click would hit that instead).
	 */
	public boolean clickHitsTarget(double maxDistance) {
		TargetState t = seen();
		if (t == null) {
			return false;
		}
		Vec3 eye = self.eyePosition();
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		double entry = rayEntry(eye, dir, new Vec3(t.position().x() - t.halfWidth(), t.position().y(), t.position().z() - t.halfWidth()),
			new Vec3(t.position().x() + t.halfWidth(), t.position().y() + t.height(), t.position().z() + t.halfWidth()), maxDistance);
		if (entry < 0) {
			return false;
		}
		for (Vec3 p : world.crystals()) {
			if (rayHitsBox(eye, dir, new Vec3(p.x() - 1.0, p.y(), p.z() - 1.0), new Vec3(p.x() + 1.0, p.y() + 2.0, p.z() + 1.0), entry)) {
				return false;
			}
		}
		for (Vec3 p : world.tntCarts()) {
			if (rayHitsBox(eye, dir, new Vec3(p.x() - 0.49, p.y(), p.z() - 0.49), new Vec3(p.x() + 0.49, p.y() + 0.7, p.z() + 0.49), entry)) {
				return false;
			}
		}
		return true;
	}

	/** Mouse movement this tick towards looking at the given angles (smoothed, jittered by the aim model). */
	public float[] lookAt(float goalYaw, float goalPitch) {
		return aim.step(self.yaw(), self.pitch(), goalYaw, goalPitch);
	}

	/** Angular distance (degrees) between where the bot looks and the given angles. */
	public float aimError(float goalYaw, float goalPitch) {
		float dy = Angles.wrapDegrees(goalYaw - self.yaw());
		float dp = goalPitch - self.pitch();
		return (float) Math.sqrt(dy * dy + dp * dp);
	}

	public boolean allows(Technique technique) {
		return !disabled.contains(technique);
	}

	/** How often and how well the bot uses a technique: the profile's skill, or 0 when it is switched off. */
	public double skill(Technique technique) {
		return allows(technique) ? technique.skill(profile) : 0;
	}

	public boolean mistake(Mistake kind) {
		return memory.mistake == kind;
	}

	static boolean rayHitsBox(Vec3 origin, Vec3 dir, Vec3 min, Vec3 max, double maxDistance) {
		return rayEntry(origin, dir, min, max, maxDistance) >= 0;
	}

	/** Distance along the ray at which it enters the box (0 if it starts inside), or -1 if it misses within {@code maxDistance}. */
	static double rayEntry(Vec3 origin, Vec3 dir, Vec3 min, Vec3 max, double maxDistance) {
		double tMin = 0;
		double tMax = maxDistance;
		double[] o = {origin.x(), origin.y(), origin.z()};
		double[] d = {dir.x(), dir.y(), dir.z()};
		double[] lo = {min.x(), min.y(), min.z()};
		double[] hi = {max.x(), max.y(), max.z()};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(d[axis]) < 1e-9) {
				if (o[axis] < lo[axis] || o[axis] > hi[axis]) {
					return -1;
				}
			} else {
				double t1 = (lo[axis] - o[axis]) / d[axis];
				double t2 = (hi[axis] - o[axis]) / d[axis];
				tMin = Math.max(tMin, Math.min(t1, t2));
				tMax = Math.min(tMax, Math.max(t1, t2));
				if (tMin > tMax) {
					return -1;
				}
			}
		}
		return tMin;
	}
}
