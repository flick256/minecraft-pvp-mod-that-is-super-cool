package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class StudyTimetableTest {
	@Test void methodsDefaultRollsToExamTwoAfterExamOneAndStopsAtLastDate() {
		var configuredDefault = StudyTimetable.defaults().get("Mathematical Methods");
		assertEquals(LocalDate.of(2026, 11, 6), StudyTimetable.effectiveDate("Mathematical Methods", configuredDefault, LocalDate.of(2026, 11, 6)));
		assertEquals(LocalDate.of(2026, 11, 6), StudyTimetable.effectiveDate("Mathematical Methods", configuredDefault, LocalDate.of(2026, 12, 1)));
	}

	@Test void customDatesAreKeptAndScheduleContainsGivenExamTimes() {
		assertEquals(LocalDate.of(2026, 12, 3), StudyTimetable.effectiveDate("Mathematical Methods", "2026-12-03", LocalDate.of(2026, 11, 6)));
		var methodsExamTwo = StudyTimetable.all().stream().filter(e -> e.subject().equals("Mathematical Methods") && e.label().equals("Exam 2")).findFirst().orElseThrow();
		assertEquals(LocalDate.of(2026, 11, 6), methodsExamTwo.date());
		assertEquals(LocalTime.of(11, 45), methodsExamTwo.start());
		assertEquals(LocalTime.of(14, 0), methodsExamTwo.end());
		assertThrows(UnsupportedOperationException.class, () -> StudyTimetable.all().clear());
	}
}
