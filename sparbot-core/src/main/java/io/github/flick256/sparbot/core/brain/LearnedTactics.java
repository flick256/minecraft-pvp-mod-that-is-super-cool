package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.ml.Mlp;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.List;

/**
 * Tactic choice learned by playing: a network that, every tick, shifts the scripted brain's tactic
 * scores up or down (by up to {@link #MAX_SHIFT}, less for some: see {@link #limit}), so it decides when lava, a web, a wall, the bow or a
 * heal pays off better than the hand-written priorities do. It never makes a tactic possible that isn't
 * (a tactic scoring 0 stays at 0), and the tactics themselves (aim, timing, hands) stay as they are, so
 * the bot is held to the same human limits. A network with all-zero outputs plays exactly like the
 * scripted brain, which is where training starts.
 */
public final class LearnedTactics {
	/** The tactics the network has a say over, in output order. */
	public static final List<String> TACTICS = List.of("engage", "retreat", "heal", "ranged", "guard", "refill", "lava", "web", "water", "wall", "cleanup",
		"boost", "breakweb");
	/** Input vector length. */
	public static final int INPUTS = 48 + 2 * TACTICS.size();
	public static final int OUTPUTS = TACTICS.size();
	/** Largest change to a tactic's score: enough to swap the scripted bands (melee 0.6, specialist 0.7, survival 0.8). */
	static final double MAX_SHIFT = 0.3;
	/**
	 * UHC is a utility mode: the network may bring the utility plays forward as much as it likes but hold
	 * them back only a little, and may not lift plain melee (and the shield or running only a little) over them. So it
	 * learns when lava, a web, the bow, a wall or a boost pays off best, and can't learn to fight with the
	 * sword alone.
	 */
	private static final java.util.Set<String> UTILITY = java.util.Set.of("lava", "web", "ranged", "wall", "boost");
	/** Held back by no more than this, a utility play still beats melee that is already running (0.6 + 0.05). */
	private static final double UTILITY_HOLD_BACK = 0.04;
	private static final java.util.Map<String, Double> LIFT = java.util.Map.of("engage", 0.0, "guard", 0.05, "retreat", 0.15);

	/** The most the network can lower ({@code up = false}) or raise a tactic's score. */
	static double limit(String tactic, boolean up) {
		if (UTILITY.contains(tactic)) {
			return up ? MAX_SHIFT : UTILITY_HOLD_BACK;
		}
		return up ? LIFT.getOrDefault(tactic, MAX_SHIFT) : MAX_SHIFT;
	}

	private final Mlp net;

	public LearnedTactics(Mlp net) {
		if (!fits(net)) {
			throw new IllegalArgumentException("a tactics network needs " + INPUTS + " inputs and " + OUTPUTS + " outputs, got " + net);
		}
		this.net = net;
	}

	public static boolean fits(Mlp net) {
		return net != null && net.inputs() == INPUTS && net.outputs() == OUTPUTS;
	}

	/** A fresh network that changes nothing (all-zero outputs): the scripted brain's choices exactly. */
	public static Mlp neutral(java.util.Random random) {
		Mlp n = Mlp.random(random, INPUTS, 32, 32, OUTPUTS);
		double[] p = n.params();
		int last = 32 * OUTPUTS + OUTPUTS;
		java.util.Arrays.fill(p, p.length - last, p.length, 0.0);
		return n;
	}

	/**
	 * Adds the network's shifts to {@code scores} (one per tactic in {@code tactics}, as the brain scored
	 * them); tactics scoring 0 are left alone.
	 */
	void shift(BrainContext c, List<Tactic> tactics, double[] scores, Tactic active) {
		double[] out = net.forward(encode(c, tactics, scores, active));
		for (int i = 0; i < tactics.size(); i++) {
			int k = TACTICS.indexOf(tactics.get(i).name());
			if (k >= 0 && scores[i] > 0) {
				double shift = Math.tanh(out[k]);
				scores[i] = Math.max(0.001, scores[i] + shift * limit(TACTICS.get(k), shift > 0));
			}
		}
	}

