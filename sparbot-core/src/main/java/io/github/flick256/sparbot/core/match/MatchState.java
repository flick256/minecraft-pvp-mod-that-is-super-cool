package io.github.flick256.sparbot.core.match;

/**
 * The rules engine of one match between side A and side B, independent of Minecraft: the mod feeds
 * it ticks and deaths and acts on the phase changes it reports.
 */
public final class MatchState {
	public enum Phase {
		/** Fighters are held at their spawns. */
		COUNTDOWN,
		FIGHTING,
		/** A round just ended; the arena is being reset and the dead respawned. */
		ROUND_OVER,
		FINISHED
	}

	public enum Side {
		A,
		B;

		public Side other() {
			return this == A ? B : A;
		}
	}

	/** What happened this tick, for the mod to act on (announce, reset arena, end match). */
	public enum Event {
		NONE,
		ROUND_STARTED,
		ROUND_WON_A,
		ROUND_WON_B,
		ROUND_DRAWN,
		MATCH_WON_A,
		MATCH_WON_B
	}

	private final GameMode mode;
	private Phase phase = Phase.COUNTDOWN;
	private int ticksInPhase;
	private int round = 1;
	private int winsA;
	private int winsB;
	private int draws;
	private Side winner;

	public MatchState(GameMode mode) {
		this.mode = mode;
	}

	/**
	 * Advances one tick. {@code healthA}/{@code healthB} are health fractions used by the "health"
	 * timeout rule; {@code readyForNextRound} is true once the arena is reset and both fighters are alive.
	 */
	public Event tick(double healthA, double healthB, boolean readyForNextRound) {
		ticksInPhase++;
		switch (phase) {
			case COUNTDOWN -> {
				if (ticksInPhase >= mode.countdownSeconds() * 20) {
					enter(Phase.FIGHTING);
					return Event.ROUND_STARTED;
				}
			}
			case FIGHTING -> {
				if (ticksInPhase >= mode.roundSeconds() * 20) {
					if ("health".equals(mode.timeoutRule()) && Math.abs(healthA - healthB) > 1e-6) {
						return endRound(healthA > healthB ? Side.A : Side.B);
					}
					draws++;
					enter(Phase.ROUND_OVER);
					return Event.ROUND_DRAWN;
				}
			}
			case ROUND_OVER -> {
				if (readyForNextRound) {
					round++;
					enter(Phase.COUNTDOWN);
				}
			}
			case FINISHED -> {
			}
		}
		return Event.NONE;
	}

	/** A fighter on {@code side} died (or left the arena). Only counts while fighting. */
	public Event onDeath(Side side) {
		if (phase != Phase.FIGHTING) {
			return Event.NONE;
		}
		return endRound(side.other());
	}

	/** Both fighters died in the same tick: the round is a draw. */
	public Event onDoubleDeath() {
		if (phase != Phase.FIGHTING) {
			return Event.NONE;
		}
		draws++;
		enter(Phase.ROUND_OVER);
		return Event.ROUND_DRAWN;
	}

	private Event endRound(Side roundWinner) {
		if (roundWinner == Side.A) {
			winsA++;
		} else {
			winsB++;
		}
		if (winsA >= mode.roundsToWin() || winsB >= mode.roundsToWin()) {
			winner = roundWinner;
			enter(Phase.FINISHED);
			return roundWinner == Side.A ? Event.MATCH_WON_A : Event.MATCH_WON_B;
		}
		enter(Phase.ROUND_OVER);
		return roundWinner == Side.A ? Event.ROUND_WON_A : Event.ROUND_WON_B;
	}

	/** Ends the match early (a fighter left the server): the other side wins. */
	public Event forfeit(Side loser) {
		if (phase == Phase.FINISHED) {
			return Event.NONE;
		}
		winner = loser.other();
		enter(Phase.FINISHED);
		return winner == Side.A ? Event.MATCH_WON_A : Event.MATCH_WON_B;
	}

	private void enter(Phase next) {
		phase = next;
		ticksInPhase = 0;
	}

	public Phase phase() {
		return phase;
	}

	public int round() {
		return round;
	}

	public int winsA() {
		return winsA;
	}

	public int winsB() {
		return winsB;
	}

	public int draws() {
		return draws;
	}

	public Side winner() {
		return winner;
	}

	public GameMode mode() {
		return mode;
	}

	public String score() {
		return winsA + " - " + winsB + (draws > 0 ? " (" + draws + " drawn)" : "");
	}
}
