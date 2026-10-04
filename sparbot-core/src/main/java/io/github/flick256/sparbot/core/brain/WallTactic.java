package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Cutting the opponent off to heal: getting low with a golden apple to eat and the opponent coming in,
 * the bot stops and puts a wall of building blocks between them, then eats behind it (the heal tactic
 * takes over the moment the wall is up). The wall goes one block towards the opponent: the middle
 * column first, two high, then the columns either side, as many blocks as the player is good (a
 * beginner manages two, a pro the full three by two).
 */
public final class WallTactic implements Tactic {
	/** Blocks that are tools of other tactics, not wall material. */
	private static final Set<String> NOT_WALL = Set.of("minecraft:obsidian", "minecraft:rail", "minecraft:glowstone", "minecraft:respawn_anchor",
		"minecraft:tnt", "minecraft:cobweb");
	private static final double MIN_RANGE = 1.5;
	private static final double MAX_RANGE = 7.0;
	/** Starts this far above the health at which the bot eats, so the wall is up in time. */
	private static final double EARLY = 0.15;
	private static final int TIMEOUT = 50;
	/** One block that won't go down in this many ticks is skipped. */
	private static final int BLOCK_TIMEOUT = 8;
	private static final int COOLDOWN = 160;
	private static final double STOPPED = 0.03;
	private static final int STOP_TICKS = 4;
	private static final double BODY_HALF_WIDTH = 0.3;
	private static final int MAX_BLOCKS = 6;

