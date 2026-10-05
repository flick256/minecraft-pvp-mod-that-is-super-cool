package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Comparator;
import java.util.Optional;

/**
 * Answering the opponent's carts. 26.2 facts (MinecartTNT#destroy): a TNT minecart that isn't moving,
 * hit by anything but fire or an explosion, just breaks and drops as an item; only a burning arrow, a
 * blast, or a hit while it rolls sets it off. So when a cart lands next to the bot (closer to it than to
 * the opponent: theirs, aimed at it), a cart player knocks it out with one sword hit before the Flame
 * arrow arrives, or, out of reach of it, gets out of the blast. Skill decides how reliably it reacts.
 */
public final class DefuseTactic implements Tactic {
	/** A cart this close (horizontally) is a threat: a power-4 to 5.5 blast hurts badly within a few blocks. */
	private static final double DANGER = 4.0;
	/** A TNT minecart is 0.98 wide and 0.7 tall. */
	private static final double HALF = 0.49;
	private static final double HEIGHT = 0.7;

	private final Decision willDefuse = new Decision();

	@Override
	public String name() {
		return "defuse";
	}

	@Override
	public double score(BrainContext c) {
		if (threat(c).isEmpty()) {
			return 0;
		}
		return willDefuse.get(c.rng, c.profile.items().cartSkill(), 10) ? Scores.SURVIVAL + 0.1 : 0;
	}

	@Override
	public void reset() {
		willDefuse.reset();
	}

	/** The nearest cart by the bot that is the opponent's (nearer the bot than them). */
	private static Optional<Vec3> threat(BrainContext c) {
		TargetState t = c.seen();
		Vec3 here = c.self.position();
		return c.world.tntCarts().stream()
			.filter(p -> horizontal(p, here) <= DANGER && (t == null || horizontal(p, here) < horizontal(p, t.position())) && !own(c, p))
			.min(Comparator.comparingDouble(p -> horizontal(p, here)));
	}

	@Override
	public Inputs act(BrainContext c) {
		Optional<Vec3> found = threat(c);
		if (found.isEmpty()) {
			return Inputs.IDLE;
		}
		Vec3 cart = found.get();
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		Vec3 eye = self.eyePosition();
		Vec3 middle = cart.add(new Vec3(0, HEIGHT / 2, 0));
		Vec3 min = new Vec3(cart.x() - HALF, cart.y(), cart.z() - HALF);
		Vec3 max = new Vec3(cart.x() + HALF, cart.y() + HEIGHT, cart.z() + HALF);
		double reach = self.attackReach();
		boolean inReach = BrainContext.rayEntry(eye, middle.subtract(eye).normalize(), min, max, reach) >= 0;
		if (inReach) {
			// One hit with whatever is in hand breaks it; the sword if it is a switch away.
			float[] look = c.lookAt(Angles.yawTowards(eye, middle), Angles.pitchTowards(eye, middle));
			int sword = Hands.preferredMeleeSlot(inv);
			int press = sword >= 0 ? c.memory.hands.request(c, sword) : -1;
			boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()), min, max, reach);
			return new Inputs(look[0], look[1], 0, 0, false, false, false, aimed && !inv.usingItem(), false, press);
		}
		// Out of reach of it: get clear of the blast, facing the opponent.
		TargetState t = c.seen();
		Vec3 facing = t != null ? t.chest() : middle;
		float[] look = c.lookAt(Angles.yawTowards(eye, facing), Angles.pitchTowards(eye, facing));
		float away = Angles.yawTowards(new Vec3(cart.x(), self.position().y(), cart.z()), self.position());
		float rel = Angles.wrapDegrees(away - (self.yaw() + look[0]));
		int forward = Math.abs(rel) < 67.5F ? 1 : Math.abs(rel) > 112.5F ? -1 : 0;
		int strafe = rel < -22.5F && rel > -157.5F ? 1 : rel > 22.5F && rel < 157.5F ? -1 : 0;
		return Movement.guardEdges(c, new Inputs(look[0], look[1], forward, strafe, false, false, forward > 0, false, false, -1));
	}

	/** The bot's own cart (put down in the last few seconds where it is now). */
	private static boolean own(BrainContext c, Vec3 cart) {
		return c.memory.ownCart != null && c.observation.tick() - c.memory.ownCartAt < 200 && horizontal(cart, c.memory.ownCart) < 1.2;
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		return Math.hypot(a.x() - b.x(), a.z() - b.z());
	}
}
