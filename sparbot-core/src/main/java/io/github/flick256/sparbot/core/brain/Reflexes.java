package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.sense.SelfState;

/**
 * What a practised player keeps doing with their hands and feet while busy with utility (a bucket, a
 * block, a crystal): the hit that is there for the taking, the jump reset when knocked back, and not
 * standing still to be comboed. Applied on top of whatever the active tactic pressed; the melee
 * tactics do all of this themselves and are left alone.
 */
final class Reflexes {
	/** Opponent this close, or a hit taken within the last half second: under pressure. */
	private static final double PRESSURE_RANGE = 2.8;

	private Reflexes() {
	}

	static Inputs apply(BrainContext c, Tactic active, Inputs in) {
		if (active instanceof EngageTactic || active instanceof LearnedMeleeTactic || active instanceof ImitationRecorder || active instanceof SpearTactic || active instanceof MaceTactic || active instanceof SearchTactic || in.inventoryOpen() || c.target == null
			|| !c.allows(Technique.REFLEXES)) {
			return in;
		}
		SelfState self = c.self;
		DuelMemory m = c.memory;
		boolean justHurt = self.hurtTime() > m.lastSelfHurtTime;

		// Jump the moment the knockback lands: cancels part of it, as in melee.
		if (justHurt && self.onGround() && !in.jump() && c.rng.chance(c.skill(Technique.JUMP_RESET))) {
			in = in.withJump(true);
		}

		// The free hit: weapon in hand, charged, crosshair on them, in reach.
		double distance = c.targetDistance();
		double reach = self.attackReach() + m.reachError;
		if (!in.attack() && !in.use() && !self.inventory().usingItem() && self.holdingMeleeWeapon() && in.hotbarSlot() < 0
			&& self.attackStrength() >= m.cooldownThreshold && distance <= reach && c.clickHitsTarget(reach + 0.5)) {
			in = in.withAttack(true);
			m.ticksSinceOwnClick = 0;
			m.reachError = c.profile.reach().rangeErrorBlocks().sample(c.rng);
			m.cooldownThreshold = EngageTactic.sampleCooldownThreshold(c);
		}

		// Standing still under pressure gets you comboed: sidestep while the hands work.
		boolean pressured = self.hurtTime() > 0 || distance < PRESSURE_RANGE;
		if (pressured && in.forward() == 0 && in.strafe() == 0 && !(active instanceof WallTactic) && c.rng.chance(c.skill(Technique.STRAFE))) {
			in = Movement.guardEdges(c, in.withMovement(0, m.strafeDirection));
		}
		return in;
	}
}
