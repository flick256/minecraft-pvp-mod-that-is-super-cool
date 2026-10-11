package io.github.flick256.sparbot.core.drill;

import java.util.List;
import java.util.Map;

/**
 * A skill drill: one PvP skill taught in three stages against a bot set up for it.
 * <ol>
 *   <li><b>Learn</b>: on-screen cues say exactly when to act (now jump, now swing), against a slow and
 *       predictable bot;</li>
 *   <li><b>Practice</b>: the same bot without the cues;</li>
 *   <li><b>Under pressure</b>: a bot that fights back, the skill done mid-fight.</li>
 * </ol>
 * Every attempt (a rep) is judged the moment it happens and told to you with what went wrong (jumped 3 ticks
 * late, swung at 70% charge, sprinting so no crit); a stage is passed as soon as enough good reps are in. A
 * stage that runs out of time starts again with a tip, so a drill is never just failed, and you can't die in
 * one. Passing stage one earns bronze, two silver, three gold.
 *
 * @param discipline the group it is listed under (Sword, Shield, NoDebuff, UHC, Crystal, Cart, Mace)
 * @param skill what it trains, in a few words
 * @param how how to do it, one or two sentences
 * @param tips the key points, shown when the drill starts and after a stage runs out of time
 * @param kit the player's kit; {@code botKit} the bot's
 * @param rep what counts as an attempt, and what makes it a good one
 */
public record Drill(String id, String name, String discipline, String skill, String how, List<String> tips, String kit, String botKit, Rep rep,
	List<Stage> stages) {

	/** What the bot does in a stage. */
	public enum Pattern {
		/** Stands still, facing you: a training dummy. */
		STILL,
		/** Walks from side to side facing you, never attacking. */
		PACE,
		/** Walks from side to side with its shield raised, never attacking. */
		SHIELD,
		/** Keeps just inside its reach and swings on a steady rhythm you can learn (for jump resets and blocking). */
		ATTACKER,
		/** Steps in and swings from just too far away, then stands there recharging: a missed swing to punish. */
		WHIFFER,
		/** Strafes and jumps at random, close in, never attacking: something to track. */
		STRAFER,
		/** Starts apart and sprints in to hit you: whoever hits first wins the exchange. */
		RUSHER,
		/** Fights you with its brain, held to the stage's tier, tactic weights and techniques. */
		FIGHT
	}

	/**
	 * One stage.
	 *
	 * @param label shown as the stage's name
	 * @param profile the bot's skill tier
	 * @param pattern what the bot does
	 * @param tactics tactic weights for a fighting bot (unlisted: 1, 0: never)
	 * @param techniquesOff techniques the bot doesn't use
	 * @param reps good reps needed to pass
	 * @param seconds how long a try at the stage lasts before it starts again
	 * @param distance how far apart you start
	 * @param cues whether the on-screen cues say when to act
	 * @param target the rep's mark where it has one (seconds out of a web, the combo length...), else 0
	 */
	public record Stage(String label, String profile, Pattern pattern, Map<String, Double> tactics, List<String> techniquesOff, int reps, int seconds,
		double distance, boolean cues, double target) {
	}

	/**
	 * What counts as one attempt in a drill, and how the attempts add up. A strict rep takes a good one back
	 * for each bad one (so swinging wildly can't pass an aim drill); otherwise a bad rep only costs time.
	 */
	public enum Rep {
		CHARGE("full-charge hits", false),
		REACH("hits from the edge of reach", false),
		CRIT("critical hits", false),
		SPRINT_HIT("sprint hits", false),
		JUMP_RESET("jump resets", false),
		COMBO("combos", false),
		AIM("swings on target", true),
		PUNISH("punished misses", false),
		FIRST_HIT("first hits", false),
		S_TAP("S-taps", false),
		BLOCK("hits blocked", true),
		STUN("shield stuns", false),
		SHIELD_DEFENCE("blocks without losing the shield", true),
		POT("full pots", false),
		WEB("quick web escapes", false),
		LAVA("times you set them alight", false),
		ARROW("arrows on target", false),
		ROD("rod hooks", false),
		CRYSTAL("crystal blasts", false),
		ANCHOR("anchor blasts", false),
		RETOTEM("quick re-totems", false),
		CART("cart blasts", false),
		SMASH("mace smashes", false);

		private final String label;
		private final boolean strict;

		Rep(String label, boolean strict) {
			this.label = label;
			this.strict = strict;
		}

		public String label() {
			return label;
		}

		public boolean strict() {
			return strict;
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
