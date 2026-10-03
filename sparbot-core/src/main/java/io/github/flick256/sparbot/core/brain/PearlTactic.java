package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Ballistics;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.OptionalDouble;

/**
 * Ender pearls: close a long distance to the opponent, or escape when low and cornered. Pearls are
 * thrown at 1.5 with gravity 0.03 and drag 0.99, have a 1 second cooldown and are consumed; the
 * teleport itself is vanilla (including its landing damage).
 */
public final class PearlTactic implements Tactic {
	static final double ENGAGE_MIN = 16.0;
	static final double ENGAGE_MAX = 45.0;
	private static final double ESCAPE_DISTANCE = 18.0;

	private final Decision willEngage = new Decision();
	private final Decision willEscape = new Decision();
	private boolean escaping;
	private int aimTicks;

	@Override
	public String name() {
		return "pearl";
	}

	@Override
	public double score(BrainContext c) {
		TargetState t = c.seen();
		InventoryState inv = c.self.inventory();
		int slot = pearlSlot(inv);
		if (c.target == null || t == null || slot < 0 || !c.self.onGround()) {
			return 0;
		}
		double distance = c.targetDistance();
		boolean low = c.self.healthFraction() < c.profile.panicHealthFraction();
		if (low && distance < 6 && willEscape.get(c.rng, c.profile.items().pearlSkill(), 40)) {
			return 0.88;
		}
		// A decided engage-pearl outranks drawing a bow: the bot commits to closing the gap.
		if (t.visible() && distance >= ENGAGE_MIN && distance <= ENGAGE_MAX && willEngage.get(c.rng, c.profile.items().pearlSkill(), 40)) {
			return Scores.SPECIALIST + 0.08;
		}
		return 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		escaping = c.self.healthFraction() < c.profile.panicHealthFraction() && c.targetDistance() < 6;
		aimTicks = 0;
	}

	@Override
	public void reset() {
		willEngage.reset();
		willEscape.reset();
		aimTicks = 0;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		int slot = pearlSlot(inv);
		int press = c.memory.hands.request(c, slot);
		boolean inHand = slot >= 0 && inv.selectedSlot() == slot;
		float[] goal = aim(c, escaping);
		float[] look = c.lookAt(goal[0], goal[1]);
		boolean aimed = c.aimError(goal[0], goal[1]) < 2.5F;
		aimTicks++;
		boolean throwNow = inHand && (aimed || aimTicks > 30);
		if (throwNow) {
			// One throw per decision; the item cooldown stops the pearl being thrown twice anyway.
			willEngage.reset();
			willEscape.reset();
		}
		return new Inputs(look[0], look[1], escaping ? 0 : 1, 0, false, false, !escaping, false, throwNow, press);
	}

	/** Engage: land just short of the opponent. Escape: land well away from them, level with us. */
	private static float[] aim(BrainContext c, boolean escaping) {
		Vec3 eye = c.self.eyePosition();
		Vec3 opponent = c.seen().position();
		Vec3 landing;
		if (escaping) {
			float away = Angles.wrapDegrees(Angles.yawTowards(eye, opponent) + 180.0F);
			Vec3 dir = Angles.lookVector(away, 0);
			landing = c.self.position().add(dir.scale(ESCAPE_DISTANCE));
		} else {
			Vec3 toUs = c.self.position().subtract(opponent);
			double len = toUs.horizontalLength();
			landing = opponent.add(new Vec3(toUs.x() / len * 2.0, 0, toUs.z() / len * 2.0));
		}
		double horizontal = landing.horizontalDistanceTo(eye);
		OptionalDouble pitch = Ballistics.solvePitch(Ballistics.ENDER_PEARL, horizontal, landing.y() - eye.y());
		return new float[] {Angles.yawTowards(eye, landing), pitch.isPresent() ? (float) pitch.getAsDouble() : -40.0F};
	}

	static int pearlSlot(InventoryState inv) {
		return inv.hotbarSlot(i -> i.kind() == ItemKind.ENDER_PEARL && !i.onCooldown());
	}
}
