package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.act.Inputs;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everything a vanilla 26.2 client does between the keyboard/mouse and the server, re-implemented
 * for the bot from the decompiled client (KeyboardInput, LocalPlayer, Minecraft#startAttack,
 * MultiPlayerGameMode). Every action reaches the server as the same serverbound packet a real client
 * sends and goes through the same server-side validation.
 */
public final class ClientEmulator {
	/** Minecraft#startAttack: a survival-mode click that hits nothing blocks clicking for 10 ticks. */
	private static final int MISS_TIME_TICKS = 10;

	private Input keys = Input.EMPTY;
	private boolean useHeld;
	private int missTime;
	private int useSequence;

	/** Applies one tick of shaped inputs. Order follows the client: mouse, hotbar, clicks, keys, sprint. */
	void apply(BotPlayer player, Bot bot, Inputs in) {
		ServerGamePacketListenerImpl listener = player.connection;
		if (missTime > 0) {
			missTime--;
		}

		// Mouse movement. Pitch is clamped like Entity#turn.
		player.setYRot(Mth.wrapDegrees(player.getYRot() + in.yawDelta()));
		player.setXRot(Mth.clamp(player.getXRot() + in.pitchDelta(), -90.0F, 90.0F));
		player.setYHeadRot(player.getYRot());

		if (in.hotbarSlot() >= 0 && in.hotbarSlot() != player.getInventory().getSelectedSlot()) {
			listener.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(in.hotbarSlot()));
		}

		if (in.attack()) {
			click(player, bot, listener);
		}
		use(player, listener, in.use());

