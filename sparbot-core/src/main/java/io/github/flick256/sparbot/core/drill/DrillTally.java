package io.github.flick256.sparbot.core.drill;

/**
 * What happened in one stage of a drill, counted from the player's side: the game feeds it events (a hit
 * on the bot and how it was made, a hit taken, a pot, a web, a blast) and {@link Drill.Metric} turns the
 * counts into the stage's score.
 */
public final class DrillTally {
	public int ticks;
	// Your melee hits on the bot.
	public int swings;
	public int hits;
	public int fullChargeHits;
	public int crits;
	public int sprintHits;
	public double reachSum;
	public int combo;
	public int bestCombo;
	// The bot's melee hits on you.
	public int hitsTaken;
	public int hitsTakenOnGround;
	public int jumpResets;
	public int blocked;
	public int shieldsLost;
	// Shields: yours on the bot's.
	public int stuns;
	public int stunFollowUps;
	// Healing pots.
	public int pots;
	public double potIntensity;
	// Webs.
	public int webEscapes;
	public int webTicks;
	// The bot on fire.
	public int botBurningTicks;
	// Ranged.
	public int arrowsShot;
	public int arrowHits;
	public int rodCasts;
	public int rodHooks;
	// Blasts and smashes on the bot.
	public int crystalBlasts;
	public int anchorBlasts;
	public int cartBlasts;
	public int smashes;
	// Totems.
	public int retotems;
	public int retotemTicks;

	// A drill's reps.
	public int reps;
	public int goodReps;
	/** Bad reps by what went wrong (a {@link Feedback.Verdict#reason()}). */
	public final java.util.Map<String, Integer> mistakes = new java.util.LinkedHashMap<>();
	/** The text of the most common mistake, for the stage's summary. */
	private final java.util.Map<String, String> mistakeText = new java.util.HashMap<>();

	/** A drill rep: counted, and a good one adds to the pass; a bad one takes one back when the rep is strict. */
	public void rep(Feedback.Verdict verdict, boolean strict) {
		reps++;
		if (verdict.good()) {
			goodReps++;
		} else {
			mistakes.merge(verdict.reason(), 1, Integer::sum);
			mistakeText.put(verdict.reason(), verdict.text());
			if (strict && goodReps > 0) {
				goodReps--;
			}
		}
	}

	/** What went wrong most often in the bad reps, as last said (null if nothing did). */
	public String commonestMistake() {
		return mistakes.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(e -> mistakeText.get(e.getKey()) + " (" + e.getValue()
			+ "x)").orElse(null);
	}

	/** A melee hit of yours landed on the bot. */
	public void hit(double charge, boolean crit, boolean sprint, double reach) {
		hits++;
		if (charge >= 0.9) {
			fullChargeHits++;
		}
		if (crit) {
			crits++;
		}
		if (sprint) {
			sprintHits++;
		}
		reachSum += reach;
		combo++;
		bestCombo = Math.max(bestCombo, combo);
	}

	/** The bot's melee hit landed on you (not blocked). */
	public void hitTaken(boolean onGround) {
		hitsTaken++;
		combo = 0;
		if (onGround) {
			hitsTakenOnGround++;
		}
	}

	public double share(int part, int whole) {
		return whole == 0 ? 0 : part / (double) whole;
	}
}
