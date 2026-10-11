package io.github.flick256.sparbot.core.drill;

import java.util.List;

/**
 * Reads techniques from a player's client ticks ({@link ClientFrame}): the jump reset, the S-tap and the
 * W-tap, from the keys they really pressed and how they really moved, not from guesses.
 *
 * <p>Vanilla 26.2 facts used: a jump sets the vertical speed to {@code max(0.42, current)} and, while
 * sprinting, adds 0.2 towards where you face ({@code LivingEntity#jumpFromGround}); knockback on the ground
 * lifts you by at most 0.4 ({@code LivingEntity#knockback}), in the air it only pushes. So a take-off that
 * rises 0.42 in its first tick is a jump and one that rises 0.4 or less is knockback; a jump on the very
 * client tick the knockback arrives (the jump reset) takes off at 0.42 and soaks up the push, while a jump
 * any earlier leaves you in the air for the push to land in full. (The server's own jump counter can't
 * tell: it counts any take-off, knockback included.)
 */
public final class ClientFrames {
	private ClientFrames() {
	}

	/** The first-tick rise of a real jump (0.42, more with Jump Boost); knockback lifts 0.4 at most. */
	public static final double JUMP_RISE = 0.41;
	/** How many client ticks after the hit was sent the knockback is looked for. */
	public static final int SEARCH = 40;

	/** Not decided yet (more client ticks are needed). */
	public static final Integer PENDING = Integer.MIN_VALUE;

	/**
	 * A jump reset read from the frames: {@code offset} client ticks from the knockback to the jump (0 is the
	 * jump reset; negative, already in the air when it landed; positive, jump pressed after it), or null for no
	 * jump at all; whether you held W and were sprinting when it landed; whether you were in the air for some
	 * other reason (not a jump), so there was nothing to reset.
	 */
	public record JumpReading(Integer offset, boolean forward, boolean sprinting, boolean inAir) {
		public boolean pending() {
			return PENDING.equals(offset);
		}
	}

	private static final JumpReading WAIT = new JumpReading(PENDING, false, false, false);

	static double rise(List<ClientFrame> f, int i) {
		return f.get(i).y() - f.get(i - 1).y();
	}

	static boolean takeoff(List<ClientFrame> f, int i) {
		return i >= 1 && f.get(i - 1).onGround() && !f.get(i).onGround() && rise(f, i) > 0;
	}

	static boolean jumped(List<ClientFrame> f, int i) {
		return takeoff(f, i) && rise(f, i) >= JUMP_RISE;
	}

	/** Movement in tick {@code i} along the knockback direction ({@code dx, dz}: unit vector, attacker to you). */
	static double along(List<ClientFrame> f, int i, double dx, double dz) {
		if (i < 1) {
			return 0;
		}
		return (f.get(i).x() - f.get(i - 1).x()) * dx + (f.get(i).z() - f.get(i - 1).z()) * dz;
	}

	/** A sudden push away from the attacker: knockback (your own keys can't change your speed this much in a tick). */
	static boolean pushed(List<ClientFrame> f, int i, double dx, double dz) {
		double a = along(f, i, dx, dz);
		return i >= 2 && a >= 0.05 && a - along(f, i - 1, dx, dz) >= 0.15;
	}

