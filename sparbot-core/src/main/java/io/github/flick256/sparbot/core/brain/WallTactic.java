package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Set;

/**
 * Cutting the opponent off to heal: with low health, a golden apple to eat and the opponent coming in,
 * put a two-high wall of building blocks between them and itself (one block on the ground towards
 * them, one on top), then let the heal tactic eat behind it. Skill decides how often the bot thinks of
 * it.
 */
public final class WallTactic implements Tactic {
	/** Blocks that are tools of other tactics, not wall material. */
	private static final Set<String> NOT_WALL = Set.of("minecraft:obsidian", "minecraft:rail", "minecraft:glowstone", "minecraft:respawn_anchor",
		"minecraft:tnt", "minecraft:cobweb");
	private static final double MIN_RANGE = 2.0;
	private static final double MAX_RANGE = 6.0;
	private static final int TIMEOUT = 30;
	private static final int COOLDOWN = 200;
	private static final double STOPPED = 0.03;
	private static final int STOP_TICKS = 8;
	private static final double BODY_HALF_WIDTH = 0.3;

	private final Decision willWall = new Decision();
	private int placed;
	private int blocksBefore;
	private int ticks;
	private int dx;
	private int dz;
	private BlockSpot feet;
	private long lastWall = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "wall";
	}

	@Override
	public double score(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || !self.onGround() || wallSlot(inv) < 0 || c.observation.tick() - lastWall < COOLDOWN) {
			return 0;
		}
		boolean canHeal = inv.hotbarSlot(i -> i.kind() == ItemKind.GOLDEN_APPLE || i.kind() == ItemKind.ENCHANTED_GOLDEN_APPLE) >= 0;
		boolean low = self.healthFraction() < c.profile.items().gappleHealthFraction() + 0.1;
		double d = c.targetDistance();
		if (!canHeal || !low || d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		return willWall.get(c.rng, c.profile.items().uhcSkill(), 40) ? Scores.SURVIVAL + 0.07 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		placed = 0;
		ticks = 0;
		blocksBefore = blocks(c.self.inventory());
		feet = null;
	}

	/** Once stopped: the wall goes one block towards the opponent, on the nearest compass axis. */
	private void plan(BrainContext c) {
		float yaw = Angles.yawTowards(c.self.position(), c.seen().position());
		int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
		dx = quadrant == 1 ? -1 : quadrant == 3 ? 1 : 0;
		dz = quadrant == 0 ? 1 : quadrant == 2 ? -1 : 0;
		BlockSpot ground = BlockPlay.groundUnder(c.self.position());
		feet = new BlockSpot(ground.x(), ground.y() + 1, ground.z());
		// A block can't go where the bot's own body (0.6 wide) reaches: then the wall goes one further out.
		double reach = dx != 0 ? (c.self.position().x() - (feet.x() + 0.5)) * dx : (c.self.position().z() - (feet.z() + 0.5)) * dz;
		if (reach + BODY_HALF_WIDTH > 0.5) {
			feet = new BlockSpot(feet.x() + dx, feet.y(), feet.z() + dz);
		}
	}

	@Override
	public void reset() {
		willWall.reset();
		lastWall = Long.MIN_VALUE / 2;
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		int now = blocks(inv);
		if (now < blocksBefore) {
			placed += blocksBefore - now;
			blocksBefore = now;
		}
		if (placed >= 2 || ++ticks > TIMEOUT || wallSlot(inv) < 0) {
			lastWall = c.observation.tick();
			willWall.reset();
			return Inputs.IDLE;
		}
		if (feet == null) {
			// Stop first: a sprinting player slides a block before they can build.
			double speed = Math.hypot(c.self.velocity().x(), c.self.velocity().z());
			if (speed > STOPPED && ticks < STOP_TICKS) {
				return BlockPlay.position(c).withMovement(0, 0).withSprint(false);
			}
			plan(c);
		}
		// First on the ground in front, then on top of that block.
		BlockSpot clicked = new BlockSpot(feet.x() + dx, feet.y() - 1 + placed, feet.z() + dz);
		return BlockPlay.clickTop(c, clicked, 1.0, wallSlot(inv)).withMovement(0, 0).withSprint(false);
	}

	static boolean wallBlock(ItemInfo item) {
		return item.kind() == ItemKind.BLOCK && !NOT_WALL.contains(item.id());
	}

	private static int wallSlot(InventoryState inv) {
		return inv.hotbarSlot(WallTactic::wallBlock);
	}

	private static int blocks(InventoryState inv) {
		int n = 0;
		for (int i = 0; i < InventoryState.SIZE; i++) {
			if (wallBlock(inv.slot(i))) {
				n += inv.slot(i).count();
			}
		}
		return n;
	}
}
