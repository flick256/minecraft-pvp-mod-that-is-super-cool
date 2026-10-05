package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.HashSet;
import java.util.Set;

/**
 * The block line going back: under pressure with an apple to eat, a UHC player doesn't stop to build a
 * wall. They back-pedal facing the opponent and fast-click blocks onto the ground in front of them, one
 * per step, so a line of blocks trails out between them (two high when they're quick enough). The chaser
 * has to jump every block, which kills their sprint and their combo, and the player eats behind it.
 * Every block is a real click on the ground block in front, at the profile's click rate.
 */
public final class BacklineTactic implements Tactic {
	private static final double MIN_RANGE = 1.8;
	private static final double MAX_RANGE = 5.5;
	/** Far enough away again: the line has done its job. */
	private static final double CLEAR = 7.0;
	private static final int TIMEOUT = 50;
	private static final int COOLDOWN = 140;
	/** Starts this far above the health at which the bot eats. */
	private static final double EARLY = 0.15;
	private static final double BODY_HALF_WIDTH = 0.3;

	private final Decision willLine = new Decision();
	private final Set<BlockSpot> placed = new HashSet<>();
	private boolean running;
	private int ticks;
	private int planned;
	private int blocksBefore;
	private BlockSpot aimed;
	private long last = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "backline";
	}

	@Override
	public String detail() {
		return running ? placed.size() + "/" + planned : "";
	}

	@Override
	public double score(BrainContext c) {
		if (running) {
			return Scores.SURVIVAL + 0.06;
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || !self.onGround() || WallTactic.wallSlot(inv) < 0 || c.observation.tick() - last < COOLDOWN) {
			return 0;
		}
		double d = c.targetDistance();
		boolean low = self.healthFraction() < c.profile.items().gappleHealthFraction() + EARLY;
		if (!low || HealTactic.gappleSlot(inv) < 0 || d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		return willLine.get(c.rng, c.profile.items().uhcSkill(), 20) ? Scores.SURVIVAL + 0.06 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		if (!running) {
			running = true;
			ticks = 0;
			placed.clear();
			aimed = null;
			planned = 2 + (int) Math.round(4 * c.profile.items().uhcSkill());
			blocksBefore = WallTactic.blocks(c.self.inventory());
		}
	}

	@Override
	public void onExit(BrainContext c) {
		if (running) {
			finish(c);
		}
	}

	@Override
	public void reset() {
		running = false;
		placed.clear();
		willLine.reset();
		last = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		if (!running) {
			onEnter(c);
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		int now = WallTactic.blocks(inv);
		if (now < blocksBefore && aimed != null) {
			placed.add(aimed);
		}
		blocksBefore = now;
		int slot = WallTactic.wallSlot(inv);
		if (t == null || slot < 0 || ++ticks > TIMEOUT || placed.size() >= planned || c.targetDistance() > CLEAR) {
			finish(c);
			return Inputs.IDLE;
		}
		// The space on the ground one step towards them; once it has a block, the one on top of it (two
		// high, for a quick player), then the next step back brings a new space in front.
		Vec3 here = self.position();
		double dx = t.position().x() - here.x();
		double dz = t.position().z() - here.z();
		double len = Math.max(1e-6, Math.hypot(dx, dz));
		BlockSpot ground = BlockPlay.groundUnder(here);
		BlockSpot front = new BlockSpot((int) Math.floor(here.x() + dx / len * 1.1), ground.y() + 1, (int) Math.floor(here.z() + dz / len * 1.1));
		BlockSpot target = front;
		if (placed.contains(front)) {
			BlockSpot top = new BlockSpot(front.x(), front.y() + 1, front.z());
			target = c.profile.items().uhcSkill() >= 0.6 && !placed.contains(top) ? top : null;
		}
		Inputs back;
		if (target != null && !occupied(c, target)) {
			aimed = target;
			back = BlockPlay.clickTop(c, new BlockSpot(target.x(), target.y() - 1, target.z()), 1.0, slot);
		} else {
			aimed = null;
			back = BlockPlay.position(c);
		}
		// Back-pedalling, facing them: the line trails out in front.
		return Movement.guardEdges(c, back.withMovement(-1, 0).withSprint(false));
	}

	private static boolean occupied(BrainContext c, BlockSpot space) {
		TargetState t = c.seen();
		return overlaps(c.self.position(), BODY_HALF_WIDTH, 1.8, space) || t != null && overlaps(t.position(), t.halfWidth(), t.height(), space);
	}

	private static boolean overlaps(Vec3 feet, double halfWidth, double height, BlockSpot b) {
		return feet.x() + halfWidth > b.x() && feet.x() - halfWidth < b.x() + 1 && feet.z() + halfWidth > b.z() && feet.z() - halfWidth < b.z() + 1
			&& feet.y() + height > b.y() && feet.y() < b.y() + 1;
	}

	private void finish(BrainContext c) {
		running = false;
		last = c.observation.tick();
		willLine.reset();
		if (!placed.isEmpty()) {
			// Eat behind it now (see HealTactic).
			c.memory.walledAt = c.observation.tick();
		}
	}
}
