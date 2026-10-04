package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;

/**
 * Caught in a cobweb with no water bucket to hand (or not reaching for it): cut out with the sword. A
 * sword is the right tool for a cobweb (26.2: speed 15), so holding the attack button on it breaks it in
 * 8 ticks on the ground (a fifth as fast in the air or with the eyes under water). The bot holds the
 * sword, puts the crosshair on the web it is caught in and holds the button; with its eyes inside the web
 * the crosshair is on it whatever it looks at, so it keeps facing the opponent.
 */
public final class BreakWebTactic implements Tactic {
	/** Aim error (degrees) within which the crosshair is taken to be on the web. */
	private static final float ON_WEB = 12.0F;

	private final Decision willBreak = new Decision();

	@Override
	public String name() {
		return "breakweb";
	}

	@Override
	public double score(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		if (!self.inWeb() || inv.bestHotbarWeapon(ItemKind.SWORD) < 0 || web(c) == null) {
			return 0;
		}
		// Water first when it is at hand (the water tactic scores above this); the sword otherwise.
		return willBreak.get(c.rng, c.profile.items().uhcSkill(), 20) ? Scores.SURVIVAL + 0.1 : 0;
	}

	@Override
	public void reset() {
		willBreak.reset();
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		int sword = inv.bestHotbarWeapon(ItemKind.SWORD);
		int press = c.memory.hands.request(c, sword);
		boolean holding = inv.selectedSlot() == sword;
		BlockSpot web = web(c);
		if (web == null) {
			return Inputs.IDLE;
		}
		Vec3 eye = self.eyePosition();
		boolean eyesInWeb = web.x() == (int) Math.floor(eye.x()) && web.y() == (int) Math.floor(eye.y()) && web.z() == (int) Math.floor(eye.z());
		float[] look;
		boolean onWeb;
		if (eyesInWeb && c.seen() != null) {
			// From inside, any look is on the web: keep the opponent in view.
			Vec3 chest = c.seen().chest();
			look = c.lookAt(Angles.yawTowards(eye, chest), Angles.pitchTowards(eye, chest));
			onWeb = true;
		} else {
			// The nearest point of the web block, a little inside it.
			Vec3 point = new Vec3(clamp(eye.x(), web.x() + 0.2, web.x() + 0.8), clamp(eye.y(), web.y() + 0.2, web.y() + 0.8),
				clamp(eye.z(), web.z() + 0.2, web.z() + 0.8));
			float yaw = Angles.yawTowards(eye, point);
			float pitch = Angles.pitchTowards(eye, point);
			look = c.lookAt(yaw, pitch);
			onWeb = c.aimError(yaw, pitch) < ON_WEB;
		}
		return new Inputs(look[0], look[1], 0, 0, false, false, false, false, false, press).withHoldAttack(holding && onWeb);
	}

	/** The web to cut: the one the eyes are in, else the one at the feet (the head-height web next to it after that). */
	private static BlockSpot web(BrainContext c) {
		Vec3 eye = c.self.eyePosition();
		BlockSpot eyeBlock = new BlockSpot((int) Math.floor(eye.x()), (int) Math.floor(eye.y()), (int) Math.floor(eye.z()));
		if (c.world.webs().contains(eyeBlock)) {
			return eyeBlock;
		}
		Vec3 p = c.self.position();
		BlockSpot best = null;
		for (BlockSpot w : c.world.webs()) {
			boolean overlaps = p.x() + 0.3 > w.x() && p.x() - 0.3 < w.x() + 1 && p.z() + 0.3 > w.z() && p.z() - 0.3 < w.z() + 1 && p.y() + 1.8 > w.y()
				&& p.y() < w.y() + 1;
			if (overlaps && (best == null || w.y() < best.y())) {
				best = w;
			}
		}
		return best;
	}

	private static double clamp(double v, double lo, double hi) {
		return Math.max(lo, Math.min(hi, v));
	}
}
