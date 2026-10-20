package io.github.flick256.sparbot.core.study;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validates a loaded bank and exposes stable subject filtering. */
public final class StudyBank {
	private final Map<String, StudyQuestion> questions;
	public StudyBank(List<StudyQuestion> questions) {
		if (questions == null || questions.isEmpty()) throw new IllegalArgumentException("question bank is empty");
		var indexed = new LinkedHashMap<String, StudyQuestion>();
		for (StudyQuestion question : questions) {
			if (question == null || indexed.putIfAbsent(question.id(), question) != null) throw new IllegalArgumentException("null or duplicate question id");
		}
		this.questions = Map.copyOf(indexed);
	}
	public List<StudyQuestion> all() { return List.copyOf(questions.values()); }
	public StudyQuestion get(String id) { return questions.get(id); }
	public List<StudyQuestion> forSubject(String subject) { return questions.values().stream().filter(q -> q.subject().equals(subject)).toList(); }
	public List<String> subjects() { return questions.values().stream().map(StudyQuestion::subject).distinct().sorted(String.CASE_INSENSITIVE_ORDER).toList(); }
}
