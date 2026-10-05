package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Explosions;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.Surroundings;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reading the opponent's crystals and anchors. An end crystal or a respawn anchor the opponent has put
 * down by the bot, one the bot would rather not set off itself (it would hurt the bot more than them),
 * is about to be: a crystal player steps out of it, facing the opponent, until what the blast would
 * take through their armor is small. Crystals the bot would gladly set off are left to the crystal
 * tactic, which hits them first. Skill decides how quickly and how reliably it reacts.
 */
public final class DodgeTactic implements Tactic {
	/** A blast that would take at least this much (through armor) is worth stepping out of. */
	private static final double DANGER = 4.0;
	/** Anchors nearer the bot than this (horizontally) are watched; farther ones are theirs to worry about. */
	private static final double ANCHOR_NEAR = 3.5;

	private final Decision willDodge = new Decision();
	private String detail = "";

	@Override
	public String name() {
		return "dodge";
	}

	@Override
	public String detail() {
		return detail;
	}

	@Override
	public double score(BrainContext c) {
		if (threat(c).isEmpty()) {
			return 0;
		}
		return willDodge.get(c.rng, c.profile.items().crystalSkill(), 8) ? Scores.SURVIVAL + 0.02 : 0;
	}

	@Override
	public void reset() {
		willDodge.reset();
		detail = "";
	}

	private record Threat(Vec3 center, double damage, String kind) {
	}

	/** The blast that would hurt the bot most, among those it wouldn't set off itself. */
	private static Optional<Threat> threat(BrainContext c) {
		TargetState t = c.seen();
		if (t == null) {
			return Optional.empty();
		}
		double skill = c.profile.items().crystalSkill();
		List<Threat> threats = new ArrayList<>();
		for (Vec3 crystal : c.world.crystals()) {
			// Theirs to set off: one the bot could hit and would gladly is the crystal tactic's.
			boolean ours = CrystalTactic.crystalDistance(c.self.eyePosition(), crystal) <= c.self.attackReach()
				&& BlockPlay.worth(c, crystal, Explosions.CRYSTAL_POWER, skill);
			if (!ours) {
				threats.add(new Threat(crystal, BlockPlay.selfDamage(c, crystal, Explosions.CRYSTAL_POWER), "crystal"));
			}
		}
		for (Surroundings.Anchor anchor : c.world.anchors()) {
			Vec3 mid = new Vec3(anchor.spot().x() + 0.5, anchor.spot().y() + 0.5, anchor.spot().z() + 0.5);
			double toSelf = mid.horizontalDistanceTo(c.self.position());
			// Theirs: by the bot rather than by the opponent (the anchor tactic sets off the ones by them).
			if (toSelf <= ANCHOR_NEAR && toSelf < mid.horizontalDistanceTo(t.position())) {
				threats.add(new Threat(mid, BlockPlay.selfDamage(c, mid, Explosions.ANCHOR_POWER), anchor.charge() > 0 ? "charged anchor" : "anchor"));
			}
		}
		return threats.stream().filter(th -> th.damage() >= DANGER).max(Comparator.comparingDouble(Threat::damage));
	}

	@Override
	public Inputs act(BrainContext c) {
		Optional<Threat> found = threat(c);
		TargetState t = c.seen();
		if (found.isEmpty() || t == null) {
			return Inputs.IDLE;
		}
		Threat th = found.get();
		detail = th.kind() + String.format(java.util.Locale.ROOT, " %.1f", th.damage());
		return Movement.awayFrom(c, th.center(), t.chest());
	}
}
