package io.github.flick256.sparbot.core.career;

/** Serializable per-player career track. Each discipline has its own promotion series. */
public final class CareerProgress {
	public static final int WINS_TO_PROMOTE = 3;
	private int tier;
	private int wins;
	private int losses;
	private int seriesPlayerWins;
	private int seriesRivalWins;
	private boolean seriesActive;
	private boolean champion;

	public int tier() { return tier; }
	public int wins() { return wins; }
	public int losses() { return losses; }
	public int seriesPlayerWins() { return seriesPlayerWins; }
	public int seriesRivalWins() { return seriesRivalWins; }
	public boolean seriesActive() { return seriesActive; }
	public boolean champion() { return champion; }

	public boolean isValidFor(String discipline) {
		if (!isValid()) return false;
		var rivals = io.github.flick256.sparbot.core.rival.Rivals.inMode(discipline);
		return !rivals.isEmpty() && tier < rivals.size() && (!champion || tier == rivals.size() - 1);
	}

	public void beginSeries() {
		if (champion) throw new IllegalStateException("This discipline is already championed");
		if (seriesActive) throw new IllegalStateException("A promotion series is already active");
		seriesPlayerWins = seriesRivalWins = 0;
		seriesActive = true;
	}

	/** Completes one normal SparBot best-of-five Match used as the promotion match. */
	public SeriesResult completeSeries(boolean playerWon, boolean disciplineHasNextTier) {
		if (!seriesActive) throw new IllegalStateException("No promotion series is active");
		seriesActive = false;
		seriesPlayerWins = seriesRivalWins = 0;
		if (playerWon) {
			wins++;
			if (disciplineHasNextTier) tier++;
			else champion = true;
			return SeriesResult.PROMOTED;
		}
		losses++;
		return SeriesResult.HELD;
	}

	/** Aborting/stopping a series discards its partial score and grants no career result. */
	public void cancelSeries() {
		seriesActive = false;
		seriesPlayerWins = seriesRivalWins = 0;
	}

	public boolean cancelDanglingSeries() {
		if (!seriesActive) return false;
		cancelSeries();
		return true;
	}

	/** Normalizes Gson-loaded or hand-edited values before accepting a save. */
	public boolean isValid() {
		return tier >= 0 && tier <= 100 && wins >= 0 && wins <= 1_000_000 && losses >= 0 && losses <= 1_000_000 && seriesPlayerWins >= 0 && seriesPlayerWins <= 2
			&& seriesRivalWins >= 0 && seriesRivalWins <= 2 && (!seriesActive || seriesPlayerWins + seriesRivalWins <= 4)
			&& !(champion && seriesActive);
	}

	public enum SeriesResult { IN_PROGRESS, PROMOTED, HELD }
}
