package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Explosions;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Comparator;
import java.util.Optional;

/**
 * Crystal PvP. 26.2 facts: an end crystal can only be placed on top of obsidian or bedrock with air
 * above and no entity in the 1x2x1 space it will fill (EndCrystalItem#useOn); hitting it with any
 * attack makes it explode with power 6 (EndCrystal#hurtServer), hurting everything within 12 blocks,
 * the placer included (see {@link Explosions}). It is 2x2x2 blocks, so it is easy to hit.
 *
 * <p>Each tick the bot does the most advanced step available: hit a crystal that would hurt the
 * opponent, else put a crystal on obsidian next to them, else put down obsidian next to them, while
 * holding 3-4.5 blocks of distance. Every step is a real click on the real block or entity the
 * crosshair is on, with the profile's aim, hotbar time and click limits. Skill decides how often it
 * goes for crystals and how much self-damage it accepts.
 */
public final class CrystalTactic implements Tactic {
	/** Vanilla block_interaction_range. */
	static final double BLOCK_REACH = 4.5;
	/** End crystals are 2 x 2 x 2. */
	private static final double CRYSTAL_HALF = 1.0;
	private static final double CRYSTAL_HEIGHT = 2.0;
	/** Below this much raw damage to the opponent a crystal isn't worth it. */
	private static final double MIN_TARGET_DAMAGE = 12.0;
	/** Opponents farther than this are first walked up to. */
	private static final double ENGAGE_RANGE = 8.0;
	/** Crystal centre within this horizontal distance of the opponent's feet. */
	private static final double NEAR_TARGET = 2.6;
	private static final double TOO_CLOSE = 2.5;
	private static final double TOO_FAR = 4.5;
	/** Aim this far inside a block face's edge so aim jitter stays on the face. */
	private static final double FACE_INSET = 0.15;

	private final Decision willCrystal = new Decision();
	private String step = "";

	@Override
	public String name() {
		return "crystal";
	}

