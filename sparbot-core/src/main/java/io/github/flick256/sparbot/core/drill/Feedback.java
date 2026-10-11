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
		int early = Math.max(1, (int) Math.ceil((0.9 - charge) * SWORD_CHARGE_TICKS));
		return Verdict.bad("early", "Too early: " + pct + "% charge. Wait about " + early + " tick" + (early == 1 ? "" : "s") + " more for the bar to fill");
	}

	/** A melee hit from {@code reach} blocks (eye to the nearest point of their hitbox). */
	public static Verdict reach(double reach, double mark) {
		String r = String.format(Locale.ROOT, "%.2f", reach);
		if (reach >= mark) {
			return Verdict.good(r + " blocks: " + (reach >= 2.85 ? "the very edge of reach" : "out at range"));
		}
		if (reach < 2.0) {
			return Verdict.bad("close", r + " blocks: far too close, they can hit you back. Back off between swings");
		}
		return Verdict.bad("close", r + " blocks: a little close (aim for " + String.format(Locale.ROOT, "%.1f", mark)
			+ " or more). Swing as they come into 3 blocks, not after");
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
		return sprintHit(sprinting, charge, null);
	}

	/**
	 * A melee hit, and whether it carried sprint knockback; {@code reset}: whether W was let go (or S pressed)
	 * since your last hit, read from your keys (null if unknown).
	 */
	public static Verdict sprintHit(boolean sprinting, double charge, Boolean reset) {
		if (sprinting && charge > 0.9) {
			return Verdict.good("Sprint hit: extra knockback");
		}
		if (sprinting) {
			return Verdict.bad("charge", "Sprinting, but only " + Math.round(charge * 100) + "% charged: no extra knockback");
		}
		if (Boolean.FALSE.equals(reset)) {
			return Verdict.bad("held", "Not sprinting: you held W the whole time. Let go of W right after each hit, then press it again");
		}
		if (Boolean.TRUE.equals(reset)) {
			return Verdict.bad("nosprint", "Not sprinting: you let go of W but weren't sprinting again yet. Press W again sooner");
		}
		return Verdict.bad("nosprint", "Not sprinting: let go of W right after your hit and press it again");
	}

	/**
	 * A W-tap: your hit (a sprint hit at full charge?) and letting go of W (or tapping S) just after it, read from
	 * your keys ({@link ClientFrames#wTap}). With a sprint key held (or toggle sprint) the game starts your sprint
	 * again by itself, so in 26.2 the tap is about spacing: letting go of W stops you running into them as they
	 * fly back, so the next hit lands from range too.
	 */
	public static Verdict wTap(boolean sprinting, double charge, ClientFrames.TapReading r) {
		Verdict hit = sprintHit(sprinting, charge, null);
		if (!hit.good()) {
			return hit;
		}
		if (r.offset() == null) {
			return Verdict.bad("notap", "Sprint hit, but no W-tap: let go of W for a moment right after the hit so you don't run into them");
		}
		int o = r.offset();
		if (o > 3) {
			return Verdict.bad("late", "Sprint hit, W let go " + o + " ticks after it: too late, let go as you hit");
		}
		if (r.held() > 6) {
			return Verdict.bad("held", "Sprint hit, but W off for " + (r.held() >= 20 ? "too long" : r.held() + " ticks") + ": tap it, then straight back on W");
		}
		return Verdict.good("Sprint hit + W-tap (off " + r.held() + " tick" + (r.held() == 1 ? "" : "s") + ")");
	}

	/** A hit taken on the ground and the jump reset read from your keys and movement ({@link ClientFrames#jumpReset}). */
	public static Verdict jumpReset(ClientFrames.JumpReading r) {
		Integer offset = r.offset();
		if (offset == null) {
			return r.inAir() ? Verdict.bad("air", "You were in the air when it hit: nothing to reset. Stay on the ground and jump as it lands")
				: Verdict.bad("none", "No jump: jump the moment their hit lands on you");
		}
		int o = offset;
		if (o == 0) {
			if (!r.forward()) {
				return Verdict.bad("nowasd", "Right tick, but you weren't holding W: the jump only cuts knockback while you sprint into them");
			}
			if (!r.sprinting()) {
				return Verdict.bad("nosprint", "Right tick, but you weren't sprinting: the jump only cuts knockback while you sprint into them");
			}
			return Verdict.good("Perfect jump reset!");
		}
		if (o > 0) {
			return Verdict.bad("late", "Jumped " + o + " tick" + (o == 1 ? "" : "s") + " late (" + o * 50
				+ " ms): the knockback had already lifted you. Jump as their sword hits");
		}
		if (o == -1) {
			return Verdict.bad("early", "1 tick early, so close: you were just off the ground when it landed");
		}
		return Verdict.bad("early", "Jumped " + -o + " ticks early: you were already in the air and took the full knockback. Wait for the hit");
	}

	/** Your hit and the S-tap around it ({@link ClientFrames#sTap}). */
	public static Verdict sTap(ClientFrames.TapReading r) {
		if (r.offset() == null) {
			return Verdict.bad("none", "No S-tap: tap S as you hit, so you're out of their reach when they swing back");
		}
		int o = r.offset();
		if (o > 3) {
			return Verdict.bad("late", "S " + o + " ticks after your hit: too late, tap it as you swing");
		}
		if (r.held() > 6) {
			return Verdict.bad("held", "Held S for " + (r.held() >= 20 ? "too long" : r.held() + " ticks") + ": just tap it, then W back in");
		}
		return Verdict.good("S-tap! (" + (o < 0 ? -o + " tick" + (o == -1 ? "" : "s") + " before the hit" : o == 0 ? "with the hit" : o + " after") + ", held "
			+ r.held() + ")");
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

	/** A hit taken while practising spacing: you stood in their reach. */
	public static Verdict spacing() {
		return Verdict.bad("hit", "Hit back: you stayed in their reach. S-tap as you swing, step back in as your charge fills");
	}

	/** One of the bot's swings at you, with your shield up or not. */
	public static Verdict block(boolean blocked) {
		return blocked ? Verdict.good("Blocked between swings") : Verdict.bad("open", "Hit: raise your shield as soon as you have swung");
	}

	/** A hit blocked with a shield that was never lowered to swing: that's turtling, not block-hitting. */
	public static Verdict blockNoSwing() {
		return Verdict.bad("turtle", "Blocked, but you haven't swung for 2 s: lower the shield and hit when your charge is full");
	}

	/** Client ticks between the axe hit and the next for a stun (a double click); any slower is a plain shield break. */
	public static final int STUN_TICKS = 4;

	/**
	 * Their shield disabled by your axe, and your next hit {@code ticks} client ticks after the axe hit (null:
	 * none in time). A stun is the double click: axe, then the next hit at once, before they can react; a hit
	 * any later is only a shield break.
	 */
	public static Verdict stun(Integer ticks) {
		if (ticks == null) {
			return Verdict.bad("none", "Shield down, but no second hit: double-click, axe then hit again at once");
		}
		if (ticks <= STUN_TICKS) {
			return Verdict.good("Stun! Second hit " + ticks * 50 + " ms after the axe");
		}
		return Verdict.bad("slow", "Second hit " + ticks * 50 + " ms after the axe: that's a shield break, not a stun. Double-click: axe, then hit again at once");
	}

	/** A block with your shield ({@code lost}: your shield was disabled by their axe instead). */
	public static Verdict shieldDefence(boolean lost) {
		return lost ? Verdict.bad("axe", "Shield disabled: drop it (or strafe away) when they swap to an axe")
			: Verdict.good("Blocked, shield kept");
	}

	/** A healing pot and how much of its splash reached you (0-1). */
	public static Verdict pot(double intensity) {
		int pct = (int) Math.round(intensity * 100);
		if (intensity >= 0.9) {
			return Verdict.good("Pot: " + pct + "% of the heal");
		}
		return Verdict.bad("splash", "Pot: only " + pct + "% of the heal. Look down at your feet as you throw");
	}

	/** A healing pot thrown with only {@code missing} health gone: most of it is wasted. */
	public static Verdict potWasted(double missing) {
		return Verdict.bad("wasted", String.format(Locale.ROOT, "Pot wasted: only %.1f hearts missing. Pot when you're down 3 hearts or more", missing / 2));
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
		return arrow(hit, 1, 15);
	}

	/** An arrow, its draw (0-1, from its speed) and the range it was shot from. */
	public static Verdict arrow(boolean hit, double draw, double range) {
		if (draw < 0.9) {
			return Verdict.bad("draw", (hit ? "Hit, but" : "Missed:") + " only " + Math.round(Math.min(1, draw) * 100)
				+ "% drawn. Hold the draw a full second");
		}
		if (!hit) {
			return Verdict.bad("miss", String.format(Locale.ROOT, "Missed from %.0f blocks: aim a little above them and ahead of where they're going", range));
		}
		if (range < 8) {
			return Verdict.bad("close", String.format(Locale.ROOT, "Hit, but from only %.0f blocks: the drill is shots at range (8 or more)", range));
		}
		return Verdict.good(String.format(Locale.ROOT, "Arrow hit from %.0f blocks!", range));
	}

	/** A rod cast: hooked or not, cast from {@code distance} blocks. */
	public static Verdict rod(boolean hooked, double distance) {
		String d = String.format(Locale.ROOT, "%.1f", distance);
		if (hooked && distance < 3.5) {
			return Verdict.bad("close", "Hooked, but from " + d + " blocks: cast as they come in, from 4 to 8");
		}
		return hooked ? Verdict.good("Hooked from " + d + " blocks!")
			: Verdict.bad("miss", "Missed from " + d + " blocks: cast from 4 to 8, a little above their head");
	}

	/** A lava pour: did it set them alight, and was it scooped back ({@code seconds} after the pour)? */
	public static Verdict lava(boolean lit, boolean scooped, double seconds) {
		if (lit && scooped) {
			return Verdict.good(String.format(Locale.ROOT, "Set alight, lava back in %.1f s", seconds));
		}
		if (lit) {
			return Verdict.bad("lying", "Set alight, but you left the lava lying: scoop it straight back, it burns you too");
		}
		return Verdict.bad("miss", "No fire: pour at their feet from 2 to 4 blocks, as they stand or eat");
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
