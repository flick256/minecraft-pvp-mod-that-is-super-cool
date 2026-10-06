package io.github.flick256.sparbot.core.drill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a fight's tally (the same one the drills score) and says what to work on: the few skills furthest
 * below what a good player does, each with the number that shows it and the drill that trains it.
 */
public final class Coach {
	private Coach() {
	}

	/** How far below the mark a skill was (0-1, bigger is worse) and the line saying so. */
	private record Note(double shortfall, String line) {
	}

	/** At most this many lines after a fight. */
	public static final int MAX_LINES = 3;

	/** The fight's numbers worth showing, in order (name to a formatted value); skills with too few tries are left out. */
	public static Map<String, String> numbers(DrillTally t) {
		Map<String, String> n = new LinkedHashMap<>();
		if (t.hits > 0) {
			n.put("Hits", Integer.toString(t.hits));
			n.put("Full charge", pct(t.share(t.fullChargeHits, t.hits)));
			n.put("Crits", pct(t.share(t.crits, t.hits)));
			n.put("Sprint hits", pct(t.share(t.sprintHits, t.hits)));
			n.put("Reach", String.format(Locale.ROOT, "%.2f", t.reachSum / t.hits));
			n.put("Longest combo", Integer.toString(t.bestCombo));
		}
		if (t.hits + t.hitsTaken > 0) {
			n.put("Trades won", pct(t.share(t.hits, t.hits + t.hitsTaken)));
		}
		if (t.swings >= 8) {
			n.put("Accuracy", pct(t.share(t.hits, Math.max(t.hits, t.swings))));
		}
		if (t.hitsTakenOnGround >= 4) {
			n.put("Jump resets", pct(t.share(t.jumpResets, t.hitsTakenOnGround)));
		}
		if (t.blocked > 0) {
			n.put("Blocked", Integer.toString(t.blocked));
		}
		if (t.webEscapes > 0) {
			n.put("Out of webs", String.format(Locale.ROOT, "%.1f s", t.webTicks / 20.0 / t.webEscapes));
		}
		if (t.pots > 0) {
			n.put("Pot accuracy", pct(t.potIntensity / t.pots));
		}
		if (t.arrowsShot > 0) {
			n.put("Arrows hit", pct(t.share(t.arrowHits, t.arrowsShot)));
		}
		if (t.retotems > 0) {
			n.put("Re-totem", String.format(Locale.ROOT, "%.1f s", t.retotemTicks / 20.0 / t.retotems));
		}
		return n;
	}

	/** What to work on, worst first (at most {@link #MAX_LINES}); a line of praise when nothing stands out. */
	public static List<String> lines(DrillTally t) {
		List<Note> notes = new ArrayList<>();
		if (t.hits >= 6) {
			below(notes, t.share(t.fullChargeHits, t.hits), 0.85, v -> pct(1 - v) + " of your hits were below full charge: wait for the cooldown to fill"
				+ " (drill: Full charge)");
			below(notes, t.reachSum / t.hits / 3.0, 2.55 / 3.0, v -> String.format(Locale.ROOT, "your hits averaged %.2f blocks: hit from the edge of reach",
				v * 3) + " so they can't hit back (drill: Edge of reach)");
			below(notes, t.share(t.sprintHits, t.hits), 0.4, v -> "only " + pct(v) + " of your hits were sprint hits: W-tap between them (drill: W-tap)");
		}
		if (t.hits >= 10) {
			below(notes, t.share(t.crits, t.hits), 0.2, v -> "only " + pct(v) + " of your hits were crits (drill: Critical hits)");
		}
		if (t.hits + t.hitsTaken >= 10) {
			below(notes, t.share(t.hits, t.hits + t.hitsTaken), 0.45, v -> "you won " + pct(v) + " of the trades: space with S-taps so you hit first"
				+ " (drill: S-tap and spacing)");
		}
		if (t.swings >= 15) {
			below(notes, t.share(t.hits, Math.max(t.hits, t.swings)), 0.6, v -> pct(v) + " of your swings hit: track them and swing only on target"
				+ " (drill: Aim and tracking)");
		}
		if (t.hitsTakenOnGround >= 6) {
			below(notes, t.share(t.jumpResets, t.hitsTakenOnGround), 0.3, v -> "you jump-reset " + pct(v) + " of the hits you took on the ground"
				+ " (drill: Jump reset)");
		}
		if (t.webEscapes > 0) {
			double s = t.webTicks / 20.0 / t.webEscapes;
			if (s > 1.0) {
				notes.add(new Note(Math.min(1, (s - 1.0) / 2), String.format(Locale.ROOT,
					"%.1f s on average to get out of a web; a pro takes under a second (drill: Out of webs)", s)));
			}
		}
		if (t.pots >= 3) {
			below(notes, t.potIntensity / t.pots, 0.8, v -> "your pots healed " + pct(v) + " of what they could: throw them at your feet (drill: Health pots)");
		}
		if (t.arrowsShot >= 5) {
			below(notes, t.share(t.arrowHits, t.arrowsShot), 0.3, v -> pct(v) + " of your arrows hit: draw fully and lead them (drill: Bow shots)");
		}
		if (t.retotems > 0) {
			double s = t.retotemTicks / 20.0 / t.retotems;
			if (s > 1.0) {
				notes.add(new Note(Math.min(1, (s - 1.0) / 2), String.format(Locale.ROOT, "%.1f s to a new totem after a pop (drill: Re-totem)", s)));
			}
		}
		if (t.shieldsLost >= 2) {
			notes.add(new Note(Math.min(1, t.shieldsLost / 6.0), "your shield was disabled " + t.shieldsLost + " times: drop it when they swap to an axe"
				+ " (drill: Shield defence)"));
		}
		notes.sort(Comparator.comparingDouble(Note::shortfall).reversed());
		List<String> out = new ArrayList<>();
		for (Note n : notes.subList(0, Math.min(MAX_LINES, notes.size()))) {
			out.add(Character.toUpperCase(n.line.charAt(0)) + n.line.substring(1) + ".");
		}
		if (out.isEmpty() && t.hits >= 6) {
			out.add("A clean fight: nothing stood out. Try a harder bot, or go for gold in the drills.");
		}
		return out;
	}

	private interface Line {
		String say(double value);
	}

	private static void below(List<Note> notes, double value, double mark, Line line) {
		if (value < mark) {
			notes.add(new Note((mark - value) / mark, line.say(value)));
		}
	}

	private static String pct(double v) {
		return Math.round(v * 100) + "%";
	}
}
