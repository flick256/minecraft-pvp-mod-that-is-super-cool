package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Ballistics;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.OptionalDouble;

/**
 * Bow and crossbow play at range. Vanilla 26.2 facts used: a bow reaches full power after 20 ticks
 * of drawing and launches arrows at 3.0; a crossbow charges in 25 ticks (Quick Charge shortens it),
 * loads when charged, and fires at 3.15 on the next use. Arrows fly with gravity 0.05 and drag 0.99.
 */
public final class RangedTactic implements Tactic {
	static final double MIN_RANGE = 8.0;
	static final double MAX_RANGE = 40.0;
	private static final int BOW_FULL_DRAW_TICKS = 20;
	/** Stop strafing this many ticks before a full draw, so the release comes from standing still. */
	private static final int STOP_BEFORE_SHOT = 4;
	/** Blocks per tick: slower than this counts as standing still for a shot. */
	private static final double STILL = 0.04;
	private static final int MAX_HOLD_TICKS = 70;

	/** Longest stretch of bow play (6 s) before pushing in for {@link #PUSH_IN_TICKS}. */
	private static final int MAX_RANGED_TICKS = 120;
	private static final int PUSH_IN_TICKS = 200;
	/** Farther than this, the bow is still the way to go. */
	private static final double FAR = 20.0;
	private int rangedTicks;
	private long pushInFrom = Long.MIN_VALUE / 2;
	private final Decision wantsRanged = new Decision();
	private boolean crossbowFiring;