		Input newKeys = new Input(in.forward() > 0, in.forward() < 0, in.strafe() > 0, in.strafe() < 0, in.jump(), in.sneak(), in.sprint());
		if (!newKeys.equals(keys)) {
			keys = newKeys;
			listener.handlePlayerInput(new ServerboundPlayerInputPacket(newKeys));
		}
		updateSprint(player, listener);
	}

	/** Minecraft#startAttack. Returns without doing anything in the same cases the client does. */
	private void click(BotPlayer player, Bot bot, ServerGamePacketListenerImpl listener) {
		if (missTime > 0) {
			return;
		}
		ItemStack held = player.getMainHandItem();
		if (player.cannotAttackWithItem(held, 0)) {
			return;
		}
		if (held.has(DataComponents.PIERCING_WEAPON)) {
			// Spear stab attacks arrive with the spear milestone; until then a bot never clicks with one.
			return;
		}
		bot.stats().recordSwing();
		HitResult hit = raycast(player);
		switch (hit.getType()) {
			case ENTITY -> {
				Entity target = ((EntityHitResult) hit).getEntity();
				AttackRange range = held.get(DataComponents.ATTACK_RANGE);
				if (range == null || range.isInRange(player, hit.getLocation())) {
					bot.beginAttack(target.getId(), wouldCrit(player, target));
					try {
						listener.handleAttack(new ServerboundAttackPacket(target.getId()));
					} finally {
						bot.endAttack();
					}
				}
			}
			// A bot never starts mining: clicking a block only swings (a deliberate milestone-1 limit).
			case BLOCK -> {
			}
			case MISS -> missTime = MISS_TIME_TICKS;
		}
		listener.handleAnimate(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));
	}

	/** Right click: Minecraft#startUseItem on press, MultiPlayerGameMode#releaseUsingItem on release. */
	private void use(BotPlayer player, ServerGamePacketListenerImpl listener, boolean pressed) {
		if (pressed && !useHeld) {
			listener.handleUseItem(new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, ++useSequence, player.getYRot(), player.getXRot()));
			if (!player.isUsingItem() && !player.getOffhandItem().isEmpty()) {
				listener.handleUseItem(new ServerboundUseItemPacket(InteractionHand.OFF_HAND, ++useSequence, player.getYRot(), player.getXRot()));
			}
		} else if (!pressed && useHeld && player.isUsingItem()) {
			listener.handlePlayerAction(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN));
		}
		useHeld = pressed;
	}

	/** LocalPlayer#aiStep sprint rules (canStartSprinting / shouldStopRunSprinting / shouldStopSwimSprinting). */
	private void updateSprint(BotPlayer player, ServerGamePacketListenerImpl listener) {
		boolean forwardImpulse = keys.forward() && !keys.backward();
		boolean flying = player.getAbilities().flying;
		if (!player.isSprinting()) {
			boolean canStart = forwardImpulse
				&& sprintingPossible(player, flying)
				&& !slowDueToUsingItem(player)
				&& (!player.isFallFlying() || player.isUnderWater())
				&& (!movingSlowly(player) || player.isUnderWater());
			if (canStart && keys.sprint()) {
				listener.handlePlayerCommand(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
			}
		} else {
			boolean stop = player.isSwimming()
				? !sprintingPossible(player, true) || !player.isInWater() || !forwardImpulse && !player.onGround() && !keys.shift()
				: !sprintingPossible(player, flying) || !forwardImpulse || player.horizontalCollision && !player.minorHorizontalCollision;
			if (stop) {
				listener.handlePlayerCommand(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
			}
		}
	}

	/** KeyboardInput#tick + LocalPlayer#applyInput/modifyInput, applied to the input the server accepted. */
	void applyMovementInput(BotPlayer player) {
		Input input = player.getLastClientInput();
		float left = impulse(input.left(), input.right());
		float forward = impulse(input.forward(), input.backward());
		float length = Mth.sqrt(left * left + forward * forward);
		if (length > 0) {
			left /= length;
			forward /= length;
			float scale = 0.98F;
			if (player.isUsingItem() && !player.isPassenger()) {
				scale *= player.getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).speedMultiplier();
			}
			if (movingSlowly(player)) {
				scale *= (float) player.getAttributeValue(Attributes.SNEAKING_SPEED);
			}
			left *= scale;
			forward *= scale;
			// modifyInputSpeedForSquareMovement: diagonal input reaches the corners of the unit square.
			float scaledLength = Mth.sqrt(left * left + forward * forward);
			float dirX = Math.abs(left / scaledLength);
			float dirY = Math.abs(forward / scaledLength);
			float tan = dirY > dirX ? dirX / dirY : dirY / dirX;
			float modified = Math.min(scaledLength * Mth.sqrt(1.0F + tan * tan), 1.0F);
			left = left / scaledLength * modified;
			forward = forward / scaledLength * modified;
		}
		player.xxa = left;
		player.zza = forward;
		player.setJumping(input.jump());
	}

	/** LocalPlayer#raycastHitResult with partial tick 1.0 (the server has no frames between ticks). */
	static HitResult raycast(BotPlayer player) {
		ItemStack active = player.getActiveItem();
		AttackRange range = active.get(DataComponents.ATTACK_RANGE);
		double blockRange = player.blockInteractionRange();
		HitResult hit = null;
		if (range != null) {
			hit = range.getClosesetHit(player, 1.0F, EntitySelector.CAN_BE_PICKED);
			if (hit instanceof BlockHitResult) {
				hit = filter(hit, player.getEyePosition(1.0F), blockRange);
			}
		}
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			hit = pick(player, blockRange, player.entityInteractionRange());
		}
		return hit;
	}

	/** LocalPlayer#pick. */
	private static HitResult pick(BotPlayer player, double blockRange, double entityRange) {
		double maxDistance = Math.max(blockRange, entityRange);
		double maxDistanceSq = maxDistance * maxDistance;
		Vec3 from = player.getEyePosition(1.0F);
		HitResult blockHit = player.pick(maxDistance, 1.0F, false);
		double blockDistanceSq = blockHit.getLocation().distanceToSqr(from);
		if (blockHit.getType() != HitResult.Type.MISS) {
			maxDistanceSq = blockDistanceSq;
			maxDistance = Math.sqrt(maxDistanceSq);
		}
		Vec3 direction = player.getViewVector(1.0F);
		Vec3 to = from.add(direction.x * maxDistance, direction.y * maxDistance, direction.z * maxDistance);
		AABB box = player.getBoundingBox().expandTowards(direction.scale(maxDistance)).inflate(1.0, 1.0, 1.0);
		EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(player, from, to, box, EntitySelector.CAN_BE_PICKED, maxDistanceSq);
		return entityHit != null && entityHit.getLocation().distanceToSqr(from) < blockDistanceSq
			? filter(entityHit, from, entityRange)
			: filter(blockHit, from, blockRange);
	}

	/** LocalPlayer#filterHitResult. */
	private static HitResult filter(HitResult hit, Vec3 from, double maxRange) {
		Vec3 location = hit.getLocation();
		if (!location.closerThan(from, maxRange)) {
			Direction direction = Direction.getApproximateNearest(location.x - from.x, location.y - from.y, location.z - from.z);
			return BlockHitResult.miss(location, direction, BlockPos.containing(location));
		}
		return hit;
	}

	/** Same predicate as Player#attack + Player#canCriticalAttack, evaluated before the attack for stats. */
	private static boolean wouldCrit(BotPlayer player, @Nullable Entity target) {
		return player.getAttackStrengthScale(0.5F) > 0.9F
			&& player.fallDistance > 0.0
			&& !player.onGround()
			&& !player.onClimbable()
			&& !player.isInWater()
			&& !player.isMobilityRestricted()
			&& !player.isPassenger()
			&& target instanceof LivingEntity
			&& !player.isSprinting();
	}

	private static boolean sprintingPossible(BotPlayer player, boolean allowedInShallowWater) {
		boolean enoughFood = player.getFoodData().hasEnoughFood() || player.getAbilities().mayfly;
		boolean vehicleOk = player.getVehicle() == null || player.getVehicle().canSprint() && player.getVehicle().isLocalInstanceAuthoritative();
		return !player.isMobilityRestricted()
			&& (player.isPassenger() ? vehicleOk : enoughFood)
			&& (allowedInShallowWater || !player.isInShallowWater());
	}

	private static boolean slowDueToUsingItem(BotPlayer player) {
		return player.isUsingItem() && !player.getUseItem().getOrDefault(DataComponents.USE_EFFECTS, UseEffects.DEFAULT).canSprint();
	}

	private static boolean movingSlowly(BotPlayer player) {
		return player.isCrouching() || player.isVisuallyCrawling();
	}

	private static float impulse(boolean positive, boolean negative) {
		return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
	}

	public int missTime() {
		return missTime;
	}

	void reset() {
		keys = Input.EMPTY;
		useHeld = false;
		missTime = 0;
	}
}
