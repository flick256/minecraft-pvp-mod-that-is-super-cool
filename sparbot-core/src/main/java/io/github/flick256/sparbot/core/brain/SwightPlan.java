package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.TargetState;

/** Swight chooses UHC plays more selectively. It shares the normal perception and input limits. */
final class SwightPlan {
	/** Swight keeps two webs in reserve for a clear opening or a heal setup. */
	static final int WEB_RESERVE = 2;
	private int shieldTicks;
	private long shieldRecoverUntil;

	static double weight(BrainContext c, String tactic) {
		if (!"swight".equals(c.profile.id()) || !hasTools(c)) { return 1; }
		// Shorter defensive construction and fewer speculative switches leave time to exploit a
		// web/lava opening with the sword. Existing tactic preconditions still decide what is legal.
		return switch (tactic) {
			case "backline" -> 0.8;
			case "wall" -> 0.7;
			case "boost" -> 0.5;
			case "lava" -> c.target != null && c.target.usingKind().isFood() ? 1.15 : 0.9;
			case "web" -> c.target != null && !c.target.onGround() && c.memory.ticksSinceOwnClick < 15 ? 1.15 : 0.9;
			default -> 1;
		};
	}

	/** Close on a visibly occupied hand; stop short of walking into the web we just placed. */
	static boolean meleeOpening(BrainContext c, double distance, double reach, SwordPlan.Move move) {
		if (!"swight".equals(c.profile.id()) || c.target == null || !c.memory.reading || c.self.inWeb()) { return false; }
		boolean busy = switch (c.target.mainHand()) {
			case WATER_BUCKET, LAVA_BUCKET, BUCKET, COBWEB, BLOCK, GOLDEN_APPLE, GOLDEN_HEAD, DRINK_POTION -> true;
			default -> false;
		};
		if (!busy && !c.target.inWeb()) { return false; }
		move.forward = distance > reach - 0.65 ? 1 : distance < 1.4 ? -1 : 0;
		move.sprint = distance > reach + 0.7;
		if (c.target.inWeb() && distance < reach) { move.strafe = 0; }
		// A head-height web or another block can take the click before the opponent. Standing
		// still outside our trap then waits forever: circle for an angle without walking into it.
		if (distance < reach && c.crosshairBlockedBeforeTarget(reach + 0.5)) {
			move.forward = 0;
			move.strafe = c.memory.strafeDirection;
			move.sprint = false;
		}
		move.planned = true;
		return true;
	}

	/** Aim at an exposed part of a webbed opponent, using only the visible webs already in the observation. */
	static Vec3 meleeAim(BrainContext c, TargetState target, Vec3 lead) {
		Vec3 chest = target.chest().add(lead);
		if (!"swight".equals(c.profile.id()) || !target.inWeb() || !webBlocks(c, target, chest)) { return chest; }
		for (double height : new double[] {0.9, 0.25}) {
			Vec3 point = target.position().add(new Vec3(0, target.height() * height, 0)).add(lead);
			if (!webBlocks(c, target, point)) { return point; }
		}
		return chest;
	}

	private static boolean webBlocks(BrainContext c, TargetState target, Vec3 point) {
		Vec3 eye = c.self.eyePosition(), to = point.subtract(eye);
		double length = to.length();
		if (length < 0.001) { return false; }
		Vec3 dir = to.scale(1 / length);
		double entry = BrainContext.rayEntry(eye, dir,
			new Vec3(target.position().x() - target.halfWidth(), target.position().y(), target.position().z() - target.halfWidth()),
			new Vec3(target.position().x() + target.halfWidth(), target.position().y() + target.height(), target.position().z() + target.halfWidth()), length);
		if (entry <= 0) { return false; }
		for (BlockSpot web : c.world.webs()) {
			if (BrainContext.rayHitsBox(eye, dir, new Vec3(web.x(), web.y(), web.z()), new Vec3(web.x() + 1, web.y() + 1, web.z() + 1), entry - 0.01)) {
				return true;
			}
		}
		return false;
	}

	/** Sustained shield use has visible release windows, even against an opponent holding a bow. */
	Inputs limitShield(BrainContext c, Inputs in) {
		if (!"swight".equals(c.profile.id())) { return in; }
		var inv = c.self.inventory();
		ItemKind held = in.hotbarSlot() >= 0 ? inv.slot(in.hotbarSlot()).kind() : inv.mainHand().kind();
		boolean shield = in.use() && !in.inventoryOpen() && inv.offhand().is(ItemKind.SHIELD)
			&& (held == ItemKind.SWORD || held == ItemKind.AXE || held == ItemKind.MACE);
		if (!shield) { shieldTicks = 0; return in; }
		if (c.observation.tick() < shieldRecoverUntil) { return in.withUse(false); }
		if (++shieldTicks > 22) {
			shieldTicks = 0;
			shieldRecoverUntil = c.observation.tick() + 6;
			return in.withUse(false);
		}
		return in;
	}

	void reset() { shieldTicks = 0; shieldRecoverUntil = 0; }

	/** A small, fallible lead for grounded utility, based only on already delayed visible motion. */
	static BlockSpot utilityGround(BrainContext c, TargetState t) {
		if (!"swight".equals(c.profile.id()) || !t.onGround() || t.inWeb()) { return BlockPlay.groundBelow(c, t); }
		double lead = 2 * c.profile.aim().trackingLead();
		double dx = t.velocity().x() * lead, dz = t.velocity().z() * lead;
		double length = Math.hypot(dx, dz);
		if (length > 0.65) { dx *= 0.65 / length; dz *= 0.65 / length; }
		return BlockPlay.groundUnder(t.position().add(new Vec3(dx, 0, dz)));
	}

	private static boolean hasTools(BrainContext c) {
		var inv = c.self.inventory();
		return inv.count(ItemKind.LAVA_BUCKET) > 0 || inv.count(ItemKind.WATER_BUCKET) > 0 || inv.count(ItemKind.COBWEB) > 0;
	}
}
