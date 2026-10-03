package io.github.flick256.sparbot.core.stats;

/** Standard Elo maths, used to benchmark how strong each skill profile really is. */
public final class Elo {
	public static final double DEFAULT_RATING = 1000.0;
	public static final double DEFAULT_K = 24.0;

	private Elo() {
	}

	/** Expected score (0-1) of a player rated {@code ratingA} against one rated {@code ratingB}. */
	public static double expected(double ratingA, double ratingB) {
		return 1.0 / (1.0 + Math.pow(10.0, (ratingB - ratingA) / 400.0));
	}

	/**
	 * New ratings after one game.
	 *
	 * @param scoreA 1 for a win by A, 0.5 for a draw, 0 for a loss
	 * @return {newRatingA, newRatingB}
	 */
	public static double[] update(double ratingA, double ratingB, double scoreA, double k) {
		double expectedA = expected(ratingA, ratingB);
		double delta = k * (scoreA - expectedA);
		return new double[] {ratingA + delta, ratingB - delta};
	}
}