	/**
	 * Reads the jump reset of a hit whose knockback was sent while the client was on tick {@code hitTick}.
	 *
	 * @param f the player's recent frames, oldest first
	 * @param dx the knockback direction (unit vector from the attacker to the player)
	 */
	public static JumpReading jumpReset(List<ClientFrame> f, int hitTick, double dx, double dz) {
		int from = indexOf(f, hitTick);
		if (from < 0) {
			return f.isEmpty() || f.get(f.size() - 1).tick() < hitTick ? WAIT : new JumpReading(null, false, false, false);
		}
		for (int i = Math.max(1, from); i < f.size() && f.get(i).tick() <= hitTick + SEARCH; i++) {
			ClientFrame at = f.get(i);
			if (jumped(f, i)) {
				// A jump after the hit was sent: on the knockback's tick (it soaked it up), or before it, in which case
				// the push lands a few ticks later while still in the air.
				for (int k = i + 1; k <= i + 10; k++) {
					if (k >= f.size()) {
						return WAIT;
					}
					if (f.get(k).onGround()) {
						break;
					}
					if (pushed(f, k, dx, dz)) {
						return new JumpReading(i - k, f.get(k).forward(), f.get(k).sprinting(), false);
					}
				}
				return new JumpReading(0, at.forward(), f.get(i - 1).sprinting() || at.sprinting(), false);
			}
			if (takeoff(f, i) && rise(f, i) >= 0.3) {
				// Lifted by the knockback without a jump: was jump pressed just after (too late)?
				for (int p = i + 1; p <= i + 8; p++) {
					if (p >= f.size()) {
						return WAIT;
					}
					if (f.get(p).jump() && !f.get(p - 1).jump()) {
						return new JumpReading(p - i, at.forward(), at.sprinting(), false);
					}
				}
				return new JumpReading(null, at.forward(), at.sprinting(), false);
			}
			if (pushed(f, i, dx, dz)) {
				if (f.get(i - 1).onGround()) {
					return new JumpReading(null, at.forward(), at.sprinting(), false);
				}
				// Pushed in the air: did a jump put you there?
				for (int j = i - 1; j >= 1 && j >= i - 15; j--) {
					if (jumped(f, j)) {
						return new JumpReading(j - i, f.get(j).forward(), f.get(j).sprinting(), false);
					}
					if (f.get(j).onGround()) {
						break;
					}
				}
				return new JumpReading(null, at.forward(), at.sprinting(), true);
			}
		}
		ClientFrame last = f.get(f.size() - 1);
		return last.tick() <= hitTick + SEARCH ? WAIT : new JumpReading(null, false, false, true);
	}

	/**
	 * An S-tap around a click on client tick {@code clickTick}: {@code offset} ticks from the click to pressing S
	 * (negative, just before) and how many ticks it was held; null offset for no S in the window.
	 */
	public record TapReading(Integer offset, int held) {
		public boolean pending() {
			return PENDING.equals(offset);
		}
	}

	/** How early before the click and how late after it an S press still belongs to it. */
	public static final int TAP_BEFORE = 3;
	public static final int TAP_AFTER = 6;

	public static TapReading sTap(List<ClientFrame> f, int clickTick) {
		return tap(f, clickTick, ClientFrame::backward);
	}

	/** A W-tap after a click: W let go (or S pressed), read like {@link #sTap}. */
	public static TapReading wTap(List<ClientFrame> f, int clickTick) {
		return tap(f, clickTick, frame -> !frame.forward() || frame.backward());
	}

	/** A short press of something ({@code down}) around a click: when it started and how long it lasted. */
	static TapReading tap(List<ClientFrame> f, int clickTick, java.util.function.Predicate<ClientFrame> down) {
		int last = f.isEmpty() ? Integer.MIN_VALUE : f.get(f.size() - 1).tick();
		if (last < clickTick + TAP_AFTER + 10) {
			return new TapReading(PENDING, 0);
		}
		int start = Integer.MIN_VALUE;
		for (ClientFrame frame : f) {
			int t = frame.tick();
			if (t >= clickTick - TAP_BEFORE && t <= clickTick + TAP_AFTER && down.test(frame)) {
				start = t;
				break;
			}
		}
		if (start == Integer.MIN_VALUE) {
			return new TapReading(null, 0);
		}
		int held = 0;
		for (ClientFrame frame : f) {
			if (frame.tick() >= start) {
				if (!down.test(frame)) {
					break;
				}
				held++;
			}
		}
		// Down from before the window opened: holding it, not tapping it.
		int i = indexOf(f, clickTick - TAP_BEFORE);
		if (i > 0 && start == clickTick - TAP_BEFORE && down.test(f.get(i - 1))) {
			held += 20;
		}
		return new TapReading(start - clickTick, held);
	}

	/** Whether W was let go (or S pressed) at some point between two client ticks: the sprint reset of a W-tap. */
	public static Boolean sprintReset(List<ClientFrame> f, int fromTick, int toTick) {
		if (f.isEmpty() || f.get(0).tick() > fromTick) {
			return null;
		}
		for (ClientFrame frame : f) {
			if (frame.tick() > fromTick && frame.tick() <= toTick && (!frame.forward() || frame.backward())) {
				return true;
			}
		}
		return false;
	}

	/** Index of the first frame at or after client tick {@code tick}, or -1. */
	static int indexOf(List<ClientFrame> f, int tick) {
		for (int i = 0; i < f.size(); i++) {
			if (f.get(i).tick() >= tick) {
				return i;
			}
		}
		return -1;
	}
}
