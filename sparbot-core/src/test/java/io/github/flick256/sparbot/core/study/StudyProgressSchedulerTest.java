package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StudyProgressSchedulerTest {
	private StudyQuestion q(String id, String subject) { return new StudyQuestion(id, subject, "Topic", "Prompt", 1, StudyQuestion.Kind.SELF_REVIEW, List.of(), -1, null, 0, "", List.of("Hint"), "Model", List.of("Point"), ""); }
	@Test void spacedRepetitionDistinguishesUnaidedAndAssistedWork() {
		var p = new StudyProgress(); p.record(true, false, 1_000);
		assertEquals(1_000 + 86_400_000L, p.dueAtMillis());
		p.record(true, true, 2_000); assertEquals(2_000 + 600_000L, p.dueAtMillis());
		p.record(false, false, 3_000); assertEquals(3_000 + 600_000L, p.dueAtMillis());
		assertEquals(3, p.attempts()); assertEquals(1, p.unaidedSuccesses()); assertEquals(1, p.assistedSuccesses());
	}

	@Test void dueWeakWorkBeatsUnseenQuestionWithMoreUrgentExam() {
		var english = q("english", "English"); var methods = q("methods", "Methods");
		var history = new java.util.HashMap<String, StudyProgress>();
		var weak = new StudyProgress(); weak.record(false, false, 10_000); history.put("english", weak);
		long now = 10_000 + 600_000L;
		assertEquals("english", StudyScheduler.select(List.of(methods, english), history, now, null,
			Map.of("Methods", now + 86_400_000L)).id());
	}

	@Test void unseenWorkComesBeforeFutureReviewAndExamUrgencyOrdersUnseenWork() {
		var future = q("future", "History: Revolutions"); var methods = q("methods", "Methods");
		var english = q("english", "English");
		var history = new java.util.HashMap<String, StudyProgress>();
		var known = new StudyProgress(); known.record(true, false, 10_000); history.put("future", known);
		long now = 10_000;
		assertEquals("english", StudyScheduler.select(List.of(future, methods, english), history, now, null,
			Map.of("English", now + 86_400_000L)).id());
	}

	@Test void avoidsImmediateRepeatAmongEquallyUrgentQuestionsOnly() {
		var a = q("a", "Maths"); var b = q("b", "Maths");
		assertEquals("b", StudyScheduler.select(List.of(a, b), Map.of(), 1_000, "a", Map.of()).id());
		var future = new StudyProgress(); future.record(true, false, 1_000);
		assertEquals("b", StudyScheduler.select(List.of(a, b), Map.of("a", future), 1_000, "a", Map.of()).id());
		assertNull(StudyScheduler.select(List.of(), Map.of(), 1_000, null, Map.of()));
	}
}
