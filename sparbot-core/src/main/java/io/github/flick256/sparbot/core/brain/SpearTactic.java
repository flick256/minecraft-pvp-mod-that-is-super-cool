package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;

/**
 * Spear fighting. 26.2 facts (Item.Properties#spear, PiercingWeapon, KineticWeapon, ProjectileUtil):
 * <ul>
 *   <li><b>Jab</b> (left click): needs a full attack charge (minimum_attack_charge 1.0) and hits every
 *       entity on the look ray from 2.0 to 4.5 blocks from the eyes, plus however far the attacker
 *       moved forward that tick. Anything closer than 2 blocks is not hit at all.</li>
 *   <li><b>Charge</b> (hold right click): a spear neither slows its holder nor stops the sprint. After the
 *       spear's delay (0.4-0.75 s by material) every target on the same ray is hit at most once per
 *       10 ticks, for 1 + floor(relative speed x multiplier) damage. Damage needs a closing speed along
 *       the look of at least 4.6 blocks/s, knockback needs the attacker's own speed of at least 5.1
 *       (sprinting on the ground is about 5.6). Both windows close after a few seconds of holding.</li>
 * </ul>
 * So a spear player stays at jab range (about 3 blocks) and jabs on full charge, and when there is a
 * gap sprints in holding a charge. Skill decides whether the bot keeps that range or walks into
 * the dead zone, and whether it charges a target that is running straight away (no damage).
 */
public final class SpearTactic implements Tactic {
	/** AttackRange of every 26.2 spear: min_reach 2.0, max_reach 4.5, hitbox_margin 0.125. */
	static final double MIN_REACH = 2.0;
	static final double MAX_REACH = 4.5;
	static final double HITBOX_MARGIN = 0.125;
	/** Jab spacing (eye-to-hitbox distance). */
	private static final double TOO_CLOSE = 2.0;
	private static final double TOO_FAR = 3.6;
	/** Start a charge from this far: enough run-up to get past the spear's delay at sprint speed. */
	private static final double CHARGE_MIN = 6.0;
	private static final double CHARGE_MAX = 16.0;
	/** Give a charge up before its damage window (8.75-15 s by material) and knockback window close. */
	private static final int CHARGE_MAX_TICKS = 100;
	/** Below this the charge has overrun the target. */
	private static final double OVERRUN = 1.2;
	/** Blocks per tick: a target moving away faster than this along the line takes no charge damage. */
	private static final double FLEEING = 0.15;

	private final Decision willSpear = new Decision();
	private final Decision willCharge = new Decision();
	private final Decision keepsRange = new Decision();
	private boolean charging;
	private int chargeTicks;
	private int lastTargetHurt;

	@Override
	public String name() {
		return "spear";
	}

