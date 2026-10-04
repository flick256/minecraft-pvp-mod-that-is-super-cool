package io.github.flick256.sparbot.core.sim;

import io.github.flick256.sparbot.core.act.InputShaper;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.brain.Policy;
import io.github.flick256.sparbot.core.math.Rng;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.Surroundings;

/**
 * A sword duel between two policies in the simulator. Each side sees the same {@link Observation} it
 * would get in game and its inputs go through the same {@link InputShaper} (ping, click and turn
 * limits) as a real bot's, so a policy that fights well here fights the same way in Minecraft.
 */
public final class DuelSim {
	/** A 24 x 24 walled square, like a duel arena. */
	public static final double ARENA_HALF_SIZE = 12.0;
	private static final double START_DISTANCE = 8.0;

	private DuelSim() {
	}

	/**
	 * How a fight went, from both sides (index 0 = the first policy).
	 *
	 * @param winner 0 or 1, or -1 when time ran out with both alive
	 */
	public record Result(int winner, int ticks, float[] health, float[] damage, int[] swings, int[] hits, int[] crits, int[] sprintHits) {
		/** Score from side {@code side}'s point of view: 1 for a win, 0 for a loss, health-based in between on a timeout. */
		public double score(int side) {
			if (winner >= 0) {
				return winner == side ? 1.0 : 0.0;
			}
			double mine = Math.max(0, health[side]);
			double theirs = Math.max(0, health[1 - side]);
			return 0.5 + 0.5 * (mine - theirs) / SimFighter.MAX_HEALTH;
		}
	}

	/** One side: a policy and the skill profile whose limits its inputs are held to. */
	public record Side(Policy policy, SkillProfile limits) {
	}

	public static Result fight(Side a, Side b, Loadout loadout, long seed, int maxTicks) {
		Rng rng = new Rng(seed);
		double offset = rng.nextDouble() * 4 - 2;
		SimFighter[] f = {
			new SimFighter(loadout, -START_DISTANCE / 2, offset, -90.0F),
			new SimFighter(loadout, START_DISTANCE / 2, -offset, 90.0F)
		};
		Side[] sides = {a, b};
		InputShaper[] shapers = {new InputShaper(a.limits(), rng.fork(), InputShaper.ABSOLUTE_MAX_CPS),
			new InputShaper(b.limits(), rng.fork(), InputShaper.ABSOLUTE_MAX_CPS)};
		a.policy().reset();
		b.policy().reset();
		int tick = 0;
		while (tick < maxTicks && !f[0].dead() && !f[1].dead()) {
			for (SimFighter fighter : f) {
				fighter.receiveKnockback();
			}
			Inputs[] in = new Inputs[2];
			for (int i = 0; i < 2; i++) {
				Observation obs = new Observation(tick, f[i].selfState(), f[1 - i].asTarget(1 - i), Surroundings.EMPTY);
				in[i] = shapers[i].shape(sides[i].policy().act(obs));
			}
			for (int i = 0; i < 2; i++) {
				f[i].look(in[i]);
			}
			// The server handles both players' clicks before moving anyone; whose packet is first varies.
			int first = rng.chance(0.5) ? 0 : 1;
			for (int k = 0; k < 2; k++) {
				int i = (first + k) % 2;
				if (in[i].attack()) {
					f[i].click(f[1 - i], rng);
				}
			}
			for (SimFighter fighter : f) {
				fighter.move(ARENA_HALF_SIZE);
			}
			tick++;
		}
		int winner = f[0].dead() == f[1].dead() ? -1 : f[0].dead() ? 1 : 0;
		return new Result(winner, tick, new float[] {f[0].health, f[1].health}, new float[] {f[0].damageDealt, f[1].damageDealt},
			new int[] {f[0].swings, f[1].swings}, new int[] {f[0].hits, f[1].hits}, new int[] {f[0].crits, f[1].crits},
			new int[] {f[0].sprintHits, f[1].sprintHits});
	}
}
