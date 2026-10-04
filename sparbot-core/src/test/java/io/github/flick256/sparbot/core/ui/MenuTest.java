package io.github.flick256.sparbot.core.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class MenuTest {
	@Test
	void stateSurvivesTheTripToTheClient() {
		MenuState state = new MenuState(true, List.of("beginner", "pro"), List.of("basic_sword"), List.of("balanced", "kiter"), List.of("sword_duel"),
			List.of(new MenuState.BotEntry("Bob", "pro", "kiter", true, 17.5F)),
			List.of(new MenuState.Setting("maxBots", "int", "8"), new MenuState.Setting("autoTarget", "boolean", "true")));
		assertEquals(state, MenuState.fromJson(state.toJson()));
	}

	@Test
	void rejectsHalfAState() {
		assertThrows(JsonParseException.class, () -> MenuState.fromJson("{\"open\":true}"));
	}

	@Test
	void buildsCommandsAndRefusesInjection() {
		assertEquals("sparbot spawn Bob pro sparbot_nodebuff", MenuCommands.spawn("Bob", "pro", "sparbot_nodebuff"));
		assertEquals("sparbot style set Bob aggressive_rusher:0.7,kiter:0.3", MenuCommands.style("Bob", "aggressive_rusher:0.7,kiter:0.3"));
		assertEquals("sparbot config set maxBots 12", MenuCommands.setConfig("maxBots", " 12 "));
		assertEquals("sparbot fight Bob Steve", MenuCommands.fight("Bob", "Steve"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.spawn("Bob op Steve", "pro", "basic_sword"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.spawn("Bob", "pro; op", "basic_sword"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.setConfig("maxBots", "1\nop Steve"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.style("Bob", "kiter op"));
	}
}