	@Override
	public String name() {
		return "ranged";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible()) {
			return 0;
		}
		InventoryState inv = c.self.inventory();
		if (weaponSlot(inv) < 0) {
			return 0;
		}
		double distance = c.targetDistance();
		boolean drawing = inv.usingItem() && (inv.usingKind() == ItemKind.BOW || inv.usingKind() == ItemKind.CROSSBOW);
		if (drawing && distance > 5.0) {
			return Scores.SPECIALIST + 0.02; // finish the shot
		}
		if (distance < MIN_RANGE || distance > MAX_RANGE) {
			return 0;
		}
		// A bow exchange going nowhere: after a while a player pushes in instead (unless they're far off).
		long now = c.observation.tick();
		if (now - pushInFrom < PUSH_IN_TICKS && distance < FAR) {
			return 0;
		}
		if (rangedTicks > MAX_RANGED_TICKS) {
			rangedTicks = 0;
			pushInFrom = now;
			return 0;
		}
		return wantsRanged.get(c.rng, c.profile.items().bowSkill(), 60) ? Scores.SPECIALIST : 0;
	}

	@Override
	public void onExit(BrainContext c) {
		// Short breaks (a hotbar switch, a heal) don't reset the count; a fight that moved on does.
		if (c.targetDistance() < MIN_RANGE) {
			rangedTicks = 0;
		}
	}

	@Override
	public void reset() {
		rangedTicks = 0;
		pushInFrom = Long.MIN_VALUE / 2;
		wantsRanged.reset();
		crossbowFiring = false;
	}

	@Override
	public Inputs act(BrainContext c) {
		rangedTicks++;
		InventoryState inv = c.self.inventory();
		int slot = weaponSlot(inv);
		int press = c.memory.hands.request(c, slot);
		boolean inHand = slot >= 0 && inv.selectedSlot() == slot;
		ItemInfo weapon = slot >= 0 ? inv.slot(slot) : ItemInfo.EMPTY;
		boolean crossbow = weapon.is(ItemKind.CROSSBOW);

		float[] goal = aim(c, crossbow ? Ballistics.ARROW_CROSSBOW : Ballistics.ARROW_FULL_BOW);
		float[] look = c.lookAt(goal[0], goal[1]);
		// Hand steadiness: a less skilled archer releases with a larger aim error.
		float tolerance = (float) (0.8 + (1.0 - c.profile.items().bowSkill()) * 3.0);
		boolean aimed = c.aimError(goal[0], goal[1]) < tolerance;

		// A shot carries the shooter's own motion, and a loaded crossbow doesn't slow its holder, so a
		// skilled archer plants their feet for the shot and fires once they have stopped.
		boolean aboutToShoot = inHand && (crossbow ? weapon.charged()
			: inv.usingItem() && inv.usingKind() == ItemKind.BOW && inv.useTicks() >= BOW_FULL_DRAW_TICKS - STOP_BEFORE_SHOT);
		boolean plantFeet = aboutToShoot && c.memory.plantsFeet.get(c.rng, c.profile.items().bowSkill(), 40);
		boolean still = !plantFeet || Math.hypot(c.memory.selfMotion.x(), c.memory.selfMotion.z()) < STILL;

		boolean use = false;
		if (inHand) {
			if (crossbow) {
				if (weapon.charged()) {
					// Loaded: one press fires when on target.
					use = aimed && still && !crossbowFiring;
					crossbowFiring = use;
				} else {
					crossbowFiring = false;
					// Hold until vanilla loads the crossbow (charged flag), then let go.
					use = true;
				}
			} else {
				boolean drawing = inv.usingItem() && inv.usingKind() == ItemKind.BOW;
				boolean fullyDrawn = drawing && inv.useTicks() >= BOW_FULL_DRAW_TICKS;
				// Keep drawing until full power and on target; let go if held far too long.
				use = !(fullyDrawn && aimed && still) && !(drawing && inv.useTicks() > MAX_HOLD_TICKS);
			}
		}
		// Strafing into a wall gets nowhere: go the other way.
		if (c.self.horizontalCollision()) {
			c.memory.strafeDirection = -c.memory.strafeDirection;
		}
		Inputs inputs = new Inputs(look[0], look[1], 0, plantFeet || !c.allows(Technique.STRAFE) ? 0 : c.memory.strafeDirection, false, false, false, false, use, press);
		return Movement.guardEdges(c, inputs);
	}

	/**
	 * Yaw and pitch that lands an arrow on the target's chest, leading its movement. An arrow also
	 * carries the shooter's own horizontal motion (Projectile#shootFromRotation), so a strafing archer
	 * aims against it, as much as their tracking skill manages.
	 */
	static float[] aim(BrainContext c, Ballistics.Projectile projectile) {
		TargetState t = c.seen();
		Vec3 eye = c.self.eyePosition();
		Vec3 point = t.chest();
		double horizontal = point.horizontalDistanceTo(eye);
		OptionalDouble pitch = Ballistics.solvePitch(projectile, horizontal, point.y() - eye.y());
		if (pitch.isPresent()) {
			int flight = Ballistics.flightTicks(projectile, (float) pitch.getAsDouble(), horizontal);
			Vec3 own = new Vec3(c.memory.selfMotion.x(), 0, c.memory.selfMotion.z());
			point = point.add(t.velocity().subtract(own).scale(flight * c.profile.aim().trackingLead()));
			horizontal = point.horizontalDistanceTo(eye);
			pitch = Ballistics.solvePitch(projectile, horizontal, point.y() - eye.y());
		}
		float yaw = Angles.yawTowards(eye, point);
		float p = pitch.isPresent() ? (float) pitch.getAsDouble() : -35.0F;
		return new float[] {yaw, p};
	}

	static int weaponSlot(InventoryState inv) {
		boolean hasArrows = inv.count(ItemKind.ARROW) > 0;
		int crossbow = inv.hotbarSlot(i -> i.kind() == ItemKind.CROSSBOW && (i.charged() || hasArrows));
		if (crossbow >= 0 && inv.slot(crossbow).charged()) {
			return crossbow;
		}
		int bow = hasArrows ? inv.hotbarSlot(ItemKind.BOW) : -1;
		return bow >= 0 ? bow : crossbow;
	}
}
