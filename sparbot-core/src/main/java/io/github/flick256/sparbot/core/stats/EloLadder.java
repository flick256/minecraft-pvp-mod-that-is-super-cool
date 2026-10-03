package io.github.flick256.sparbot.core.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Elo ratings for every competitor that has played a match: each bot configuration
 * ("bot:pro/balanced") and each human ("player:<name>"). Measures how strong a skill level really is.
 */
public final class EloLadder {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** One competitor's record. */
	public static final class Entry {
		public double rating = Elo.DEFAULT_RATING;
		public int wins;
		public int losses;
		public int draws;

		public int games() {
			return wins + losses + draws;
		}
	}

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public Entry get(String key) {
		return entries.computeIfAbsent(key, k -> new Entry());
	}

	/** Records one finished match. {@code scoreA}: 1 = A won, 0 = B won, 0.5 = draw. */
	public void record(String a, String b, double scoreA) {
		Entry ea = get(a);
		Entry eb = get(b);
		double[] updated = Elo.update(ea.rating, eb.rating, scoreA, Elo.DEFAULT_K);
		ea.rating = updated[0];
		eb.rating = updated[1];
		if (scoreA > 0.5) {
			ea.wins++;
			eb.losses++;
		} else if (scoreA < 0.5) {
			ea.losses++;
			eb.wins++;
		} else {
			ea.draws++;
			eb.draws++;
		}
	}

	/** Competitors sorted by rating, best first. */
	public List<Map.Entry<String, Entry>> standings() {
		List<Map.Entry<String, Entry>> list = new ArrayList<>(entries.entrySet());
		list.sort(Comparator.comparingDouble((Map.Entry<String, Entry> e) -> e.getValue().rating).reversed());
		return list;
	}

	public String toJson() {
		return GSON.toJson(entries);
	}

	public static EloLadder fromJson(String json) {
		EloLadder ladder = new EloLadder();
		Map<String, Entry> read = GSON.fromJson(json, new TypeToken<LinkedHashMap<String, Entry>>() { }.getType());
		if (read != null) {
			ladder.entries.putAll(read);
		}
		return ladder;
	}

	public static String botKey(String profileId, String styleId) {
		return "bot:" + profileId + "/" + styleId;
	}

	public static String playerKey(String name) {
		return "player:" + name;
	}
}
