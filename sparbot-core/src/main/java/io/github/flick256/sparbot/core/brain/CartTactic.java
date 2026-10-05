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
 * <p>Cart players inst-cart all the time: whenever the opponent is a few blocks away on the ground (or
 * stuck, or running in), the bot puts the rail where they will be by the time the arrow arrives (their
 * motion times the combo's length, as well as its tracking skill allows), the cart on it, and fires a
 * short-draw Flame shot straight away (a quarter draw is plenty to light a cart a few blocks off, and a
 * full draw is what gets carts dodged). Once a rail or cart is down it finishes the combo instead of
 * wandering off. It holds 2.5-4.5 blocks of distance throughout.
 */
public final class CartTactic implements Tactic {
	static final String FLAME = "minecraft:flame";
	private static final double ENGAGE_RANGE = 8.0;
	/** BowItem#getPowerForTime(6) = 0.23, so the arrow leaves at 0.69 blocks per tick. */
	private static final int DRAW_TICKS = 6;
	private static final double ARROW_SPEED = 0.69;
	/** The blast's power at its strongest for that arrow speed: 4 + 1.5 x 0.69. */
	private static final double PLANNED_POWER = 4.0 + 1.5 * ARROW_SPEED;
	/** Ticks from deciding where the rail goes to the arrow arriving: two hotbar switches, two clicks, the draw. */
	private static final int COMBO_TICKS = DRAW_TICKS + 10;
	/** Never lead the opponent by more than this (blocks). */
	private static final double MAX_LEAD = 2.5;
	/** Below this horizontal speed (blocks per tick) the opponent counts as standing still. */
	private static final double STILL = 0.12;
	/** A rail this recent means the combo is under way. */
	private static final int COMBO_MEMORY = 60;
	/** A TNT minecart's box is 0.98 wide and 0.7 tall; aim at its middle. */
	private static final double CART_MID = 0.35;
	private static final double SHOOT_RANGE = 10.0;
	/** Cart range: far enough that the blast is mostly theirs, close enough for a quick rail and a short shot. */
	private static final double CART_RANGE_MIN = 2.5;
	private static final double CART_RANGE_MAX = 6.0;
	/** Released when this close to the aim (the cart is a big target at a few blocks)... */
	private static final float SHOT_TOLERANCE = 4.0F;
	/** ...or once drawn this long whatever: a full draw is slow and gets the cart dodged. */
	private static final int LATEST_RELEASE = DRAW_TICKS + 5;
	private static final Ballistics.Projectile ARROW = new Ballistics.Projectile("arrow (short draw)", ARROW_SPEED, 0.05, 0.99,
		Ballistics.Order.MOVE_DRAG_GRAVITY, Ballistics.LOOK_VECTOR);

	private final Decision willCart = new Decision();
	private String step = "";
	private long lastRail = Long.MIN_VALUE / 2;
	private int railsBefore = -1;

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
		// A rail or cart already down next to them: finish the combo.
		boolean cartDown = c.world.tntCarts().stream().anyMatch(p -> horizontal(p, t.position()) <= BlockPlay.NEAR_TARGET + 1.0);
		boolean railDown = c.observation.tick() - lastRail < COMBO_MEMORY && inv.hotbarSlot(ItemKind.TNT_MINECART) >= 0;
		if (cartDown || railDown) {
			return Scores.SPECIALIST + 0.08;
		}
		if (inv.hotbarSlot(ItemKind.TNT_MINECART) < 0 || !opening(c, t)) {
			return 0;
		}
		return willCart.get(c.rng, c.profile.items().cartSkill(), 20) ? Scores.SPECIALIST : 0;
	}

	/**
	 * When a cart is worth starting: the opponent stuck, standing still or coming straight in (it lands
	 * where it was aimed), or, for a practised cart player, simply on the ground at cart range (they cart
	 * whenever they can; the lead takes care of the rest).
	 */
	static boolean opening(BrainContext c, TargetState t) {
		double speed = Math.sqrt(t.velocity().x() * t.velocity().x() + t.velocity().z() * t.velocity().z());
		Vec3 toUs = c.self.position().subtract(t.position());
		double len = Math.sqrt(toUs.x() * toUs.x() + toUs.z() * toUs.z());
		double closing = len < 1e-6 ? 0 : (t.velocity().x() * toUs.x() + t.velocity().z() * toUs.z()) / len;
		boolean predictable = t.inWeb() || t.onGround() && speed < STILL || closing > speed * 0.8 && closing > 0.1;
		// (Not someone sprinting sideways: they cover more ground than the lead can follow before the arrow lands.)
		double lateral = len < 1e-6 ? speed : Math.abs(t.velocity().x() * toUs.z() - t.velocity().z() * toUs.x()) / len;
		boolean inRange = t.onGround() && len >= CART_RANGE_MIN && len <= CART_RANGE_MAX && lateral < 0.2;
		return predictable || inRange && c.profile.items().cartSkill() >= 0.5;
	}

	/** Where the opponent will be when the arrow arrives, as well as the bot's tracking skill can tell. */
	static Vec3 predicted(TargetState t, double trackingLead) {
		if (t.inWeb()) {
			return t.position();
		}
		double scale = COMBO_TICKS * trackingLead;
		double lx = t.velocity().x() * scale;
		double lz = t.velocity().z() * scale;
		double lead = Math.sqrt(lx * lx + lz * lz);
		if (lead > MAX_LEAD) {
			lx *= MAX_LEAD / lead;
			lz *= MAX_LEAD / lead;
		}
		return new Vec3(t.position().x() + lx, t.position().y(), t.position().z() + lz);
	}

	@Override
	public void reset() {
		willCart.reset();
		step = "";
		lastRail = Long.MIN_VALUE / 2;
		railsBefore = -1;
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
		Vec3 ahead = predicted(t, c.profile.aim().trackingLead());
		int rails = inv.count(i -> i.kind() == ItemKind.BLOCK && "minecraft:rail".equals(i.id()));
		if (railsBefore >= 0 && rails < railsBefore) {
			lastRail = c.observation.tick();
		}
		railsBefore = rails;

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
			.filter(r -> r.horizontalDistanceTo(ahead) <= BlockPlay.NEAR_TARGET && r.y() + BlockPlay.RAIL_HEIGHT < eye.y()
				&& BlockPlay.worth(c, center(r, 0), PLANNED_POWER, skill))
			.min(Comparator.comparingDouble(r -> r.horizontalDistanceTo(ahead)));
		if (rail.isPresent()) {
			step = "place cart";
			c.memory.ownCart = center(rail.get(), 0);
			c.memory.ownCartAt = c.observation.tick();
			return BlockPlay.clickTop(c, rail.get(), BlockPlay.RAIL_HEIGHT, cartSlot);
		}

		int railItem = inv.hotbarSlot(i -> i.kind() == ItemKind.BLOCK && "minecraft:rail".equals(i.id()));
		Optional<BlockSpot> spot = railItem < 0 || cartSlot < 0 ? Optional.empty() : c.world.groundSpots().stream()
			.filter(s -> {
				// Where they are going, but not under them now (they would ride the cart away).
				return s.horizontalDistanceTo(ahead) <= BlockPlay.NEAR_TARGET && s.horizontalDistanceTo(t.position()) >= 0.9 && s.y() + 1 < eye.y()
					&& BlockPlay.worth(c, center(s, 1), PLANNED_POWER, skill);
			})
			.min(Comparator.comparingDouble(s -> s.horizontalDistanceTo(ahead)));
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
		boolean drawing = inv.usingItem() && inv.usingKind() == ItemKind.BOW;
		boolean drawn = drawing && inv.useTicks() >= DRAW_TICKS;
		boolean aimed = c.aimError(goalYaw, goalPitch) < SHOT_TOLERANCE;
		// Holding right click draws; letting go fires (a short draw: never on to a full one).
		boolean use = armed && !(drawn && aimed) && !(drawing && inv.useTicks() >= LATEST_RELEASE);
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
