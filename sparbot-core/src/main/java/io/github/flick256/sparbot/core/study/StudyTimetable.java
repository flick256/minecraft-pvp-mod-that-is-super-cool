package io.github.flick256.sparbot.core.study;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** User-provided Melbourne exam timetable. Dates are scheduling input, not verified official data. */
public final class StudyTimetable {
	private StudyTimetable() {}
	public static final String TIME_ZONE_ID = "Australia/Melbourne";
	public record Exam(String subject, String label, LocalDate date, LocalTime start, LocalTime end) {
		public Exam {
			if (subject == null || subject.isBlank() || label == null || label.isBlank() || date == null || start == null || end == null || !end.isAfter(start))
				throw new IllegalArgumentException("invalid exam entry");
		}
	}

	private static final List<Exam> ALL = List.of(
		new Exam("English", "Exam", LocalDate.of(2026, 10, 27), LocalTime.of(9, 0), LocalTime.of(12, 15)),
		new Exam("Mathematical Methods", "Exam 1", LocalDate.of(2026, 11, 5), LocalTime.of(9, 0), LocalTime.of(10, 15)),
		new Exam("Mathematical Methods", "Exam 2", LocalDate.of(2026, 11, 6), LocalTime.of(11, 45), LocalTime.of(14, 0)),
		new Exam("History: Revolutions", "Exam", LocalDate.of(2026, 11, 9), LocalTime.of(15, 0), LocalTime.of(17, 15)),
		new Exam("Chemistry", "Exam", LocalDate.of(2026, 11, 10), LocalTime.of(9, 0), LocalTime.of(11, 45)),
		new Exam("French", "Exam", LocalDate.of(2026, 11, 17), LocalTime.of(15, 0), LocalTime.of(17, 15))
	);

	public static List<Exam> all() { return ALL; }

	/** First scheduled exam date for each subject, encoded as ISO yyyy-MM-dd. */
	public static Map<String, String> defaults() {
		Map<String, String> dates = new LinkedHashMap<>();
		for (Exam exam : ALL) dates.putIfAbsent(exam.subject(), exam.date().toString());
		return Map.copyOf(dates);
	}

	/**
	 * Uses user overrides unchanged. If the configured date is a timetable default,
	 * roll it forward to the next same-subject exam on or after today. Once the
	 * subject's final exam has passed, retain that final date.
	 */
	public static LocalDate effectiveDate(String subject, String configuredIsoDate, LocalDate today) {
		if (configuredIsoDate == null || configuredIsoDate.isBlank()) return null;
		final LocalDate configured;
		try { configured = LocalDate.parse(configuredIsoDate); }
		catch (DateTimeParseException e) { throw new IllegalArgumentException("exam date must be ISO yyyy-MM-dd", e); }
		if (today == null) throw new IllegalArgumentException("today is required");
		List<Exam> exams = ALL.stream().filter(e -> e.subject().equals(subject))
			.sorted(Comparator.comparing(Exam::date)).toList();
		String defaultDate = defaults().get(subject);
		if (exams.isEmpty() || defaultDate == null || !defaultDate.equals(configured.toString())) return configured;
		return exams.stream().filter(e -> !e.date().isBefore(today)).map(Exam::date).findFirst()
			.orElse(exams.getLast().date());
	}
}
