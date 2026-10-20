package io.github.flick256.sparbot.core.drill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Turns the coach's evidence-backed observations into one clear next drill. */
public final class TrainingPlan {
	private TrainingPlan() { }

	/** A recommendation tied to a coach observation from a completed fight. */
	public record Recommendation(String drillId, String title, String reason, int priority) { }

	private record DrillInfo(String id, String title) { }
	private static final List<DrillInfo> DRILLS = Drills.ALL.stream().map(d -> new DrillInfo(d.id(), d.name())).toList();

	/**
	 * Uses only coach lines that cite a named drill. Those lines are emitted only after the coach's
	 * sample minimum for that skill has been met, so sparse fights cannot create invented advice.
	 * Newer reports receive more weight; ties keep the order in the latest report.
	 */
	public static List<Recommendation> recommendations(List<FightReport> reports) {
		if (reports == null || reports.isEmpty()) return List.of();
		Map<String, Score> scores = new LinkedHashMap<>();
		int count = reports.size();
		for (int i = 0; i < count; i++) {
			FightReport report = reports.get(i);
			if (report == null || report.coach() == null) continue;
			int recencyWeight = i + 1;
			for (String line : report.coach()) {
				if (line == null) continue;
				String cited = citedDrill(line);
				if (cited == null) continue;
				String id = DRILLS.stream().filter(d -> d.title().equalsIgnoreCase(cited)).map(DrillInfo::id).findFirst().orElse(null);
				if (id == null) continue;
				Score score = scores.computeIfAbsent(id, ignored -> new Score(DRILLS.stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow().title()));
				score.weight += recencyWeight;
				score.reason = line;
				score.latest = i;
			}
		}
		List<Map.Entry<String, Score>> ranked = new ArrayList<>(scores.entrySet());
		ranked.sort(Comparator.<Map.Entry<String, Score>>comparingInt(e -> e.getValue().weight).reversed()
			.thenComparing(Comparator.comparingInt((Map.Entry<String, Score> e) -> e.getValue().latest).reversed()));
		if (ranked.isEmpty()) return List.of();
		var entry = ranked.getFirst();
		return List.of(new Recommendation(entry.getKey(), entry.getValue().title, entry.getValue().reason, entry.getValue().weight));
	}

	private static String citedDrill(String line) {
		int start = line.lastIndexOf("(drill: ");
		if (start < 0) return null;
		int end = line.indexOf(')', start + 8);
		if (end < 0 || !line.substring(end + 1).isBlank() && !line.substring(end + 1).matches("[.!?]*")) return null;
		return line.substring(start + 8, end).strip();
	}

	private static final class Score {
		final String title;
		int weight;
		int latest;
		String reason;
		Score(String title) { this.title = Objects.requireNonNull(title); }
	}
}
