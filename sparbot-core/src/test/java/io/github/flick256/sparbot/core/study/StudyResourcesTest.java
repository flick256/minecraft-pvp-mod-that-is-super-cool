package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StudyResourcesTest {
	@Test void shippedQuestionsParseAndCoverOnlyTheSelectedExamCourses() throws Exception {
		List<StudyQuestion> questions = new ArrayList<>();
		for (String resource : List.of("/study/questions.json", "/study/languages-history.json")) {
			try (var input = getClass().getResourceAsStream(resource)) {
				assertNotNull(input, resource);
				questions.addAll(List.of(new Gson().fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), StudyQuestion[].class)));
			}
		}
		var bank = new StudyBank(questions);
		assertEquals(Set.of("English", "Mathematical Methods", "Chemistry", "History: Revolutions", "French"), Set.copyOf(bank.subjects()));
		assertEquals(72, bank.all().size());
		for (StudyQuestion q : bank.all()) {
			assertTrue(q.id().matches("[a-zA-Z0-9._:-]{1,96}"), q.id());
			assertEquals(2, q.hints().size(), q.id());
			assertNotEquals(q.hints().getFirst(), q.hints().getLast(), q.id());
			assertEquals(q.options().size(), Set.copyOf(q.options()).size(), q.id());
			assertTrue(q.source().contains("not an official VCAA"), q.id());
		}
	}
}
