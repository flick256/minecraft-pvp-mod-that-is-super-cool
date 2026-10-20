package io.github.flick256.sparbot.core.study;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class StudyGateTest {
	@Test void entryStudyPaysForOneFightAndEachLaterFightWaits() {
		var gate = new StudyGate();
		gate.joined();
		assertTrue(gate.worldRequired());
		assertNull(gate.completed());
		assertTrue(gate.allow("sparbot practice fight uhc_duel swight"));
		assertFalse(gate.allow("sparbot practice rematch"));
		assertEquals("sparbot practice rematch", gate.completed());
		assertFalse(gate.allow("sparbot rival iron_echo"));
	}

	@Test void queuedCommandCannotBeReplacedOrSurviveDisconnect() {
		var gate = new StudyGate();
		gate.joined();
		assertFalse(gate.allow("sparbot practice rematch"));
		assertFalse(gate.allow("sparbot rival swight"));
		assertEquals("sparbot practice rematch", gate.pendingCommand());
		gate.cancelFight();
		assertNull(gate.pendingCommand());
		assertTrue(gate.worldRequired());
		gate.completed();
		gate.disconnected();
		gate.joined();
		assertFalse(gate.allow("sparbot practice rematch"));
	}

	@Test void onlyActivityCommandsUseStudyCredit() {
		for (String command : List.of("sparbot spawn Sparky pro", "sparbot fight Sparky Player", "sparbot match start sword a x y",
			"sparbot career uhc_duel continue", "sparbot practice colosseum hill", "sparbot drill charge", "/sparbot:sparbot practice rematch"))
			assertTrue(StudyGate.startsActivity(command), command);
		for (String command : List.of("sparbot menu", "sparbot practice terrain flat", "sparbot practice leave", "sparbot match stop court",
			"sparbot drill stop", "sparbot replay demo", "sparbot record stop demo", "sparbot career adapt on", "say sparbot fight", "sparbot drill"))
			assertFalse(StudyGate.startsActivity(command), command);
	}
}