	@Override
	public String detail() {
		return step;
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || inv.hotbarSlot(ItemKind.END_CRYSTAL) < 0) {
			return 0;
		}
		if (c.targetDistance() > ENGAGE_RANGE) {
			return 0;
		}
		return willCrystal.get(c.rng, c.profile.items().crystalSkill(), 40) ? Scores.SPECIALIST : 0;
	}

	@Override
	public void reset() {
		willCrystal.reset();
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

		Optional<Vec3> crystal = c.world.crystals().stream()
			.filter(p -> crystalDistance(eye, p) <= self.attackReach() && worth(c, p))
			.min(Comparator.comparingDouble(p -> -Explosions.rawDamage(p, t.position(), Explosions.CRYSTAL_POWER)));
		if (crystal.isPresent()) {
			step = "hit";
			Vec3 p = crystal.get();
			float[] look = c.lookAt(Angles.yawTowards(eye, p.add(new Vec3(0, 1, 0))), Angles.pitchTowards(eye, p.add(new Vec3(0, 1, 0))));
			boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()),
				new Vec3(p.x() - CRYSTAL_HALF, p.y(), p.z() - CRYSTAL_HALF), new Vec3(p.x() + CRYSTAL_HALF, p.y() + CRYSTAL_HEIGHT, p.z() + CRYSTAL_HALF),
				self.attackReach());
			return spacing(c, look, -1).withAttack(aimed && !inv.usingItem());
		}

		Optional<BlockSpot> base = c.world.crystalBases().stream()
			.filter(b -> b.horizontalDistanceTo(t.position()) <= NEAR_TARGET && b.y() + 1 < eye.y() && hittable(c, b.topCenter())
				&& worth(c, b.topCenter()))
			.min(Comparator.comparingDouble(b -> -Explosions.rawDamage(b.topCenter(), t.position(), Explosions.CRYSTAL_POWER)));
		if (base.isPresent()) {
			step = "place crystal";
			return clickTop(c, base.get(), inv.hotbarSlot(ItemKind.END_CRYSTAL));
		}

		int obsidian = inv.hotbarSlot(i -> i.kind() == ItemKind.BLOCK && "minecraft:obsidian".equals(i.id()));
		Optional<BlockSpot> spot = obsidian < 0 ? Optional.empty() : c.world.obsidianSpots().stream()
			.filter(s -> {
				double d = s.horizontalDistanceTo(t.position());
				// Next to the opponent, not under them (a block can't be placed into a player).
				Vec3 crystalAt = new Vec3(s.x() + 0.5, s.y() + 2.0, s.z() + 0.5);
				return d <= NEAR_TARGET && d >= 0.9 && s.y() + 1 < eye.y() && hittable(c, crystalAt) && worth(c, crystalAt);
			})
			// Prefer the far side of the opponent: the opponent takes the full blast, the bot less.
			.min(Comparator.comparingDouble(s -> -new Vec3(s.x() + 0.5, s.y() + 2.0, s.z() + 0.5).distanceTo(self.position())
				+ s.horizontalDistanceTo(t.position())));
		if (spot.isPresent()) {
			step = "place obsidian";
			return clickTop(c, spot.get(), obsidian);
		}

		step = "position";
		Vec3 chest = t.chest();
		float[] look = c.lookAt(Angles.yawTowards(eye, chest), Angles.pitchTowards(eye, chest));
		return spacing(c, look, -1);
	}

	/**
	 * Holds {@code slot}, looks at the top face of {@code block} and right-clicks once the crosshair is
	 * on it. It aims at the part of the face nearest to it, which may be in reach when the centre isn't.
	 */
	private Inputs clickTop(BrainContext c, BlockSpot block, int slot) {
		SelfState self = c.self;
		Vec3 eye = self.eyePosition();
		Vec3 top = new Vec3(Math.max(block.x() + FACE_INSET, Math.min(eye.x(), block.x() + 1 - FACE_INSET)), block.y() + 1.0,
			Math.max(block.z() + FACE_INSET, Math.min(eye.z(), block.z() + 1 - FACE_INSET)));
		float[] look = c.lookAt(Angles.yawTowards(eye, top), Angles.pitchTowards(eye, top));
		int press = c.memory.hands.request(c, slot);
		Vec3 dir = Angles.lookVector(self.yaw(), self.pitch());
		Vec3 min = new Vec3(block.x(), block.y(), block.z());
		Vec3 max = new Vec3(block.x() + 1, block.y() + 1, block.z() + 1);
		double entry = BrainContext.rayEntry(eye, dir, min, max, BLOCK_REACH);
		boolean onTop = entry >= 0 && eye.y() + dir.y() * entry >= block.y() + 1 - 1e-3;
		boolean click = onTop && self.inventory().selectedSlot() == slot && !self.inventory().usingItem();
		return spacing(c, look, press).withUse(click);
	}

	/** Holds 3-4.5 blocks from the opponent, strafing a little, with the given look and hotbar press. */
	private static Inputs spacing(BrainContext c, float[] look, int press) {
		double d = c.targetDistance();
		int forward = d > TOO_FAR ? 1 : d < TOO_CLOSE ? -1 : 0;
		DuelMemory m = c.memory;
		if (--m.ticksUntilStrafeSwitch <= 0) {
			m.strafeDirection = -m.strafeDirection;
			m.ticksUntilStrafeSwitch = c.rng.nextInt(8, 25);
			m.strafeActive = c.rng.chance(c.profile.technique().strafeSkill() * 0.25);
		}
		int strafe = m.strafeActive ? m.strafeDirection : 0;
		boolean jump = c.self.horizontalCollision() && c.self.onGround() && forward > 0;
		return Movement.guardEdges(c, new Inputs(look[0], look[1], forward, strafe, jump, false, d > TOO_FAR + 2, false, false, press));
	}

	/**
	 * Whether a crystal exploding at {@code center} is worth it: it must hurt the opponent, and the
	 * bot's own share must stay under a limit that shrinks with skill. A skilled player only blows up
	 * crystals closer to the opponent than to themselves; a beginner blows themselves up.
	 */
	static boolean worth(BrainContext c, Vec3 center) {
		double toTarget = Explosions.rawDamage(center, c.seen().position(), Explosions.CRYSTAL_POWER);
		double toSelf = Explosions.rawDamage(center, c.self.position(), Explosions.CRYSTAL_POWER);
		double maxSelfShare = 1.25 - 0.35 * c.profile.items().crystalSkill();
		return toTarget >= MIN_TARGET_DAMAGE && toSelf <= toTarget * maxSelfShare;
	}

	/**
	 * A crystal placed there could be hit from here. Blocks are reachable to 4.5 but entities only to
	 * 3, so a crystal placed at the edge of block reach couldn't be detonated.
	 */
	private static boolean hittable(BrainContext c, Vec3 crystal) {
		return crystalDistance(c.self.eyePosition(), crystal) <= c.self.attackReach() - 0.25;
	}

	/** Eye distance to the nearest point of a crystal's 2x2x2 box. */
	static double crystalDistance(Vec3 eye, Vec3 crystal) {
		double x = Math.max(crystal.x() - CRYSTAL_HALF, Math.min(eye.x(), crystal.x() + CRYSTAL_HALF));
		double y = Math.max(crystal.y(), Math.min(eye.y(), crystal.y() + CRYSTAL_HEIGHT));
		double z = Math.max(crystal.z() - CRYSTAL_HALF, Math.min(eye.z(), crystal.z() + CRYSTAL_HALF));
		return eye.distanceTo(new Vec3(x, y, z));
	}
}
