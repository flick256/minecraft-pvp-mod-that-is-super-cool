package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.aim.Explosions;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.Surroundings;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Comparator;
import java.util.Optional;

/**
 * Respawn anchors in crystal PvP. 26.2 facts (RespawnAnchorBlock): using glowstone on an anchor adds a
 * charge (up to 4); using it with anything else while it holds a charge, anywhere but the Nether,
 * blows it up: a power-5 blast with fire, centred on the block (only water next to it softens it).
 * So the bot puts an anchor down next to the opponent, charges it once with glowstone, and sets it off
 * with its hotbar totem in hand (as players do; the sword without one), one real click each, with the profile's aim and hotbar time. It only goes for
 * the blast when the opponent takes more of it than it does (as with crystals), and finishes an anchor
 * it has started.
 */
public final class AnchorTactic implements Tactic {
	/** Opponents farther than this are first walked up to. */
	private static final double ENGAGE_RANGE = 8.0;
	/** Closer than this (eye to hitbox) the opponent can hit the bot. */
	private static final double UNDER_PRESSURE = 3.2;
	/** An anchor this far (horizontally) from the opponent still catches them. */
	private static final double NEAR = 2.2;
	/** One step that won't happen in this many ticks (the anchor can't be clicked, say) ends the try. */
	private static final int STEP_TIMEOUT = 20;
	private static final int COOLDOWN = 30;

	private final Decision willAnchor = new Decision();
	private String step = "";
	private String lastStep = "";
	private int stepTicks;
	private long gaveUpAt = Long.MIN_VALUE / 2;

	@Override
	public String name() {
		return "anchor";
	}

	@Override
	public String detail() {
		return step;
	}

	@Override
	public double score(BrainContext c) {
		InventoryState inv = c.self.inventory();
		TargetState t = c.seen();
		if (c.target == null || t == null || !t.visible() || c.targetDistance() > ENGAGE_RANGE || c.observation.tick() - gaveUpAt < COOLDOWN) {
			return 0;
		}
		Choice choice = choose(c);
		if (choice.step().equals("detonate") || choice.step().equals("charge")) {
			// An anchor is down by them: see it through (a half-done anchor is just a block in the way).
			return Scores.SPECIALIST + 0.05;
		}
		if (anchorSlot(inv) < 0 || glowstoneSlot(inv) < 0 || choice.step().equals("position") && c.targetDistance() < UNDER_PRESSURE) {
			return 0;
		}
		// Slightly behind crystals: the anchor is the burst for when a crystal spot isn't there.
		return willAnchor.get(c.rng, c.profile.items().crystalSkill(), 40) ? Scores.SPECIALIST - 0.01 : 0;
	}

	@Override
	public void reset() {
		willAnchor.reset();
		step = "";
		lastStep = "";
		stepTicks = 0;
		gaveUpAt = Long.MIN_VALUE / 2;
	}

	private record Choice(String step, BlockSpot block) {
	}

	/** The most advanced anchor step available right now. */
	private Choice choose(BrainContext c) {
		SelfState self = c.self;
		InventoryState inv = self.inventory();
		TargetState t = c.seen();
		Vec3 eye = self.eyePosition();
		double skill = c.profile.items().crystalSkill();
		Optional<Surroundings.Anchor> near = c.world.anchors().stream()
			.filter(a -> a.spot().horizontalDistanceTo(t.position()) <= NEAR && a.spot().y() < eye.y() && BlockPlay.worth(c, center(a.spot()), Explosions.ANCHOR_POWER, skill))
			.min(Comparator.comparingDouble(a -> -Explosions.rawDamage(center(a.spot()), t.position(), Explosions.ANCHOR_POWER)));
		if (near.isPresent()) {
			Surroundings.Anchor a = near.get();
			if (a.charge() > 0) {
				return new Choice("detonate", a.spot());
			}
			if (glowstoneSlot(inv) >= 0) {
				return new Choice("charge", a.spot());
			}
		}
		if (anchorSlot(inv) >= 0) {
			Optional<BlockSpot> spot = c.world.groundSpots().stream()
				.filter(s -> {
					double d = s.horizontalDistanceTo(t.position());
					BlockSpot anchor = new BlockSpot(s.x(), s.y() + 1, s.z());
					// Next to the opponent, not under them (a block can't be placed into a player).
					return d <= NEAR && d >= 0.9 && s.y() + 1 < eye.y() && BlockPlay.worth(c, center(anchor), Explosions.ANCHOR_POWER, skill);
				})
				// The far side of the opponent: they take the full blast, the bot less.
				.min(Comparator.comparingDouble(s -> -center(new BlockSpot(s.x(), s.y() + 1, s.z())).distanceTo(self.position()) + s.horizontalDistanceTo(t.position())));
			if (spot.isPresent()) {
				return new Choice("place anchor", spot.get());
			}
		}
		return new Choice("position", null);
	}

	@Override
	public Inputs act(BrainContext c) {
		InventoryState inv = c.self.inventory();
		if (c.seen() == null) {
			return Inputs.IDLE;
		}
		Choice choice = choose(c);
		step = choice.step();
		stepTicks = step.equals(lastStep) ? stepTicks + 1 : 0;
		lastStep = step;
		if (stepTicks > STEP_TIMEOUT && !step.equals("position")) {
			gaveUpAt = c.observation.tick();
			willAnchor.reset();
			return BlockPlay.position(c);
		}
		switch (step) {
			case "detonate" -> {
				// Anything but glowstone sets it off. Players use the hotbar totem: it has no use of its own, and
				// it is in hand ready to swap if the blast pops theirs. No totem: the sword.
				int totem = inv.hotbarSlot(ItemKind.TOTEM);
				int sword = Hands.preferredMeleeSlot(inv);
				int slot = totem >= 0 ? totem : sword >= 0 && !isGlowstone(inv, sword) ? sword : nonGlowstoneSlot(inv);
				return BlockPlay.clickTop(c, choice.block(), 1.0, slot);
			}
			case "charge" -> {
				return BlockPlay.clickTop(c, choice.block(), 1.0, glowstoneSlot(inv));
			}
			case "place anchor" -> {
				return BlockPlay.clickTop(c, choice.block(), 1.0, anchorSlot(inv));
			}
			default -> {
				return BlockPlay.position(c);
			}
		}
	}

	/** The blast's centre: the middle of the anchor block. */
	private static Vec3 center(BlockSpot anchor) {
		return new Vec3(anchor.x() + 0.5, anchor.y() + 0.5, anchor.z() + 0.5);
	}

	static int anchorSlot(InventoryState inv) {
		return inv.hotbarSlot(i -> i.kind() == ItemKind.BLOCK && "minecraft:respawn_anchor".equals(i.id()));
	}

	static int glowstoneSlot(InventoryState inv) {
		return inv.hotbarSlot(i -> "minecraft:glowstone".equals(i.id()));
	}

	private static boolean isGlowstone(InventoryState inv, int slot) {
		return "minecraft:glowstone".equals(inv.slot(slot).id());
	}

	/** Any hotbar slot that doesn't hold glowstone (an empty one will do). */
	private static int nonGlowstoneSlot(InventoryState inv) {
		for (int i = 0; i < InventoryState.HOTBAR_SIZE; i++) {
			if (!isGlowstone(inv, i)) {
				return i;
			}
		}
		return -1;
	}
}