	@Override
	public double score(BrainContext c) {
		if (charging) {
			return Scores.SPECIALIST + 0.1;
		}
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || t.ticksSinceSeen() > 100 || spearSlot(inv) < 0) {
			return 0;
		}
		if (Hands.preferredMeleeSlot(inv) < 0) {
			// The spear is the only weapon: fight with it at whatever skill the profile has.
			return Scores.SPECIALIST - 0.02;
		}
		return willSpear.get(c.rng, c.profile.items().spearSkill(), 60) ? Scores.SPECIALIST - 0.02 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		charging = false;
		lastTargetHurt = c.target == null ? 0 : c.target.hurtTime();
	}

	@Override
	public void onExit(BrainContext c) {
		// Interrupted (e.g. by a retreat): the charge is let go and not resumed later.
		charging = false;
	}

	@Override
	public void reset() {
		charging = false;
		willSpear.reset();
		willCharge.reset();
		keepsRange.reset();
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		int spear = spearSlot(inv);
		if (t == null || spear < 0) {
			charging = false;
			return Inputs.IDLE;
		}
		int press = c.memory.hands.request(c, spear);
		boolean armed = inv.selectedSlot() == spear;

		// Aim at the chest, leading by the skill's share of the motion during the tracking delay.
		Vec3 lead = t.velocity().scale(c.profile.aim().trackingLead() * (c.trackingDelayTicks + 1));
		Vec3 goal = t.chest().add(lead);
		float[] look = c.lookAt(Angles.yawTowards(self.eyePosition(), goal), Angles.pitchTowards(self.eyePosition(), goal));
		double distance = c.targetDistance();
		double skill = c.profile.items().spearSkill();

		// Our hit landing is noticed a reaction time late, through the target's hurt animation.
		boolean landed = c.target != null && c.target.hurtTime() > lastTargetHurt;
		lastTargetHurt = c.target != null ? c.target.hurtTime() : 0;

		if (charging) {
			chargeTicks = inv.usingItem() && inv.usingKind() == ItemKind.SPEAR ? inv.useTicks() : chargeTicks + 1;
			if (landed || distance < OVERRUN || chargeTicks > CHARGE_MAX_TICKS || !t.visible()) {
				charging = false;
				willCharge.reset();
			} else {
				boolean jump = self.horizontalCollision() && self.onGround();
				return Movement.guardEdges(c, new Inputs(look[0], look[1], 1, 0, jump, false, true, false, armed, press));
			}
		}

		if (armed && distance >= CHARGE_MIN && distance <= CHARGE_MAX && t.visible() && !inv.usingItem()
			&& !(fleeing(self, t) && c.rng.chance(skill)) && willCharge.get(c.rng, skill, 40)) {
			charging = true;
			chargeTicks = 0;
			return Movement.guardEdges(c, new Inputs(look[0], look[1], 1, 0, false, false, true, false, true, press));
		}

		// Jab range. A skilled player keeps about 3 blocks; others drift into the 2-block dead zone.
		int forward = 1;
		boolean sprint = distance > TOO_FAR + 1.5;
		if (keepsRange.get(c.rng, skill, 20)) {
			if (distance < TOO_CLOSE) {
				forward = -1;
			} else if (distance <= TOO_FAR) {
				forward = 0;
			}
		} else if (distance < 1.0) {
			forward = 0;
		}
		DuelMemory m = c.memory;
		if (--m.ticksUntilStrafeSwitch <= 0) {
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 25);
			m.strafeActive = c.rng.chance(c.skill(Technique.STRAFE));
		}
		int strafe = distance < 6.0 && m.strafeActive ? m.strafeDirection : 0;
		boolean jump = self.horizontalCollision() && self.onGround() && forward > 0;

		boolean jab = armed && !inv.usingItem() && self.attackStrength() >= 1.0F && jabWouldHit(c, MAX_REACH + m.reachError);
		if (c.mistake(Mistake.WHIFF) && armed && self.attackStrength() >= 1.0F && distance < MAX_REACH + 2) {
			jab = true;
		}
		return Movement.guardEdges(c, new Inputs(look[0], look[1], forward, strafe, jump, false, sprint, jab, false, press));
	}

	/** Whether the jab ray (2 to {@code maxReach} blocks along the look) passes through the target's hitbox. */
	static boolean jabWouldHit(BrainContext c, double maxReach) {
		TargetState t = c.seen();
		if (t == null) {
			return false;
		}
		Vec3 eye = c.self.eyePosition();
		Vec3 dir = Angles.lookVector(c.self.yaw(), c.self.pitch());
		double w = t.halfWidth() + HITBOX_MARGIN;
		Vec3 min = new Vec3(t.position().x() - w, t.position().y() - HITBOX_MARGIN, t.position().z() - w);
		Vec3 max = new Vec3(t.position().x() + w, t.position().y() + t.height() + HITBOX_MARGIN, t.position().z() + w);
		return BrainContext.rayHitsBox(eye.add(dir.scale(MIN_REACH)), dir, min, max, maxReach - MIN_REACH);
	}

	/** The target is moving away along the line between us fast enough that a charge would do no damage. */
	private static boolean fleeing(SelfState self, TargetState t) {
		Vec3 toTarget = t.position().subtract(self.position());
		double len = Math.sqrt(toTarget.x() * toTarget.x() + toTarget.z() * toTarget.z());
		if (len < 1e-6) {
			return false;
		}
		double away = (t.velocity().x() * toTarget.x() + t.velocity().z() * toTarget.z()) / len;
		return away > FLEEING;
	}

	static int spearSlot(InventoryState inv) {
		return inv.bestHotbarWeapon(ItemKind.SPEAR);
	}
}
