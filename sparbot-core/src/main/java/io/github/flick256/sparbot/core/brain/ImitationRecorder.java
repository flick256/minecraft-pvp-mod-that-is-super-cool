package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import java.util.function.BiConsumer;

/**
 * The scripted melee, with every decision written down as (what it saw, which keys it pressed): the
 * examples a learned policy first imitates before it improves on them by playing itself.
 */
public final class ImitationRecorder implements Tactic {
	private final EngageTactic inner = new EngageTactic();
	private final BiConsumer<double[], double[]> sink;

	/** @param sink receives (features, targets) for every tick the scripted melee acts */
	public ImitationRecorder(BiConsumer<double[], double[]> sink) {
		this.sink = sink;
	}

	@Override
	public String name() {
		return inner.name();
	}

	@Override
	public double score(BrainContext c) {
		return inner.score(c);
	}

	@Override
	public Inputs act(BrainContext c) {
		if (c.target.hurtTime() > c.memory.lastTargetHurtTime && c.memory.ticksSinceOwnClick < 6) {
			c.memory.sinceOwnHit = 0;
		}
		SwordPlan.observe(c);
		double[] features = MeleeFeatures.encode(c);
		Inputs in = inner.act(c);
		sink.accept(features, MeleeFeatures.target(in));
		return in;
	}

	@Override
	public void reset() {
		inner.reset();
	}
}
