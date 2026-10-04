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
	/** End crystals are 2 x 2 x 2. */
	private static final double CRYSTAL_HALF = 1.0;
	private static final double CRYSTAL_HEIGHT = 2.0;
	/** Opponents farther than this are first walked up to. */
	private static final double ENGAGE_RANGE = 8.0;
	/** Closer than this (eye to hitbox) the opponent can hit the bot. */
	private static final double UNDER_PRESSURE = 3.2;

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
		// In the opponent's face with nothing worth blowing up: fight with the sword instead of backing off.
		if (c.targetDistance() < UNDER_PRESSURE && "position".equals(choose(c).step())) {
			return 0;
		}
		return willCrystal.get(c.rng, c.profile.items().crystalSkill(), 40) ? Scores.SPECIALIST : 0;
	}

	@Override
	public void reset() {
		willCrystal.reset();
		step = "";
	}

	/** The most advanced crystal step available right now. */
	private record Choice(String step, Vec3 crystal, BlockSpot block) {
	}

	private static Choice choose(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		Vec3 eye = self.eyePosition();
		double skill = c.profile.items().crystalSkill();
		Optional<Vec3> crystal = c.world.crystals().stream()
			.filter(p -> crystalDistance(eye, p) <= self.attackReach() && BlockPlay.worth(c, p, Explosions.CRYSTAL_POWER, skill))
			.min(Comparator.comparingDouble(p -> -Explosions.rawDamage(p, t.position(), Explosions.CRYSTAL_POWER)));
		if (crystal.isPresent()) {
			return new Choice("hit", crystal.get(), null);
		}
		if (inv.hotbarSlot(ItemKind.END_CRYSTAL) >= 0) {
			Optional<BlockSpot> base = c.world.crystalBases().stream()
				.filter(b -> b.horizontalDistanceTo(t.position()) <= BlockPlay.NEAR_TARGET && b.y() + 1 < eye.y() && hittable(c, b.topCenter())
					&& BlockPlay.worth(c, b.topCenter(), Explosions.CRYSTAL_POWER, skill))
				.min(Comparator.comparingDouble(b -> -Explosions.rawDamage(b.topCenter(), t.position(), Explosions.CRYSTAL_POWER)));
			if (base.isPresent()) {
				return new Choice("place crystal", null, base.get());
			}
		}
		int obsidian = obsidianSlot(inv);
		Optional<BlockSpot> spot = obsidian < 0 ? Optional.empty() : c.world.groundSpots().stream()
			.filter(s -> {
				double d = s.horizontalDistanceTo(t.position());
				// Next to the opponent, not under them (a block can't be placed into a player).
				Vec3 crystalAt = new Vec3(s.x() + 0.5, s.y() + 2.0, s.z() + 0.5);
				return d <= BlockPlay.NEAR_TARGET && d >= 0.9 && s.y() + 1 < eye.y() && hittable(c, crystalAt) && BlockPlay.worth(c, crystalAt, Explosions.CRYSTAL_POWER, skill);
			})
			// Prefer the far side of the opponent: the opponent takes the full blast, the bot less.
			.min(Comparator.comparingDouble(s -> -new Vec3(s.x() + 0.5, s.y() + 2.0, s.z() + 0.5).distanceTo(self.position())
				+ s.horizontalDistanceTo(t.position())));
		if (spot.isPresent()) {
			return new Choice("place obsidian", null, spot.get());
		}
		return new Choice("position", null, null);
	}

	private static int obsidianSlot(InventoryState inv) {
		return inv.hotbarSlot(i -> i.kind() == ItemKind.BLOCK && "minecraft:obsidian".equals(i.id()));
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
		Choice choice = choose(c);
		step = choice.step();
		switch (choice.step()) {
			case "hit" -> {
				Vec3 p = choice.crystal();
				float[] look = c.lookAt(Angles.yawTowards(eye, p.add(new Vec3(0, 1, 0))), Angles.pitchTowards(eye, p.add(new Vec3(0, 1, 0))));
				boolean aimed = BrainContext.rayHitsBox(eye, Angles.lookVector(self.yaw(), self.pitch()),
					new Vec3(p.x() - CRYSTAL_HALF, p.y(), p.z() - CRYSTAL_HALF), new Vec3(p.x() + CRYSTAL_HALF, p.y() + CRYSTAL_HEIGHT, p.z() + CRYSTAL_HALF),
					self.attackReach());
				return BlockPlay.spacing(c, look, -1).withAttack(aimed && !inv.usingItem());
			}
			case "place crystal" -> {
				return BlockPlay.clickTop(c, choice.block(), 1.0, inv.hotbarSlot(ItemKind.END_CRYSTAL));
			}
			case "place obsidian" -> {
				return BlockPlay.clickTop(c, choice.block(), 1.0, obsidianSlot(inv));
			}
			default -> {
				return BlockPlay.position(c);
			}
		}
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
