package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Ballistics;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Comparator;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Cart PvP. 26.2 facts: a TNT minecart is placed by right-clicking a rail (MinecartItem#useOn), a rail
 * needs a solid block under it, and a burning arrow hitting the cart makes it explode at once
 * (MinecartTNT#hurtServer) with power 4 + 1.5 x the arrow's speed x a random fraction. A Flame bow
 * shoots burning arrows.
 *
 * <p>Each tick the bot does the most advanced step available: shoot a cart beside the opponent, else
 * put a cart on a rail beside them, else put a rail down beside them, while holding 2.5-4.5 blocks of
 * distance. It shoots after a short draw: the arrow only has to cross a few blocks.
 */
public final class CartTactic implements Tactic {
	static final String FLAME = "minecraft:flame";
	private static final double ENGAGE_RANGE = 8.0;
	/** BowItem#getPowerForTime(10) = 0.42, so the arrow leaves at 1.25 blocks per tick. */
	private static final int DRAW_TICKS = 10;
	private static final double ARROW_SPEED = 1.25;
	/** The blast's power at its strongest for that arrow speed: 4 + 1.5 x 1.25. */
	private static final double PLANNED_POWER = 4.0 + 1.5 * ARROW_SPEED;
	/** A TNT minecart's box is 0.98 wide and 0.7 tall; aim at its middle. */
	private static final double CART_MID = 0.35;
	private static final double SHOOT_RANGE = 10.0;
	private static final Ballistics.Projectile ARROW = new Ballistics.Projectile("arrow (short draw)", ARROW_SPEED, 0.05, 0.99,
		Ballistics.Order.MOVE_DRAG_GRAVITY, Ballistics.LOOK_VECTOR);

	private final Decision willCart = new Decision();
	private String step = "";

	@Override
	public String name() {
		return "cart";
	}

	@Override
	public String detail() {
		return step;
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || c.targetDistance() > ENGAGE_RANGE || flameBow(inv) < 0 || inv.count(ItemKind.ARROW) == 0) {
			return 0;
		}
		if (inv.usingItem() && inv.usingKind() == ItemKind.BOW && "shoot".equals(step)) {
			return Scores.SPECIALIST + 0.1;
		}
		if (inv.hotbarSlot(ItemKind.TNT_MINECART) < 0 && c.world.tntCarts().isEmpty()) {
			return 0;
		}
		return willCart.get(c.rng, c.profile.items().cartSkill(), 40) ? Scores.SPECIALIST : 0;
	}

	@Override
	public void reset() {
		willCart.reset();
		step = "";
	}

	@Override
	public Inputs act(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		if (t == null) {
			return Inputs.IDLE;
		}
		Vec3 eye = self.eyePosition();
		double skill = c.profile.items().cartSkill();

		Optional<Vec3> cart = c.world.tntCarts().stream()
			.filter(p -> horizontal(p, t.position()) <= BlockPlay.NEAR_TARGET + 0.5 && p.distanceTo(eye) <= SHOOT_RANGE
				&& !opponentInTheWay(c, p) && BlockPlay.worth(c, p, PLANNED_POWER, skill))
			.min(Comparator.comparingDouble(p -> horizontal(p, t.position())));
		if (cart.isPresent()) {
			step = "shoot";
			return shoot(c, cart.get().add(new Vec3(0, CART_MID, 0)));
		}

		int cartSlot = inv.hotbarSlot(ItemKind.TNT_MINECART);
		Optional<BlockSpot> rail = cartSlot < 0 ? Optional.empty() : c.world.rails().stream()
			.filter(r -> r.horizontalDistanceTo(t.position()) <= BlockPlay.NEAR_TARGET && r.y() + BlockPlay.RAIL_HEIGHT < eye.y()
				&& BlockPlay.worth(c, center(r, 0), PLANNED_POWER, skill))
			.min(Comparator.comparingDouble(r -> r.horizontalDistanceTo(t.position())));
		if (rail.isPresent()) {
			step = "place cart";
			return BlockPlay.clickTop(c, rail.get(), BlockPlay.RAIL_HEIGHT, cartSlot);
		}

		int railItem = inv.hotbarSlot(i -> i.kind() == ItemKind.BLOCK && "minecraft:rail".equals(i.id()));
		Optional<BlockSpot> spot = railItem < 0 || cartSlot < 0 ? Optional.empty() : c.world.groundSpots().stream()
			.filter(s -> {
				double d = s.horizontalDistanceTo(t.position());
				// Beside the opponent, not under them (they would ride the cart away).
				return d <= BlockPlay.NEAR_TARGET && d >= 0.9 && s.y() + 1 < eye.y() && BlockPlay.worth(c, center(s, 1), PLANNED_POWER, skill);
			})
			.min(Comparator.comparingDouble(s -> s.horizontalDistanceTo(t.position())));
		if (spot.isPresent()) {
			step = "place rail";
			return BlockPlay.clickTop(c, spot.get(), 1.0, railItem);
		}

		step = "position";
		return BlockPlay.position(c);
	}

	/** Draws the Flame bow briefly and lets go once the arrow will hit {@code point}. */
	private Inputs shoot(BrainContext c, Vec3 point) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		Vec3 eye = self.eyePosition();
		double horizontal = horizontal(point, eye);
		OptionalDouble pitch = Ballistics.solvePitch(ARROW, horizontal, point.y() - eye.y());
		float goalYaw = Angles.yawTowards(eye, point);
		float goalPitch = pitch.isPresent() ? (float) pitch.getAsDouble() : Angles.pitchTowards(eye, point);
		float[] look = c.lookAt(goalYaw, goalPitch);
		int bow = flameBow(inv);
		int press = c.memory.hands.request(c, bow);
		boolean armed = inv.selectedSlot() == bow;
		boolean drawn = inv.usingItem() && inv.usingKind() == ItemKind.BOW && inv.useTicks() >= DRAW_TICKS;
		boolean aimed = c.aimError(goalYaw, goalPitch) < 2.0F;
		// Holding right click draws; letting go fires.
		boolean use = armed && !(drawn && aimed);
		return BlockPlay.spacing(c, look, press).withUse(use);
	}

	/** Whether the opponent's hitbox is between the bot and {@code point}: the arrow would hit them instead. */
	private static boolean opponentInTheWay(BrainContext c, Vec3 point) {
		TargetState t = c.seen();
		Vec3 eye = c.self.eyePosition();
		Vec3 to = point.add(new Vec3(0, CART_MID, 0)).subtract(eye);
		double length = to.length();
		Vec3 dir = to.scale(1.0 / length);
		Vec3 min = new Vec3(t.position().x() - t.halfWidth(), t.position().y(), t.position().z() - t.halfWidth());
		Vec3 max = new Vec3(t.position().x() + t.halfWidth(), t.position().y() + t.height(), t.position().z() + t.halfWidth());
		return BrainContext.rayHitsBox(eye, dir, min, max, length);
	}

	static int flameBow(InventoryState inv) {
		return inv.hotbarSlot(i -> i.kind() == ItemKind.BOW && i.enchanted(FLAME));
	}

	/** Where a cart on top of {@code block} (a rail at {@code above} = 0, a ground block at 1) would sit. */
	private static Vec3 center(BlockSpot block, int above) {
		return new Vec3(block.x() + 0.5, block.y() + above + 0.0625, block.z() + 0.5);
	}

	private static double horizontal(Vec3 a, Vec3 b) {
		double dx = a.x() - b.x();
		double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dz * dz);
	}
}
