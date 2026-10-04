package io.github.flick256.sparbot.core.ml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.flick256.sparbot.core.brain.MeleeFeatures;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sim.Tournament;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SelfPlayTest {
	@Test
	void ranksAreCentred() {
		assertArrayEquals(new double[] {0.5, -0.5, 0.0}, SelfPlay.centredRanks(new double[] {9, 1, 4}), 1e-12);
	}

	@Test
	void imitationLearnsTheTeachersKeys() {
		SkillProfile pro = SkillProfiles.loadPresets().get("pro");
		List<Imitation.Example> examples = Imitation.collect(pro, List.of(pro), 8, 3);
		assertTrue(examples.size() > 500, "examples: " + examples.size());
		Mlp net = Mlp.random(new Random(2), MeleeFeatures.COUNT, 16, MeleeFeatures.OUTPUTS);
		double[] before = Imitation.agreement(net, examples);
		Imitation.train(net, examples, 5, 0.01, 2);
		double[] after = Imitation.agreement(net, examples);
		assertTrue(after[0] > before[0] && after[0] > 0.8, "forward/back agreement " + before[0] + " -> " + after[0]);
	}

	@Test
	void aGenerationOfSelfPlayChangesTheWeights() {
		SkillProfile pro = SkillProfiles.loadPresets().get("pro");
		Mlp net = Mlp.random(new Random(4), MeleeFeatures.COUNT, 8, MeleeFeatures.OUTPUTS);
		SelfPlay selfPlay = new SelfPlay(pro, List.of(Tournament.scripted(pro)), new SelfPlay.Config(2, 0.05, 0.01, 2, 1, 2));
		Mlp after = selfPlay.train(net, 1, 5, line -> { });
		assertEquals(net.size(), after.size());
		assertNotEquals(net.params()[0], after.params()[0]);
	}
}
