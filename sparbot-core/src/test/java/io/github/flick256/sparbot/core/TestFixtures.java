package io.github.flick256.sparbot.core;

import io.github.flick256.sparbot.core.item.InventoryState;
import io.github.flick256.sparbot.core.item.ItemInfo;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.profile.Distribution;
import io.github.flick256.sparbot.core.profile.SkillProfile;
import io.github.flick256.sparbot.core.profile.SkillProfiles;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.List;

/** Shared builders for core tests. */
public final class TestFixtures {
	private TestFixtures() {
	}

	public static SkillProfile preset(String id) {
		return SkillProfiles.loadPresets().get(id);
	}

	/** A profile with no randomness in mistakes, for deterministic assertions. */
	public static SkillProfile flawless(String baseId) {
		SkillProfile p = preset(baseId);
		return new SkillProfile(p.id(), p.displayName(), p.description(), p.reactionTimeMs(), p.pingMs(), p.aim(), p.clicking(),
			new SkillProfile.Reach(Distribution.fixed(0)), p.technique(), p.items(), 0.0, p.panicHealthFraction());
	}

	public static int[] flatGround() {
		return new int[SelfState.DIRECTIONS];
	}

	public static final ItemInfo SWORD = item(ItemKind.SWORD, "minecraft:diamond_sword", 1, 7);
	public static final ItemInfo AXE = item(ItemKind.AXE, "minecraft:diamond_axe", 1, 9);
	public static final ItemInfo GAPPLE = item(ItemKind.GOLDEN_APPLE, "minecraft:golden_apple", 8, 1);
	public static final ItemInfo STEAK = item(ItemKind.FOOD, "minecraft:cooked_beef", 16, 1);
	public static final ItemInfo TOTEM = item(ItemKind.TOTEM, "minecraft:totem_of_undying", 1, 1);
	public static final ItemInfo SHIELD = item(ItemKind.SHIELD, "minecraft:shield", 1, 1);

	public static ItemInfo item(ItemKind kind, String id, int count, double damage) {
		return new ItemInfo(kind, id, count, damage, 1.0, false, false, null);
	}

	/** An inventory with a sword in slot 0 (selected), plus extra items at the given slots. */
	public static InventoryState inventory(Object... slotItemPairs) {
		InventoryState base = InventoryState.empty();
		ItemInfo[] slots = base.slots().clone();
		slots[0] = SWORD;
		ItemInfo offhand = ItemInfo.EMPTY;
		for (int i = 0; i < slotItemPairs.length; i += 2) {
			int slot = (Integer) slotItemPairs[i];
			ItemInfo item = (ItemInfo) slotItemPairs[i + 1];
			if (slot == InventoryState.OFFHAND_BUTTON) {
				offhand = item;
			} else {
				slots[slot] = item;
			}
		}
		return new InventoryState(slots, offhand, base.armor(), 0, false, false, 0, ItemKind.EMPTY, false, false, false);
	}

	public static SelfState self(Vec3 pos, float yaw, float pitch, float health, float attackStrength, boolean onGround, int[] drops) {
		return self(pos, yaw, pitch, health, attackStrength, onGround, drops, inventory(), 20);
	}

	public static SelfState self(Vec3 pos, float yaw, float pitch, float health, float attackStrength, boolean onGround, int[] drops,
		InventoryState inventory, int food) {
		return new SelfState(pos, pos.add(new Vec3(0, 1.62, 0)), Vec3.ZERO, yaw, pitch, health, 20, 0, food, onGround, false, false, false,
			0, attackStrength, 3.0, 0, true, drops, inventory, List.of());
	}

	public static TargetState target(Vec3 pos, int hurtTime) {
		return target(pos, hurtTime, ItemKind.SWORD, ItemKind.EMPTY, ItemKind.EMPTY, false);
	}

	public static TargetState target(Vec3 pos, int hurtTime, ItemKind mainHand, ItemKind offhand, ItemKind using, boolean blocking) {
		return new TargetState(7, "Steve", pos, Vec3.ZERO, 0, 20, 20, true, hurtTime, blocking, true, 0, 0.3, 1.8, mainHand, offhand, using, 15);
	}
}