	private final Decision willWall = new Decision();
	/** The wall's blocks in building order (the space each goes into). */
	private final List<BlockSpot> plan = new ArrayList<>();
	private int next;
	private int blockTicks;
	private int blocksBefore;
	private int ticks;
	private long lastWall = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "wall";
	}

	@Override
	public String detail() {
		return plan.isEmpty() ? "stop" : next + "/" + plan.size();
	}

	@Override
	public double score(BrainContext c) {
		if (!plan.isEmpty() && next < plan.size()) {
			// Building: the first column hides the opponent, but the wall isn't done.
			return Scores.SURVIVAL + 0.07;
		}
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || !self.onGround() || wallSlot(inv) < 0 || c.observation.tick() - lastWall < COOLDOWN) {
			return 0;
		}
		boolean canHeal = HealTactic.gappleSlot(inv) >= 0;
		boolean low = self.healthFraction() < c.profile.items().gappleHealthFraction() + EARLY;
		double d = c.targetDistance();
		if (!canHeal || !low || d < MIN_RANGE || d > MAX_RANGE) {
			return 0;
		}
		return willWall.get(c.rng, c.profile.items().uhcSkill(), 20) ? Scores.SURVIVAL + 0.07 : 0;
	}

	@Override
	public void onEnter(BrainContext c) {
		plan.clear();
		next = 0;
		blockTicks = 0;
		ticks = 0;
		blocksBefore = blocks(c.self.inventory());
	}

	@Override
	public void reset() {
		willWall.reset();
		plan.clear();
		lastWall = Long.MIN_VALUE / 2;
	}

	/** Once stopped: the wall goes one block towards the opponent, square to the nearest compass axis. */
	private void plan(BrainContext c) {
		SelfState self = c.self;
		float yaw = Angles.yawTowards(self.position(), c.seen().position());
		int quadrant = Math.floorMod(Math.round(yaw / 90.0F), 4);
		int dx = quadrant == 1 ? -1 : quadrant == 3 ? 1 : 0;
		int dz = quadrant == 0 ? 1 : quadrant == 2 ? -1 : 0;
		BlockSpot ground = BlockPlay.groundUnder(self.position());
		int x = ground.x() + dx;
		int y = ground.y() + 1;
		int z = ground.z() + dz;
		// A block can't go where the bot's own body (0.6 wide) reaches: then the wall goes one further out.
		double reach = dx != 0 ? (self.position().x() - (ground.x() + 0.5)) * dx : (self.position().z() - (ground.z() + 0.5)) * dz;
		if (reach + BODY_HALF_WIDTH > 0.5) {
			x += dx;
			z += dz;
		}
		// Sideways: square to the way the wall faces. Which side first is a coin toss.
		int sx = dz;
		int sz = -dx;
		if (c.rng.chance(0.5)) {
			sx = -sx;
			sz = -sz;
		}
		int size = Math.min(MAX_BLOCKS, Math.min(blocksBefore, 2 + (int) Math.round(4 * c.profile.items().uhcSkill())));
		BlockSpot centre = new BlockSpot(x, y, z);
		BlockSpot sideA = new BlockSpot(x + sx, y, z + sz);
		BlockSpot sideB = new BlockSpot(x - sx, y, z - sz);
		// The middle column always; the sides as far as the blocks go. Bottoms before tops and sides before
		// the middle, since a block already standing in the middle hides the ground beside it from the bot.
		boolean a = size >= 3;
		boolean b = size >= 4;
		boolean aTop = size >= 5;
		boolean bTop = size >= 6;
		if (a) {
			plan.add(sideA);
		}
		if (b) {
			plan.add(sideB);
		}
		plan.add(centre);
		if (aTop) {
			plan.add(above(sideA));
		}
		if (bTop) {
			plan.add(above(sideB));
		}
		plan.add(above(centre));
	}

	private static BlockSpot above(BlockSpot b) {
		return new BlockSpot(b.x(), b.y() + 1, b.z());
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		int now = blocks(inv);
		if (now < blocksBefore) {
			next++;
			blockTicks = 0;
			blocksBefore = now;
		}
		ticks++;
		if (!plan.isEmpty() && next >= plan.size() || ticks > TIMEOUT || wallSlot(inv) < 0 || c.seen() == null) {
			return finish(c);
		}
		if (plan.isEmpty()) {
			// Stop first: a sprinting player slides a block before they can build.
			double speed = Math.hypot(c.self.velocity().x(), c.self.velocity().z());
			if (speed > STOPPED && ticks < STOP_TICKS) {
				return BlockPlay.position(c).withMovement(0, 0).withSprint(false);
			}
			plan(c);
		}
		// A block that won't go down (someone standing there, out of reach) is skipped.
		while (next < plan.size() && (++blockTicks > BLOCK_TIMEOUT || occupied(c, plan.get(next)))) {
			next++;
			blockTicks = 0;
		}
		if (next >= plan.size()) {
			return finish(c);
		}
		BlockSpot space = plan.get(next);
		BlockSpot clicked = new BlockSpot(space.x(), space.y() - 1, space.z());
		return BlockPlay.clickTop(c, clicked, 1.0, wallSlot(inv)).withMovement(0, 0).withSprint(false);
	}

	/** Whether the bot or the opponent stands in {@code space}: a block can't be placed into a player. */
	private static boolean occupied(BrainContext c, BlockSpot space) {
		return overlaps(c.self.position(), BODY_HALF_WIDTH, 1.8, space) || c.seen() != null
			&& overlaps(c.seen().position(), c.seen().halfWidth(), c.seen().height(), space);
	}

	private static boolean overlaps(Vec3 feet, double halfWidth, double height, BlockSpot b) {
		return feet.x() + halfWidth > b.x() && feet.x() - halfWidth < b.x() + 1 && feet.z() + halfWidth > b.z() && feet.z() - halfWidth < b.z() + 1
			&& feet.y() + height > b.y() && feet.y() < b.y() + 1;
	}

	private Inputs finish(BrainContext c) {
		lastWall = c.observation.tick();
		if (next > 0) {
			// Eat behind it now (see HealTactic).
			c.memory.walledAt = c.observation.tick();
		}
		willWall.reset();
		plan.clear();
		return Inputs.IDLE;
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
