package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * What a learned melee policy sees and how its outputs become keys. The inputs are exactly what the
 * scripted brain works from (the delayed view of the opponent, the bot's own charge and movement), put
 * in the frame of "towards the opponent" so left and right, near and far mean the same everywhere.
 */
public final class MeleeFeatures {
	/** Input vector length, version 1 (sword). */
	public static final int COUNT = 24;
	/** Output vector length, version 1: back/none/forward, right/none/left, jump, sprint, click. */
	public static final int OUTPUTS = 9;
	/** Version 2 (shields, axes and golden apples, for UHC): the version 1 inputs plus 8 about items. */
	public static final int COUNT_V2 = 32;
	/** Version 2 outputs: version 1's plus raise the shield and fight with the axe. */
	public static final int OUTPUTS_V2 = 11;
	private static final double CHARGE_TICKS = 12.5;

	private MeleeFeatures() {
	}

	/** The version 1 feature vector for this tick. Call after {@link SwordPlan#observe}. */
	static double[] encode(BrainContext c) {
		return encode(c, 1);
	}

	/** The feature vector of the given version (1 or 2) for this tick. Call after {@link SwordPlan#observe}. */
	static double[] encode(BrainContext c, int version) {
		SelfState s = c.self;
		TargetState t = c.seen();
		TargetState decided = c.target;
		DuelMemory m = c.memory;
		double[] f = new double[version >= 2 ? COUNT_V2 : COUNT];
		Vec3 to = t.position().subtract(s.position());
		double h = Math.sqrt(to.x() * to.x() + to.z() * to.z());
		double ux = h < 1e-6 ? 0 : to.x() / h;
		double uz = h < 1e-6 ? 1 : to.z() / h;
		Vec3 tv = t.velocity();
		Vec3 sv = m.selfMotion;
		double distance = c.targetDistance();
		double reach = s.attackReach() + m.reachError;
		f[0] = clamp(distance / 6.0);
		f[1] = clamp((tv.x() * ux + tv.z() * uz) * 5);
		f[2] = clamp((-tv.x() * uz + tv.z() * ux) * 5);
		f[3] = clamp((sv.x() * ux + sv.z() * uz) * 5);
		f[4] = clamp((-sv.x() * uz + sv.z() * ux) * 5);
		f[5] = s.attackStrength();
		f[6] = Math.min(1.0, SwordPlan.ticksToReady(c) / CHARGE_TICKS);
		f[7] = c.allows(Technique.READING) ? opponentCharge(decided) : 1.0;
		f[8] = s.onGround() ? 1 : -1;
		f[9] = s.sprinting() ? 1 : -1;
		f[10] = s.fallDistance() > 0 && s.velocity().y() < 0 ? 1 : -1;
		f[11] = clamp(s.velocity().y() * 5);
		f[12] = t.onGround() ? 1 : -1;
		f[13] = clamp(tv.y() * 5);
		f[14] = s.hurtTime() / 10.0;
		f[15] = decided == null ? 0 : decided.hurtTime() / 10.0;
		f[16] = s.health() / Math.max(1, s.maxHealth());
		f[17] = t.health() / Math.max(1, t.maxHealth());
		Vec3 theirLook = Angles.lookVector(t.yaw(), 0);
		f[18] = h < 1e-6 ? 0 : -(theirLook.x() * ux + theirLook.z() * uz);
		f[19] = c.clickHitsTarget(reach + 0.5) ? 1 : -1;
		f[20] = distance <= reach ? 1 : -1;
		f[21] = Math.min(1.0, m.sinceOwnHit / 20.0);
		f[22] = s.horizontalCollision() ? 1 : -1;
		f[23] = 1;
		if (version >= 2) {
			io.github.flick256.sparbot.core.item.InventoryState inv = s.inventory();
			f[24] = decided != null && decided.blocking() ? 1 : -1;
			f[25] = decided != null && decided.usingKind().isFood() ? 1 : -1;
			f[26] = t.mainHand() == io.github.flick256.sparbot.core.item.ItemKind.AXE ? 1 : -1;
			f[27] = inv.offhand().is(io.github.flick256.sparbot.core.item.ItemKind.SHIELD) && !inv.offhand().onCooldown() ? 1 : -1;
			f[28] = inv.blocking() ? 1 : -1;
			f[29] = Math.min(1.0, s.absorption() / 4.0);
			f[30] = m.shieldDisabledTicks > 0 ? 1 : -1;
			f[31] = inv.mainHand().is(io.github.flick256.sparbot.core.item.ItemKind.AXE) ? 1 : -1;
		}
		return f;
	}

	private static double opponentCharge(TargetState t) {
		if (t == null || t.ticksSinceSwing() >= TargetState.NO_SWING) {
			return 1.0;
		}
		return Math.min(1.0, (t.ticksSinceSwing() + 0.5) / CHARGE_TICKS);
	}

	/**
	 * The training target for the keys the scripted brain pressed: indices for the two 3-way choices, 0/1
	 * for the rest (version 2 adds: shield up, fighting with the axe).
	 */
	static double[] target(Inputs in, int version, boolean axe) {
		double[] base = {in.forward() + 1, in.strafe() + 1, in.jump() ? 1 : 0, in.sprint() ? 1 : 0, in.attack() ? 1 : 0};
		if (version < 2) {
			return base;
		}
		return new double[] {base[0], base[1], base[2], base[3], base[4], in.use() ? 1 : 0, axe ? 1 : 0};
	}

	/** The version 1 training target. */
	static double[] target(Inputs in) {
		return target(in, 1, false);
	}

	/** Decoded network output. */
	record Decision(int forward, int strafe, boolean jump, boolean sprint, boolean attack, boolean block, boolean axe) {
	}

	static Decision decode(double[] out) {
		boolean v2 = out.length >= OUTPUTS_V2;
		return new Decision(argmax(out, 0) - 1, argmax(out, 3) - 1, out[6] > 0, out[7] > 0, out[8] > 0, v2 && out[9] > 0, v2 && out[10] > 0);
	}

	/** The feature version a network of this input size was trained on. */
	static int version(int inputs) {
		return inputs == COUNT_V2 ? 2 : 1;
	}

	private static int argmax(double[] out, int from) {
		int best = 0;
		for (int i = 1; i < 3; i++) {
			if (out[from + i] > out[from + best]) {
				best = i;
			}
		}
		return best;
	}

	private static double clamp(double v) {
		return Math.max(-1, Math.min(1, v));
	}
}
