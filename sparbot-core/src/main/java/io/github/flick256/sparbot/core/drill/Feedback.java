package io.github.flick256.sparbot.core.drill;

import java.util.Locale;

/**
 * Judges one attempt at a skill the moment it happens and says, in a few words, what was right or what to
 * change. Shared by the drills (every rep) and the live coach in fights against bots. Pure logic: the game
 * measures, this decides.
 *
 * <p>Vanilla 26.2 facts used: a hit counts as fully charged (crits, sprint knockback) above 0.9 charge; a
 * crit also needs falling ({@code fallDistance > 0}), not on the ground and not sprinting; a sprint hit
 * stops the sprint, so the next is only a sprint hit again after W is let go and pressed (or sprint
 * pressed) again; a sword charges in 12.5 ticks; a melee hit reaches 3 blocks.
 */
public final class Feedback {
	private Feedback() {
	}

	/**
	 * The verdict on one rep.
	 *
	 * @param good whether it was done right
	 * @param text what to tell the player (a few words)
	 * @param reason a short key for what went wrong ("" when good), so a stage can say what went wrong most
	 */
	public record Verdict(boolean good, String text, String reason) {
		static Verdict good(String text) {
			return new Verdict(true, text, "");
		}

		static Verdict bad(String reason, String text) {
			return new Verdict(false, text, reason);
		}
	}

	/** Ticks a sword takes to charge fully. */
	public static final double SWORD_CHARGE_TICKS = 12.5;

	/** A melee hit and its charge (0-1). */
	public static Verdict charge(double charge) {
		int pct = (int) Math.round(charge * 100);
		if (charge > 0.9) {
			return Verdict.good("Full charge (" + pct + "%)");
		}
		int early = (int) Math.ceil((0.95 - charge) * SWORD_CHARGE_TICKS);
		return Verdict.bad("early", "Too early: " + pct + "% charge. Wait about " + early + " tick" + (early == 1 ? "" : "s") + " more for the bar to fill");
	}

	/** A melee hit from {@code reach} blocks (eye to the nearest point of their hitbox). */
	public static Verdict reach(double reach, double mark) {
		String r = String.format(Locale.ROOT, "%.2f", reach);
		if (reach >= mark) {
			return Verdict.good(r + " blocks: edge of reach");
		}
		if (reach < 2.0) {
			return Verdict.bad("close", r + " blocks: far too close, they can hit you back. Back off between swings");
		}
		return Verdict.bad("close", r + " blocks: a little close. Swing as they come into 3 blocks, not after");
	}

	/** A melee hit, and why it was or wasn't a crit. */
	public static Verdict crit(boolean crit, double charge, boolean onGround, boolean falling, boolean sprinting) {
		if (crit) {
			return Verdict.good("Critical hit!");
		}
		if (charge <= 0.9) {
			return Verdict.bad("charge", "No crit: only " + Math.round(charge * 100) + "% charged. Jump once the bar is nearly full");
		}
		if (onGround) {
			return Verdict.bad("ground", "No crit: you were on the ground. Jump, then hit on the way down");
		}
		if (sprinting) {
			return Verdict.bad("sprint", "No crit: you were sprinting. Let go of W for a moment before you jump");
		}
		if (!falling) {
			return Verdict.bad("rising", "No crit: you hit on the way up. Wait until you start to fall");
		}
		return Verdict.bad("other", "No crit");
	}

	/** A melee hit, and whether it carried sprint knockback. */
	public static Verdict sprintHit(boolean sprinting, double charge) {
		if (sprinting && charge > 0.9) {
			return Verdict.good("Sprint hit: extra knockback");
		}
		if (sprinting) {
			return Verdict.bad("charge", "Sprinting, but only " + Math.round(charge * 100) + "% charged: no extra knockback");
		}
		return Verdict.bad("nosprint", "Not sprinting: let go of W right after your hit and press it again");
	}

	/**
	 * A hit taken on the ground, and when you jumped relative to it ({@code offset} ticks: negative is before,
	 * null is no jump within a few ticks).
	 */
	public static Verdict jumpReset(Integer offset) {
		if (offset == null) {
			return Verdict.bad("none", "No jump: jump the moment their hit lands on you");
		}
		int o = offset;
		if (o >= -1 && o <= 1) {
			return Verdict.good(o == 0 ? "Perfect jump reset!" : "Jump reset (" + Math.abs(o) + " tick " + (o < 0 ? "early" : "late") + ")");
		}
		if (o == 2) {
			return Verdict.good("Jump reset, just: 2 ticks late");
		}
		if (o > 0) {
			return Verdict.bad("late", "Jumped " + o + " ticks late (" + o * 50 + " ms): jump as their sword hits, not after");
		}
		return Verdict.bad("early", "Jumped " + -o + " ticks early: you were already in the air. Wait for the hit");
	}

