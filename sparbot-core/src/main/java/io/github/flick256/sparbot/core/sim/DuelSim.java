package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.brain.Policy;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import java.util.ArrayList;
import java.util.List;

/**
 * A duel between two policies in the simulator. Each side sees the same {@link Observation} it would
 * get in game and its inputs go through the same {@link InputShaper} (ping, click and turn limits) as a
 * real bot's, so a policy that fights well here fights the same way in Minecraft.
 */
public final class DuelSim {
	/** A 24 x 24 walled square, like a duel arena. */
	public static final double ARENA_HALF_SIZE = 12.0;

	private DuelSim() {
	}

	/** What one side did over the fight: utility, damage taken from the world. */
	public record SideStats(int lavaPours, int waterPours, int scoops, int blocksPlaced, int websPlaced, int arrowsShot, int arrowHits,
		float lavaDamage, float fireDamage, float fallDamage, int ticksInWeb, int ticksInWater, int ticksBurning, int[] hitDistance) {
		/** Melee hits that landed from beyond {@code blocks} (a tenth-of-a-block resolution). */
		public int hitsBeyond(double blocks) {
			int n = 0;
			for (int i = (int) Math.round(blocks * 10); i < hitDistance.length; i++) {
				n += hitDistance[i];
			}
			return n;
		}

		/** Mean distance melee hits landed from. */
		public double meanHitDistance() {
			double sum = 0;
			int n = 0;
			for (int i = 0; i < hitDistance.length; i++) {
				sum += hitDistance[i] * (i + 0.5) / 10.0;
				n += hitDistance[i];
			}
			return n == 0 ? 0 : sum / n;
		}

		public int damagingHits() {
			int n = 0;
			for (int h : hitDistance) {
				n += h;
			}
			return n;
		}
	}

	/**
	 * How a fight went, from both sides (index 0 = the first policy).
	 *
	 * @param winner 0 or 1, or -1 when time ran out with both alive
	 */
	public record Result(int winner, int ticks, float[] health, float[] damage, int[] swings, int[] hits, int[] crits, int[] sprintHits,
		int[] strafeTicks, int[] blockedHits, int[] gapplesEaten, SideStats[] sides) {
		/** Score from side {@code side}'s point of view: 1 for a win, 0 for a loss, health-based in between on a timeout. */
		public double score(int side) {
			if (winner >= 0) {
				return winner == side ? 1.0 : 0.0;
			}
			double mine = Math.max(0, Math.min(SimFighter.MAX_HEALTH, health[side]));
			double theirs = Math.max(0, Math.min(SimFighter.MAX_HEALTH, health[1 - side]));
			return 0.5 + 0.5 * (mine - theirs) / SimFighter.MAX_HEALTH;
		}
	}

	/** One side: a policy and the skill profile whose limits its inputs are held to. */
	public record Side(Policy policy, SkillProfile limits) {
	}

	/** A sword duel in the 24 x 24 arena (or, for a loadout with a whole kit, the UHC arena). */
	public static Result fight(Side a, Side b, Loadout loadout, long seed, int maxTicks) {
		return fight(a, b, loadout, loadout.hasKit() ? SimArena.UHC : SimArena.DUEL, seed, maxTicks, null);
	}

	/**
	 * A duel in the given arena.
	 *
	 * @param watcher sees every tick (diagnostics), or null
	 */
	public static Result fight(Side a, Side b, Loadout loadout, SimArena arena, long seed, int maxTicks, Watcher watcher) {
		Rng rng = new Rng(seed);
		double[] starts = arena.starts(rng);
		SimWorld world = arena.build(rng.fork(), starts);
		SimFighter[] f = {
			new SimFighter(loadout, world, starts[0], 0, starts[1], (float) starts[4]),
			new SimFighter(loadout, world, starts[2], 0, starts[3], (float) starts[5])
		};
		Side[] sides = {a, b};
		InputShaper[] shapers = {new InputShaper(a.limits(), rng.fork(), InputShaper.ABSOLUTE_MAX_CPS),
			new InputShaper(b.limits(), rng.fork(), InputShaper.ABSOLUTE_MAX_CPS)};
		SimPerception[] eyes = {new SimPerception(), new SimPerception()};
		a.policy().reset();
		b.policy().reset();
		List<SimArrow> arrows = new ArrayList<>();
		int[][] counters = new int[2][3];
		int tick = 0;
		while (tick < maxTicks && !f[0].dead() && !f[1].dead()) {
			for (SimFighter fighter : f) {
				fighter.receiveKnockback();
			}
			Inputs[] in = new Inputs[2];
			for (int i = 0; i < 2; i++) {
				Observation obs = eyes[i].observe(tick, f[i], f[1 - i], 1 - i);
				in[i] = shapers[i].shape(sides[i].policy().act(obs));
			}
			if (watcher != null) {
				watcher.tick(tick, world, f, in, sides);
			}
			for (int i = 0; i < 2; i++) {
				f[i].look(in[i]);
			}
			// The server handles both players' clicks before moving anyone; whose packets come first varies.
			int first = rng.chance(0.5) ? 0 : 1;
			for (int k = 0; k < 2; k++) {
				int i = (first + k) % 2;
				f[i].act(in[i], f[1 - i], rng);
			}
			for (int i = 0; i < 2; i++) {
				f[i].move();
				arrows.addAll(f[i].shot);
				f[i].shot.clear();
				counters[i][0] += f[i].inWeb ? 1 : 0;
				counters[i][1] += f[i].inWater ? 1 : 0;
				counters[i][2] += f[i].onFire() ? 1 : 0;
			}
			for (SimArrow arrow : arrows) {
				arrow.tick(world, arrow.owner == f[0] ? f[1] : f[0], rng);
			}
			if (watcher != null) {
				for (SimArrow arrow : arrows) {
					if (arrow.done) {
						watcher.arrowDone(arrow);
					}
				}
			}
			arrows.removeIf(arrow -> arrow.done);
			world.tick();
			tick++;
		}
		int winner = f[0].dead() == f[1].dead() ? -1 : f[0].dead() ? 1 : 0;
		SideStats[] stats = new SideStats[2];
		for (int i = 0; i < 2; i++) {
			SimFighter s = f[i];
			stats[i] = new SideStats(s.lavaPours, s.waterPours, s.scoops, s.blocksPlaced, s.websPlaced, s.arrowsShot, s.arrowHits, s.lavaDamage,
				s.fireDamage, s.fallDamage, counters[i][0], counters[i][1], counters[i][2], s.hitDistance.clone());
		}
		return new Result(winner, tick, new float[] {f[0].health + f[0].absorption, f[1].health + f[1].absorption}, new float[] {f[0].damageDealt, f[1].damageDealt},
			new int[] {f[0].swings, f[1].swings}, new int[] {f[0].hits, f[1].hits}, new int[] {f[0].crits, f[1].crits},
			new int[] {f[0].sprintHits, f[1].sprintHits}, new int[] {f[0].strafeTicks, f[1].strafeTicks}, new int[] {f[0].blockedHits, f[1].blockedHits},
			new int[] {f[0].gapplesEaten, f[1].gapplesEaten}, stats);
	}

	/** Sees each tick of a fight before the inputs are applied (for diagnostics). */
	public interface Watcher {
		void tick(int tick, SimWorld world, SimFighter[] fighters, Inputs[] inputs, Side[] sides);

		/** An arrow finished its flight (hit something or stuck). */
		default void arrowDone(SimArrow arrow) {
		}
	}
}
