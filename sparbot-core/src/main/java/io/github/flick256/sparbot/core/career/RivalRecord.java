package io.github.flick256.sparbot.core.career;

/** Decisive named-rival challenge record; unfinished matches never update it. */
public final class RivalRecord {
	private int wins;
	private int losses;

	public int wins() { return wins; }
	public int losses() { return losses; }
	public boolean isValid() { return wins >= 0 && wins <= 1_000_000 && losses >= 0 && losses <= 1_000_000; }
	public void record(boolean won) { if (won) wins++; else losses++; }
}
