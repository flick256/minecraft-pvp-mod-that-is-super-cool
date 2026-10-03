package io.github.flick256.sparbot.core.match;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.stats.EloLadder;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatchStateTest {
	private static final GameMode FIRST_TO_2 = new GameMode("t", "T", "", "basic_sword", 2, 10, 1, "draw");

	private static MatchState.Event run(MatchState m, int ticks, boolean ready) {
		MatchState.Event last = MatchState.Event.NONE;
		for (int i = 0; i < ticks; i++) {
			MatchState.Event e = m.tick(1, 1, ready);
			if (e != MatchState.Event.NONE) {
				last = e;
			}
		}
		return last;
	}

	@Test
	void countdownThenFightThenRoundsUntilAWinner() {
		MatchState m = new MatchState(FIRST_TO_2);
		assertEquals(MatchState.Phase.COUNTDOWN, m.phase());
		assertEquals(MatchState.Event.NONE, m.onDeath(MatchState.Side.A), "deaths during the countdown do not count");
		assertEquals(MatchState.Event.ROUND_STARTED, run(m, 20, false));
		assertEquals(MatchState.Event.ROUND_WON_B, m.onDeath(MatchState.Side.A));
		assertEquals(MatchState.Phase.ROUND_OVER, m.phase());
		run(m, 50, false);
		assertEquals(MatchState.Phase.ROUND_OVER, m.phase(), "waits until the arena is reset and both are alive");
		run(m, 1, true);
		assertEquals(2, m.round());
		run(m, 20, true);
		assertEquals(MatchState.Event.MATCH_WON_B, m.onDeath(MatchState.Side.A));
		assertEquals(MatchState.Phase.FINISHED, m.phase());
		assertEquals(MatchState.Side.B, m.winner());
		assertEquals("0 - 2", m.score());
	}

	@Test
	void timeoutsAreDrawsOrDecidedByHealth() {
		MatchState draw = new MatchState(FIRST_TO_2);
		run(draw, 20, false);
		assertEquals(MatchState.Event.ROUND_DRAWN, run(draw, 200, false));
		assertEquals(1, draw.draws());

		MatchState health = new MatchState(new GameMode("h", "H", "", "basic_sword", 1, 10, 0, "health"));
		health.tick(1, 1, false);
		MatchState.Event e = MatchState.Event.NONE;
		for (int i = 0; i < 200 && e == MatchState.Event.NONE; i++) {
			e = health.tick(0.4, 0.9, false);
		}
		assertEquals(MatchState.Event.MATCH_WON_B, e, "the healthier fighter wins on timeout");
	}

	@Test
	void forfeitEndsTheMatch() {
		MatchState m = new MatchState(FIRST_TO_2);
		assertEquals(MatchState.Event.MATCH_WON_A, m.forfeit(MatchState.Side.B));
		assertEquals(MatchState.Event.NONE, m.forfeit(MatchState.Side.A));
	}

	@Test
	void bundledModesAreValid() {
		Map<String, GameMode> modes = GameModes.loadBundled();
		assertEquals(GameModes.BUNDLED_IDS.size(), modes.size());
		modes.values().forEach(mode -> assertTrue(mode.validate().isEmpty(), mode.id() + ": " + mode.validate()));
	}

	@Test
	void ladderRanksAndPersists() {
		EloLadder ladder = new EloLadder();
		for (int i = 0; i < 10; i++) {
			ladder.record("bot:pro/balanced", "bot:beginner/balanced", 1);
		}
		ladder.record("bot:pro/balanced", "player:Steve", 0.5);
		assertEquals("bot:pro/balanced", ladder.standings().get(0).getKey());
		assertTrue(ladder.get("bot:pro/balanced").rating > ladder.get("bot:beginner/balanced").rating + 150);
		assertEquals(1, ladder.get("player:Steve").draws);
		EloLadder copy = EloLadder.fromJson(ladder.toJson());
		assertEquals(ladder.get("bot:pro/balanced").rating, copy.get("bot:pro/balanced").rating, 1e-9);
		assertEquals(10, copy.get("bot:beginner/balanced").losses);
	}
}
