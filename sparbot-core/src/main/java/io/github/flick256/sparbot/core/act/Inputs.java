package io.github.flick256.sparbot.core.act;

/**
 * Everything the bot may do in one tick, expressed purely as human inputs: mouse movement and key /
 * button state. There is deliberately no "teleport", "set health" or "hit entity X" command: attacks
 * are left clicks resolved by the same crosshair raycast a vanilla client performs.
 *
 * @param forward +1 forward key, -1 back key, 0 neither
 * @param strafe +1 left key, -1 right key, 0 neither (vanilla's leftImpulse convention)
 * @param use right mouse button held
 * @param hotbarSlot 0-8 to switch to that slot (number key / scroll), -1 to keep the current one
 * @param swapOffhand the swap-hands key (F) was pressed this tick
 * @param inventoryOpen the inventory screen is open; while open a player cannot move, look around,
 *     attack or use items (vanilla routes keyboard and mouse to the screen)
 * @param inventoryClick a hover + number-key click inside the open inventory, or null
 * @param holdAttack the attack button is held down: keeps breaking the block under the crosshair
 *     (Minecraft#continueAttack). A press that lands on a player is a click ({@code attack}); holding the
 *     button never repeats clicks
 */
public record Inputs(
	float yawDelta,
	float pitchDelta,
	int forward,
	int strafe,
	boolean jump,
	boolean sneak,
	boolean sprint,
	boolean attack,
	boolean use,
	int hotbarSlot,
	boolean swapOffhand,
	boolean inventoryOpen,
	InventoryClick inventoryClick,
	boolean holdAttack
) {
	public static final Inputs IDLE = new Inputs(0, 0, 0, 0, false, false, false, false, false, -1);

	/** Inputs without inventory interaction. */
	public Inputs(float yawDelta, float pitchDelta, int forward, int strafe, boolean jump, boolean sneak, boolean sprint, boolean attack,
		boolean use, int hotbarSlot) {
		this(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot, false, false, null);
	}

	/** The inventory screen is open (optionally clicking); every other input is released. */
	public static Inputs inInventory(InventoryClick click) {
		return new Inputs(0, 0, 0, 0, false, false, false, false, false, -1, false, true, click);
	}

	/** Inputs without holding the attack button. */
	public Inputs(float yawDelta, float pitchDelta, int forward, int strafe, boolean jump, boolean sneak, boolean sprint, boolean attack,
		boolean use, int hotbarSlot, boolean swapOffhand, boolean inventoryOpen, InventoryClick inventoryClick) {
		this(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, false);
	}

	public Inputs withHoldAttack(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, value);
	}

	public Inputs withRotation(float yaw, float pitch) {
		return new Inputs(yaw, pitch, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withAttack(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, value, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withUse(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, value, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withMovement(int newForward, int newStrafe) {
		return new Inputs(yawDelta, pitchDelta, newForward, newStrafe, jump, sneak, sprint, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withJump(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, value, sneak, sprint, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withSprint(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, value, attack, use, hotbarSlot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withHotbarSlot(int slot) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, use, slot, swapOffhand, inventoryOpen, inventoryClick, holdAttack);
	}

	public Inputs withSwapOffhand(boolean value) {
		return new Inputs(yawDelta, pitchDelta, forward, strafe, jump, sneak, sprint, attack, use, hotbarSlot, value, inventoryOpen, inventoryClick, holdAttack);
	}
}
