package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyQuestionTest {
	@Test void checksChoiceAndStrictNumericAnswers() {
		var choice = new StudyQuestion("q1", "Maths", "Algebra", "Choose", 1, StudyQuestion.Kind.CHOICE,
			List.of("a", "b"), 1, null, 0, "", List.of("Try again"), "Because", List.of(), "");
		assertTrue(choice.checkChoice(1)); assertFalse(choice.checkChoice(0)); assertFalse(choice.checkNumber("1"));
		var number = new StudyQuestion("q2", "Maths", "Algebra", "Calculate", 2, StudyQuestion.Kind.NUMBER,
			List.of(), -1, 125.0, .01, "m", List.of("Break it down"), "Because", List.of(), "");
		assertTrue(number.checkNumber("+1.25e2")); assertFalse(number.checkNumber("125 m")); assertFalse(number.checkNumber("NaN"));
	}

	@Test void selfReviewCannotBeAutoMarkedAndRequiresRubric() {
		var review = new StudyQuestion("q3", "English", "Analysis", "Explain", 5, StudyQuestion.Kind.SELF_REVIEW,
			List.of(), -1, null, 0, "", List.of("Recall evidence"), "Compare with model", List.of("Evidence", "Reasoning"), "");
		assertFalse(review.isAutoMarkedCorrect()); assertFalse(review.checkChoice(0)); assertFalse(review.checkNumber("1"));
		assertThrows(IllegalArgumentException.class, () -> new StudyQuestion("q4", "English", "Analysis", "Explain", 5, StudyQuestion.Kind.SELF_REVIEW,
			List.of(), -1, null, 0, "", List.of("Hint"), "Model", List.of(), ""));
	}
}