	/** A combo that ended ({@code hits} in a row without one back) against the stage's mark. */
	public static Verdict combo(int hits, int mark) {
		if (hits >= mark) {
			return Verdict.good(hits + "-hit combo!");
		}
		return Verdict.bad("broken", hits + "-hit combo, broken. Follow their knockback and W-tap so each hit lands from the edge of reach");
	}

	/** A swing, on target or not. */
	public static Verdict aim(boolean hit) {
		return hit ? Verdict.good("On target") : Verdict.bad("miss", "Missed: keep your crosshair on them and only swing when it is");
	}

	/**
	 * Their swing missed: did you punish it? {@code ticks} after the miss you landed a hit (null: you didn't
	 * within the window).
	 */
	public static Verdict punish(Integer ticks, int window) {
		if (ticks == null) {
			return Verdict.bad("slow", "Too slow: when they swing and miss, step in and hit before they recharge");
		}
		if (ticks <= window) {
			return Verdict.good("Punished in " + ticks * 50 + " ms!");
		}
		return Verdict.bad("slow", "Punished, but late (" + ticks * 50 + " ms): go the moment their arm swings past you");
	}

	/** An exchange: who hit first. */
	public static Verdict firstHit(boolean yours, double reach) {
		if (yours) {
			return Verdict.good(String.format(Locale.ROOT, "First hit (from %.2f)", reach));
		}
		return Verdict.bad("second", "They hit first: swing as they come into 3 blocks, a sprint step before they do");
	}

	/** A hit traded, either way. */
	public static Verdict trade(boolean yours) {
		return yours ? Verdict.good("Your hit") : Verdict.bad("hit", "Their hit: tap S as you swing so you're out of their reach when they swing back");
	}

	/** One of the bot's swings at you, with your shield up or not. */
	public static Verdict block(boolean blocked) {
		return blocked ? Verdict.good("Blocked") : Verdict.bad("open", "Hit: raise your shield as soon as you have swung");
	}

	/** Their shield disabled by your axe, and whether your follow-up came {@code ticks} later (null: none in time). */
	public static Verdict stun(Integer ticks) {
		if (ticks == null) {
			return Verdict.bad("slow", "Shield down, but no follow-up: swap to your sword and hit straight away");
		}
		return Verdict.good("Stun punished in " + ticks * 50 + " ms!");
	}

	/** A block with your shield ({@code lost}: your shield was disabled by their axe instead). */
	public static Verdict shieldDefence(boolean lost) {
		return lost ? Verdict.bad("axe", "Shield disabled: drop it (or strafe away) when they swap to an axe")
			: Verdict.good("Blocked, shield kept");
	}

	/** A healing pot and how much of its splash reached you (0-1). */
	public static Verdict pot(double intensity) {
		int pct = (int) Math.round(intensity * 100);
		if (intensity >= 0.8) {
			return Verdict.good("Pot: " + pct + "% of the heal");
		}
		return Verdict.bad("splash", "Pot: only " + pct + "% of the heal. Look down at your feet as you throw");
	}

	/** Out of a web after {@code seconds}, against the stage's mark. */
	public static Verdict web(double seconds, double mark) {
		String s = String.format(Locale.ROOT, "%.1f s", seconds);
		if (seconds <= mark) {
			return Verdict.good("Out of the web in " + s);
		}
		return Verdict.bad("slow", "Out of the web in " + s + ": water on it (or break it) the moment you are caught");
	}

	/** An arrow: on target or not. */
	public static Verdict arrow(boolean hit) {
		return hit ? Verdict.good("Arrow hit!") : Verdict.bad("miss", "Missed: draw fully and lead them by their speed");
	}

	/** A rod cast: hooked or not. */
	public static Verdict rod(boolean hooked) {
		return hooked ? Verdict.good("Hooked!") : Verdict.bad("miss", "Missed: cast from 4 to 8 blocks, a little above them");
	}

	/** A new totem in your offhand {@code seconds} after the last popped, against the stage's mark. */
	public static Verdict retotem(double seconds, double mark) {
		String s = String.format(Locale.ROOT, "%.1f s", seconds);
		return seconds <= mark ? Verdict.good("New totem in " + s) : Verdict.bad("slow", "New totem in " + s + ": keep one in the hotbar and swap with F");
	}

	/** Something that simply counts when it happens (a blast that hurt, a smash, setting them alight). */
	public static Verdict landed(String text) {
		return Verdict.good(text);
	}
}
