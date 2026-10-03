package io.github.flick256.sparbot.core;

import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.Distribution;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/** Shared builders for core tests. */
public final class TestFixtures {
	private TestFixtures() {
	}

	public static SkillProfile preset(String id) {
		return SkillProfiles.loadPresets().get(id);
	}

	/** A profile with no randomness in mistakes, for deterministic assertions. */
	public static SkillProfile flawless(String baseId) {
		SkillProfile p = preset(baseId);
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(),
			new SkillProfile.Reach(Distribution.fixed(0)), p.technique(), 0.0, p.panicHealthFraction());
	}

	public static int[] flatGround() {
		return new int[SelfState.DIRECTIONS];
	}

	public static SelfState self(Vec3 pos, float yaw, float pitch, float health, float attackStrength, boolean onGround, int[] drops) {
		return new SelfState(pos, pos.add(new Vec3(0, 1.62, 0)), Vec3.ZERO, yaw, pitch, health, 20, 0, 20, onGround, false, false, false,
			0, attackStrength, 3.0, 0, true, drops);
	}

	public static TargetState target(Vec3 pos, int hurtTime) {
		return new TargetState(7, "Steve", pos, Vec3.ZERO, 0, 20, 20, true, hurtTime, false, true, 0, 0.3, 1.8);
	}
}
