package io.github.flick256.sparbot.core.study;

import java.util.List;
import java.util.regex.Pattern;

/** Content-independent question definition for offline study. */
public record StudyQuestion(String id, String subject, String topic, String prompt, int marks, Kind kind,
		List<String> options, int answerIndex, Double numericAnswer, double tolerance, String unit,
		List<String> hints, String explanation, List<String> rubric, String source) {
	private static final Pattern NUMBER = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");
	public enum Kind { CHOICE, NUMBER, SELF_REVIEW }

	public StudyQuestion {
		id = required(id, "id", 96); subject = required(subject, "subject", 96); topic = required(topic, "topic", 128);
		prompt = required(prompt, "prompt", 4000);
		if (marks < 1 || marks > 100) throw new IllegalArgumentException("marks must be between 1 and 100");
		if (kind == null) throw new IllegalArgumentException("kind is required");
		options = options == null ? List.of() : List.copyOf(options);
		hints = hints == null ? List.of() : List.copyOf(hints);
		rubric = rubric == null ? List.of() : List.copyOf(rubric);
		explanation = required(explanation, "explanation", 4000);
		unit = unit == null ? "" : unit.trim(); source = source == null ? "" : source.trim();
		if (hints.isEmpty() || hints.size() > 8 || hints.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 1000)) throw new IllegalArgumentException("one to eight non-empty hints are required");
		if (kind == Kind.CHOICE) {
			if (options.size() < 2 || options.size() > 12 || options.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 1000) || answerIndex < 0 || answerIndex >= options.size()) throw new IllegalArgumentException("invalid choice options or answer index");
			if (numericAnswer != null) throw new IllegalArgumentException("choice question cannot have a numeric answer");
		} else if (kind == Kind.NUMBER) {
			if (numericAnswer == null || !Double.isFinite(numericAnswer) || !Double.isFinite(tolerance) || tolerance <= 0 || tolerance > 1_000_000 || !options.isEmpty()) throw new IllegalArgumentException("invalid numeric answer or tolerance");
		} else {
			if (rubric.isEmpty() || rubric.size() > 12 || rubric.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 1000) || !options.isEmpty() || numericAnswer != null) throw new IllegalArgumentException("self-review questions need a rubric");
		}
	}

	private static String required(String value, String label, int max) {
		if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(label + " is required and must be at most " + max + " characters");
		return value.trim();
	}

	public boolean checkChoice(int selectedIndex) { return kind == Kind.CHOICE && selectedIndex == answerIndex; }
	/** Accepts finite signed decimal or scientific notation. Units belong in the UI prompt. */
	public boolean checkNumber(String response) {
		if (kind != Kind.NUMBER || response == null || response.length() > 64) return false;
		String value = response.trim();
		if (!NUMBER.matcher(value).matches()) return false;
		try { double parsed = Double.parseDouble(value); return Double.isFinite(parsed) && Math.abs(parsed - numericAnswer) <= tolerance; }
		catch (NumberFormatException ignored) { return false; }
	}
	public boolean isAutoMarkedCorrect() { return kind != Kind.SELF_REVIEW; }
}
