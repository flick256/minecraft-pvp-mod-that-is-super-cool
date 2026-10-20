package io.github.flick256.sparbot.core.study;

/** Persisted recall history for one question. Mutations are deliberately simple for JSON storage. */
public final class StudyProgress {
	private int attempts;
	private int unaidedSuccesses;
	private int assistedSuccesses;
	private int consecutiveUnaided;
	private long dueAtMillis;
	private long lastSeenMillis;

	public int attempts() { return attempts; }
	public int unaidedSuccesses() { return unaidedSuccesses; }
	public int assistedSuccesses() { return assistedSuccesses; }
	public int consecutiveUnaided() { return consecutiveUnaided; }
	public long dueAtMillis() { return dueAtMillis; }
	public long lastSeenMillis() { return lastSeenMillis; }
	public boolean isValid() { return attempts >= 0 && attempts <= 1_000_000 && unaidedSuccesses >= 0 && assistedSuccesses >= 0 && unaidedSuccesses <= attempts && assistedSuccesses <= attempts && unaidedSuccesses + assistedSuccesses <= attempts && consecutiveUnaided >= 0 && consecutiveUnaided <= unaidedSuccesses && dueAtMillis >= 0 && lastSeenMillis >= 0; }

	/** assisted includes a revealed hint; self-review must be recorded as assisted. */
	public void record(boolean correct, boolean assisted, long nowMillis) {
		if (nowMillis < 0) throw new IllegalArgumentException("time must be non-negative");
		attempts++;
		lastSeenMillis = nowMillis;
		if (!correct) {
			consecutiveUnaided = 0;
			dueAtMillis = add(nowMillis, 10 * 60_000L);
		} else if (assisted) {
			assistedSuccesses++;
			consecutiveUnaided = 0;
			dueAtMillis = add(nowMillis, 10 * 60_000L);
		} else {
			unaidedSuccesses++;
			consecutiveUnaided = Math.min(4, consecutiveUnaided + 1);
			long[] days = {1, 2, 4, 7};
			dueAtMillis = add(nowMillis, days[Math.min(consecutiveUnaided - 1, days.length - 1)] * 86_400_000L);
		}
	}
	private static long add(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
}
