package io.github.flick256.sparbot.core.study;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Deterministic due/weak-question selection, with optional per-subject exam urgency. */
public final class StudyScheduler {
	private StudyScheduler() {}
	public static StudyQuestion select(List<StudyQuestion> pool, Map<String, StudyProgress> progress,
			long nowMillis, String lastQuestionId, Map<String, Long> examDatesMillis) {
		if (pool == null || pool.isEmpty()) return null;
		Map<String, StudyProgress> history = progress == null ? Map.of() : progress;
		Map<String, Long> exams = examDatesMillis == null ? Map.of() : examDatesMillis;
		List<StudyQuestion> candidates = pool.stream().filter(q -> q != null)
			.sorted(order(history, nowMillis, exams)).toList();
		if (candidates.isEmpty()) return null;
		StudyQuestion best = candidates.getFirst();
		if (lastQuestionId == null || !best.id().equals(lastQuestionId)) return best;
		int bestClass = studyClass(best, history, nowMillis);
		// Avoid a repeat only when another question is equally urgent. Do not skip a due
		// review simply because the alternatives are unseen or scheduled for later.
		return candidates.stream().filter(q -> !q.id().equals(lastQuestionId)
			&& studyClass(q, history, nowMillis) == bestClass).findFirst().orElse(best);
	}
	private static Comparator<StudyQuestion> order(Map<String, StudyProgress> history, long now, Map<String, Long> exams) {
		return Comparator.comparingInt((StudyQuestion q) -> studyClass(q, history, now))
			.thenComparingLong(q -> scheduledTime(q, history, now, exams))
			.thenComparing(Comparator.comparingInt((StudyQuestion q) -> weakness(q, history)).reversed())
			.thenComparing(Comparator.comparingLong((StudyQuestion q) -> examUrgency(q, now, exams)).reversed())
			.thenComparing(StudyQuestion::id);
	}
	private static int studyClass(StudyQuestion q, Map<String, StudyProgress> history, long now) {
		StudyProgress p = history.get(q.id());
		if (p == null) return 1; // unseen
		if (p.dueAtMillis() <= now) return 0; // a review is due
		return 2; // previously studied, but not due yet
	}
	private static int weakness(StudyQuestion q, Map<String, StudyProgress> history) {
		StudyProgress p = history.get(q.id());
		return p == null || p.consecutiveUnaided() > 0
			? 0 : p.attempts() - p.unaidedSuccesses() + 4;
	}
	private static long scheduledTime(StudyQuestion q, Map<String, StudyProgress> history, long now, Map<String, Long> exams) {
		StudyProgress p = history.get(q.id());
		if (p != null && p.dueAtMillis() > now) return p.dueAtMillis();
		return Long.MAX_VALUE - examUrgency(q, now, exams);
	}
	private static long examUrgency(StudyQuestion q, long now, Map<String, Long> exams) {
		Long exam = exams.get(q.subject());
		if (exam == null || exam <= now) return 0;
		long daysUntil = (exam - now) / 86_400_000L;
		return Math.max(0, 21 - daysUntil);
	}
}
