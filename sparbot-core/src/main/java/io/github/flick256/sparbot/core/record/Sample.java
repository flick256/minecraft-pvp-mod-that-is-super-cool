package io.github.flick256.sparbot.core.record;

import io.github.flick256.sparbot.core.act.Inputs;
import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;

/**
 * One fighter in one tick of a recording: where they were, what they could see of themselves, and the
 * inputs they pressed. For a bot the inputs are exactly what its brain sent; for a human they are
 * read back from what their client told the server (movement keys, rotation, swings, item use,
 * hotbar), which is what Super Mode learns from.
 *
 * @param mainHandId item id in the main hand, for showing it in a replay
 */
public record Sample(
	Vec3 position,
	Vec3 velocity,
	float yaw,
	float pitch,
	float health,
	float absorption,
	float attackStrength,
	boolean onGround,
	int hurtTime,
	int selectedSlot,
	ItemKind mainHand,
	ItemKind offhand,
	ItemKind usingKind,
	String mainHandId,
	Inputs inputs
) {
}
