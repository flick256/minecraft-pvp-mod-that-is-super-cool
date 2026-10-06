package io.github.flick256.sparbot.core.drill;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * A skill drill: one PvP skill practised against a bot set up to train it, in three stages that get
 * harder (a dummy, then a moving bot, then a bot that fights back), each a timed round scored on that one
 * skill. Passing stage one earns bronze, two silver, three gold.
 *
 * @param discipline the group it is listed under (Sword, Shield, NoDebuff, UHC, Crystal, Cart, Mace)
 * @param skill what it trains, in a few words
 * @param how how to do it, one or two sentences
 * @param kit the player's kit; {@code botKit} the bot's
 */
public record Drill(String id, String name, String discipline, String skill, String how, String kit, String botKit, Metric metric, int seconds,
	List<Stage> stages) {

	/** What the bot does in a stage. */
	public enum Pattern {
		/** Stands still, facing you: a training dummy. */
		STILL,
		/** Walks from side to side facing you, never attacking. */
		PACE,
		/** Walks from side to side with its shield raised, never attacking. */
		SHIELD,
		/** Fights you with its brain, held to the stage's tier, tactic weights and techniques. */
		FIGHT
	}

	/**
	 * One stage.
	 *
	 * @param profile the bot's skill tier
	 * @param tactics tactic weights for a fighting bot (unlisted: 1, 0: never)
	 * @param techniquesOff techniques the bot doesn't use
	 * @param pass the score to reach (or stay under, for a metric where lower is better)
	 * @param distance how far apart you start
	 */
	public record Stage(String label, String profile, Pattern pattern, Map<String, Double> tactics, List<String> techniquesOff, double pass,
		double distance) {
	}

	/** How a stage is scored. */
	public enum Metric {
		FULL_CHARGE("full-charge hits", true, 6, t -> t.share(t.fullChargeHits, t.hits), t -> t.hits),
		REACH("average reach", false, 6, t -> t.hits == 0 ? 0 : t.reachSum / t.hits, t -> t.hits),
		CRITS("critical hits", true, 6, t -> t.share(t.crits, t.hits), t -> t.hits),
		SPRINT_HITS("sprint hits (W-taps)", true, 6, t -> t.share(t.sprintHits, t.hits), t -> t.hits),
		HIT_SHARE("hits landed of all hits traded", true, 6, t -> t.share(t.hits, t.hits + t.hitsTaken), t -> t.hits + t.hitsTaken),
		COMBO("longest combo", false, 1, t -> t.bestCombo, t -> t.hits),
		JUMP_RESETS("jump resets", true, 5, t -> t.share(t.jumpResets, t.hitsTakenOnGround), t -> t.hitsTakenOnGround),
		ACCURACY("swings that hit", true, 8, t -> t.share(t.hits, Math.max(t.hits, t.swings)), t -> t.swings),
		BLOCKED("hits blocked", true, 5, t -> t.share(t.blocked, t.blocked + t.hitsTaken), t -> t.blocked + t.hitsTaken),
		STUN_FOLLOW_UPS("shield stuns followed up", false, 1, t -> t.stunFollowUps, t -> t.stuns),
		SHIELD_DEFENCE("shield score (+1 a block, -3 a lost shield)", false, 1, t -> t.blocked - 3.0 * t.shieldsLost, t -> t.blocked + t.shieldsLost),
		POT_ACCURACY("healing per pot", true, 3, t -> t.pots == 0 ? 0 : t.potIntensity / t.pots, t -> t.pots),
		WEB_ESCAPE("seconds to get out of a web", false, 1, t -> t.webEscapes == 0 ? Double.POSITIVE_INFINITY : t.webTicks / 20.0 / t.webEscapes,
			t -> t.webEscapes, true),
		BURNING("time the bot was on fire", true, 1, t -> t.share(t.botBurningTicks, t.ticks), t -> t.ticks),
		BOW_HITS("arrows that hit", true, 4, t -> t.share(t.arrowHits, t.arrowsShot), t -> t.arrowsShot),
		ROD_HOOKS("rods that hooked", true, 4, t -> t.share(t.rodHooks, t.rodCasts), t -> t.rodCasts),
		CRYSTAL_BLASTS("crystal blasts that hurt", false, 1, t -> t.crystalBlasts, t -> t.crystalBlasts),
		ANCHOR_BLASTS("anchor blasts that hurt", false, 1, t -> t.anchorBlasts, t -> t.anchorBlasts),
		CART_BLASTS("cart blasts that hurt", false, 1, t -> t.cartBlasts, t -> t.cartBlasts),
		SMASHES("mace smashes", false, 1, t -> t.smashes, t -> t.smashes),
		RETOTEM("seconds to a new totem", false, 1, t -> t.retotems == 0 ? Double.POSITIVE_INFINITY : t.retotemTicks / 20.0 / t.retotems, t -> t.retotems,
			true);

		private final String label;
		private final boolean percent;
		private final int minSamples;
		private final ToDoubleFunction<DrillTally> value;
		private final ToIntFunction<DrillTally> samples;
		private final boolean lowerIsBetter;

		Metric(String label, boolean percent, int minSamples, ToDoubleFunction<DrillTally> value, ToIntFunction<DrillTally> samples) {
			this(label, percent, minSamples, value, samples, false);
		}

		Metric(String label, boolean percent, int minSamples, ToDoubleFunction<DrillTally> value, ToIntFunction<DrillTally> samples, boolean lowerIsBetter) {
			this.label = label;
			this.percent = percent;
			this.minSamples = minSamples;
			this.value = value;
			this.samples = samples;
			this.lowerIsBetter = lowerIsBetter;
		}

		public String label() {
			return label;
		}

		public boolean lowerIsBetter() {
			return lowerIsBetter;
		}

		public int minSamples() {
			return minSamples;
		}

		public double value(DrillTally tally) {
			return value.applyAsDouble(tally);
		}

		public int samples(DrillTally tally) {
			return samples.applyAsInt(tally);
		}

		/** Whether a stage with this tally reaches its pass mark (with enough tries to count). */
		public boolean passes(DrillTally tally, double pass) {
			if (samples(tally) < minSamples) {
				return false;
			}
			double v = value(tally);
			return lowerIsBetter ? v <= pass : v >= pass;
		}

		/** A score or pass mark as shown: 64%, 2.71, 1.2 s, 5. */
		public String format(double v) {
			if (Double.isInfinite(v)) {
				return "-";
			}
			if (percent) {
				return Math.round(v * 100) + "%";
			}
			if (lowerIsBetter) {
				return String.format(Locale.ROOT, "%.1f s", v);
			}
			if (this == REACH) {
				return String.format(Locale.ROOT, "%.2f", v);
			}
			return v == Math.rint(v) ? Long.toString(Math.round(v)) : String.format(Locale.ROOT, "%.1f", v);
		}
	}

	/** Bronze, silver, gold: the stages passed. */
	public static String medal(int stagesPassed) {
		return switch (stagesPassed) {
			case 1 -> "bronze";
			case 2 -> "silver";
			case 3 -> "gold";
			default -> "none";
		};
	}
}
