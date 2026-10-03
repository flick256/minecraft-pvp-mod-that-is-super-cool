package io.github.flick256.sparbot.core.brain;

import io.github.flick256.sparbot.core.TestFixtures;
import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Angles;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny stand-in for the game used by brain tests: applies mouse movement, hotbar presses, the
 * swap key, inventory clicks and item-use time the way vanilla would, without Minecraft.
 */
final class BrainHarness {
	final DuelBrain brain;
	ItemInfo[] slots;
	ItemInfo offhand;
	int selected;
	float yaw;
	float pitch;
	float health = 20;
	float attackStrength = 1;
	int useTicks;
	boolean usingOffhand;
	boolean hookOut;
	boolean hookOnTarget;
	TargetState target;
	final List<Inputs> history = new ArrayList<>();

	BrainHarness(SkillProfile profile, InventoryState inventory, TargetState target) {
		this.brain = new DuelBrain(profile, 11);
		this.slots = inventory.slots().clone();
		this.offhand = inventory.offhand();
		this.selected = inventory.selectedSlot();
		this.target = target;
	}

	ItemKind usingKind() {
		if (useTicks == 0) {
			return ItemKind.EMPTY;
		}
		return usingOffhand ? offhand.kind() : slots[selected].kind();
	}

	InventoryState inventory() {
		return new InventoryState(slots, offhand, InventoryState.empty().armor(), selected, useTicks > 0, usingOffhand, useTicks, usingKind(),
			useTicks > 5 && usingKind() == ItemKind.SHIELD, hookOut, hookOnTarget);
	}

	Inputs tick() {
		SelfState self = new SelfState(Vec3.ZERO, new Vec3(0, 1.62, 0), Vec3.ZERO, yaw, pitch, health, 20, 0, 20, true, false, false, false, 0,
			attackStrength, 3.0, 0, true, TestFixtures.flatGround(), inventory(), List.of());
		Inputs in = brain.act(new Observation(history.size(), self, target));
		history.add(in);
		if (in.inventoryOpen()) {
			useTicks = 0;
			if (in.inventoryClick() != null && in.inventoryClick().button() == InventoryState.OFFHAND_BUTTON) {
				ItemInfo moved = slots[in.inventoryClick().slot()];
				slots[in.inventoryClick().slot()] = offhand;
				offhand = moved;
			}
			return in;
		}
		yaw = Angles.wrapDegrees(yaw + in.yawDelta());
		pitch = Math.max(-90, Math.min(90, pitch + in.pitchDelta()));
		if (in.hotbarSlot() >= 0) {
			selected = in.hotbarSlot();
			useTicks = 0;
		}
		if (in.swapOffhand()) {
			ItemInfo main = slots[selected];
			slots[selected] = offhand;
			offhand = main;
		}
		boolean mainUsable = isUsable(slots[selected]);
		if (in.use() && (mainUsable || isUsable(offhand))) {
			usingOffhand = !mainUsable;
			useTicks++;
		} else {
			useTicks = 0;
		}
		return in;
	}

	private static boolean isUsable(ItemInfo item) {
		return switch (item.kind()) {
			case BOW, CROSSBOW, SHIELD, GOLDEN_APPLE, FOOD, ENDER_PEARL, FISHING_ROD -> true;
			default -> false;
		};
	}

	boolean any(java.util.function.Predicate<Inputs> p) {
		return history.stream().anyMatch(p);
	}
}
