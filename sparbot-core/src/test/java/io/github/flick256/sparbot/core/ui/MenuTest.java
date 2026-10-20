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
			List.of(new MenuState.BotEntry("Bob", "pro", "kiter", true, 17.5F, List.of("wtap"), "sword")),
			List.of(new MenuState.Setting("maxBots", "int", "8"), new MenuState.Setting("autoTarget", "boolean", "true")),
			List.of(new MenuState.KitEntry("basic_sword", "Basic sword", true)), List.of("sword"));
		assertEquals(state, MenuState.fromJson(state.toJson()));
	}

	@Test
	void legacySnapshotsDefaultExpansionListsAndNewSnapshotsKeepThem() {
		MenuState old = new MenuState(true, List.of("pro"), List.of("basic_sword"), List.of("balanced"), List.of("sword_duel"),
			List.of(), List.of(), List.of(), List.of());
		var json = com.google.gson.JsonParser.parseString(old.toJson()).getAsJsonObject();
		for (String key : List.of("rivals", "career", "recordings", "training")) { json.remove(key); }
		assertEquals(List.of(), MenuState.fromJson(json.toString()).career());
		var state = new MenuState(old.open(), old.profiles(), old.kits(), old.styles(), old.modes(), old.bots(), old.settings(), old.kitInfo(),
			old.models(), old.myLayouts(), old.myMedals(), old.myFights(),
			List.of(new MenuState.RivalEntry("swight", "Swight", "uhc_duel", "swight", "balanced", "UHC specialist", "Utility", "Pressure", 1, 0, true)),
			List.of(new MenuState.CareerEntry("uhc_duel", "Pro", 2, 1, 2, 3, "swight", false)),
			List.of(new MenuState.ReplayEntry("match-test", 100, true, true, 40, .5, List.of(new MenuState.ReplayEventEntry(12, "shield", "Blocked")))),
			List.of(new MenuState.TrainingEntry("full_charge", "Full charge", "Wait for charge", 1)));
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
		assertEquals("sparbot kit give pvphq_uhc", MenuCommands.giveKit("pvphq_uhc"));
		assertEquals("sparbot technique Bob wtap off", MenuCommands.technique("Bob", "wtap", false));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.giveKit("uhc @a"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.spawn("Bob op Steve", "pro", "basic_sword"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.spawn("Bob", "pro; op", "basic_sword"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.setConfig("maxBots", "1\nop Steve"));
		assertThrows(IllegalArgumentException.class, () -> MenuCommands.style("Bob", "kiter op"));
	}
}