	/** What the network sees: the fight as the brain sees it (same delays), the bot's kit, and the scripted scores. */
	static double[] encode(BrainContext c, List<Tactic> tactics, double[] scores, Tactic active) {
		double[] f = new double[INPUTS];
		SelfState s = c.self;
		InventoryState inv = s.inventory();
		TargetState t = c.seen();
		int i = 0;
		// The opponent.
		double distance = c.targetDistance();
		f[i++] = t == null ? 1 : clamp(distance / 16.0);
		f[i++] = t == null ? 0 : clamp((t.position().y() - s.position().y()) / 3.0);
		f[i++] = t == null ? -1 : t.visible() ? 1 : -1;
		f[i++] = t == null ? 1 : Math.min(1, t.ticksSinceSeen() / 40.0);
		f[i++] = t == null ? 0 : t.health() / Math.max(1, t.maxHealth());
		f[i++] = flag(t != null && t.onGround());
		f[i++] = flag(t != null && t.inWeb());
		f[i++] = flag(t != null && t.inWater());
		f[i++] = flag(t != null && t.onFire());
		f[i++] = flag(t != null && t.blocking());
		f[i++] = flag(t != null && t.usingKind().isFood());
		f[i++] = flag(t != null && (t.usingKind() == ItemKind.BOW || t.usingKind() == ItemKind.CROSSBOW));
		f[i++] = flag(t != null && t.mainHand() == ItemKind.AXE);
		f[i++] = flag(t != null && t.mainHand() == ItemKind.BOW);
		f[i++] = t == null ? 0 : t.hurtTime() / 10.0;
		double closing = 0;
		double lateral = 0;
		if (t != null) {
			Vec3 to = t.position().subtract(s.position());
			double h = Math.sqrt(to.x() * to.x() + to.z() * to.z());
			if (h > 1e-6) {
				double ux = to.x() / h;
				double uz = to.z() / h;
				closing = -(t.velocity().x() * ux + t.velocity().z() * uz);
				lateral = -t.velocity().x() * uz + t.velocity().z() * ux;
			}
		}
		f[i++] = clamp(closing * 4);
		f[i++] = clamp(Math.abs(lateral) * 4);
		// Itself.
		f[i++] = s.health() / Math.max(1, s.maxHealth());
		f[i++] = Math.min(1, s.absorption() / 4.0);
		f[i++] = flag(s.hasEffect("minecraft:regeneration"));
		f[i++] = flag(s.onGround());
		f[i++] = flag(s.inWeb());
		f[i++] = flag(s.inWater());
		f[i++] = flag(s.inLava());
		f[i++] = flag(s.onFire());
		f[i++] = s.hurtTime() / 10.0;
		f[i++] = s.attackStrength();
		f[i++] = flag(inv.usingItem());
		f[i++] = flag(inv.offhand().is(ItemKind.SHIELD) && !inv.offhand().onCooldown());
		f[i++] = clamp(Math.hypot(c.memory.selfMotion.x(), c.memory.selfMotion.z()) * 4);
		f[i++] = Math.min(1, c.memory.ticksSinceOwnClick / 20.0);
		// The kit.
		f[i++] = Math.min(1, (inv.count(ItemKind.GOLDEN_APPLE) + inv.count(ItemKind.GOLDEN_HEAD)) / 6.0);
		f[i++] = Math.min(1, inv.count(ItemKind.LAVA_BUCKET) / 2.0);
		f[i++] = Math.min(1, inv.count(ItemKind.WATER_BUCKET) / 2.0);
		f[i++] = Math.min(1, inv.count(ItemKind.BUCKET) / 2.0);
		f[i++] = Math.min(1, inv.count(ItemKind.COBWEB) / 8.0);
		f[i++] = Math.min(1, inv.count(WallTactic::wallBlock) / 64.0);
		f[i++] = Math.min(1, inv.count(ItemKind.ARROW) / 32.0);
		f[i++] = flag(inv.hotbarSlot(ItemKind.WATER_BUCKET) >= 0);
		f[i++] = flag(inv.hotbarSlot(ItemKind.LAVA_BUCKET) >= 0);
		f[i++] = flag(inv.hotbarSlot(ItemKind.BOW) >= 0);
		// What lies around.
		f[i++] = Math.min(1, c.world.lavaSources().size() / 3.0);
		f[i++] = Math.min(1, c.world.waterSources().size() / 3.0);
		f[i++] = Math.min(1, c.world.webs().size() / 4.0);
		int minDrop = SelfState.VOID_DROP;
		if (s.dropDepth() != null) {
			for (int d : s.dropDepth()) {
				minDrop = Math.min(minDrop, d);
			}
		}
		f[i++] = Math.min(1, minDrop / 4.0);
		f[i++] = c.memory.walledAt > Long.MIN_VALUE / 4 ? Math.min(1, (c.observation.tick() - c.memory.walledAt) / 100.0) : 1;
		f[i++] = Math.min(1, c.memory.sinceOwnHit / 40.0);
		f[i++] = 1; // bias
		// The scripted scores, and which tactic is running.
		i = 48;
		for (int k = 0; k < TACTICS.size(); k++) {
			for (int j = 0; j < tactics.size(); j++) {
				if (tactics.get(j).name().equals(TACTICS.get(k))) {
					f[i + k] = scores[j];
					f[i + TACTICS.size() + k] = tactics.get(j) == active ? 1 : 0;
				}
			}
		}
		return f;
	}

	private static double flag(boolean b) {
		return b ? 1 : -1;
	}

	private static double clamp(double v) {
		return Math.max(-1, Math.min(1, v));
	}
}
