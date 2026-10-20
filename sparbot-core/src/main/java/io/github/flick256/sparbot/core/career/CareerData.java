package io.github.flick256.sparbot.core.career;

import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned JSON document used by {@link CareerStore}. */
public final class CareerData {
	public int version = CareerStore.VERSION;
	public Map<String, PlayerCareer> players = new LinkedHashMap<>();

	public static final class PlayerCareer {
		public Map<String, CareerProgress> disciplines = new LinkedHashMap<>();
		public Map<String, RivalRecord> rivals = new LinkedHashMap<>();
		public Map<String, Integer> adaptiveStreaks = new LinkedHashMap<>();
		public boolean adaptivePractice;
	}
}
