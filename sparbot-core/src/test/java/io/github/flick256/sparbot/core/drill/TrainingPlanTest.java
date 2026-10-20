package io.github.flick256.sparbot.core.drill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrainingPlanTest {
	@Test
	void recommendsOnlyAQualifiedCoachObservationAndKeepsItsEvidence() {
		DrillTally tally = new DrillTally();
		for (int i = 0; i < 6; i++) tally.hit(i < 5 ? 0.4 : 1.0, false, true, 2.8);
		FightReport report = FightReport.of("sword_duel", "Rival", "lost", 1L, tally);

		var plan = TrainingPlan.recommendations(List.of(report));

		assertEquals(1, plan.size());
		assertEquals("charge", plan.getFirst().drillId());
		assertEquals("Full charge", plan.getFirst().title());
		assertTrue(plan.getFirst().reason().contains("below full charge"));
	}

	@Test
	void sparseOrUncitedReportsDoNotInventTrainingNeeds() {
		FightReport sparse = new FightReport("sword_duel", "Rival", "lost", 1L, Map.of("Hits", "2"), List.of());
		FightReport praise = new FightReport("sword_duel", "Rival", "won", 2L, Map.of(),
			List.of("A clean fight: nothing stood out. Try a harder bot, or go for gold in the drills."));

		assertTrue(TrainingPlan.recommendations(List.of(sparse, praise)).isEmpty());
	}

	@Test
	void newerRepeatedEvidenceRaisesThatDrillInThePlan() {
		var charge = FightReport.of("sword_duel", "A", "lost", 1L, chargeTally());
		DrillTally aimTally = new DrillTally(); aimTally.swings = 15;
		var aim = FightReport.of("sword_duel", "B", "lost", 2L, aimTally);
		var latestCharge = FightReport.of("sword_duel", "C", "lost", 3L, chargeTally());

		assertEquals("charge", TrainingPlan.recommendations(List.of(charge, aim, latestCharge)).getFirst().drillId());
	}

	private static DrillTally chargeTally() {
		DrillTally tally = new DrillTally();
		for (int i = 0; i < 6; i++) tally.hit(i < 5 ? 0.4 : 1.0, false, true, 2.8);
		return tally;
	}
}
