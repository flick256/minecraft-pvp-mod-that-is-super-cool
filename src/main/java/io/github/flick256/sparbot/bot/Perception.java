package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Builds the bot's {@link Observation}: only what a player could know. Opponents are perceived only
 * within the awareness radius and line of sight; when sight is lost, the bot keeps the last thing it
 * saw and its information goes stale. Opponent velocity is estimated from how their position changes
 * (what an onlooker sees), not read from server internals.
 */
public final class Perception {
	/** Columns deeper than this count as a void / lethal drop. */
	private static final int MAX_DROP_SCAN = 24;
	/** Forget an opponent not seen for this long (10 s). */
	private static final int FORGET_AFTER_TICKS = 200;

	private @Nullable UUID trackedId;
	private @Nullable TargetState lastSeen;
	private @Nullable Vec3 lastSeenPosition;
	private int ticksSinceSeen;

	Observation observe(Bot bot, BotPlayer self, long tick, double awarenessRadius, boolean autoTarget, boolean autoTargetBots) {
		LivingEntity target = chooseTarget(bot, self, awarenessRadius, autoTarget, autoTargetBots);
		return new Observation(tick, selfState(self), targetState(self, target, awarenessRadius));
	}

	void reset() {
		trackedId = null;
		lastSeen = null;
		lastSeenPosition = null;
		ticksSinceSeen = 0;
	}

	private @Nullable LivingEntity chooseTarget(Bot bot, BotPlayer self, double radius, boolean autoTarget, boolean autoTargetBots) {
		ServerLevel level = self.level();
		if (bot.assignedTarget() != null) {
			if (level.getEntity(bot.assignedTarget()) instanceof LivingEntity assigned && assigned.isAlive()) {
				return assigned;
			}
			return null;
		}
		if (!autoTarget) {
			return null;
		}
		// Keep fighting the current opponent while it is still a valid target.
		if (trackedId != null && level.getEntity(trackedId) instanceof ServerPlayer current && isAutoTargetable(self, current, radius, autoTargetBots)) {
			return current;
		}
		return level.players().stream()
			.filter(p -> isAutoTargetable(self, p, radius, autoTargetBots) && self.hasLineOfSight(p))
			.min(Comparator.comparingDouble(self::distanceToSqr))
			.orElse(null);
	}

	private static boolean isAutoTargetable(BotPlayer self, ServerPlayer candidate, double radius, boolean autoTargetBots) {
		return candidate != self
			&& candidate.isAlive()
			&& !candidate.isSpectator()
			&& !candidate.isCreative()
			&& (autoTargetBots || !(candidate instanceof BotPlayer))
			&& !self.isAlliedTo(candidate)
			&& candidate.distanceToSqr(self) <= radius * radius;
	}

	private @Nullable TargetState targetState(BotPlayer self, @Nullable LivingEntity target, double radius) {
		if (target == null) {
			reset();
			return null;
		}
		if (!target.getUUID().equals(trackedId)) {
			reset();
			trackedId = target.getUUID();
		}
		boolean visible = target.distanceToSqr(self) <= radius * radius && self.hasLineOfSight(target);
		if (visible) {
			Vec3 position = new Vec3(target.getX(), target.getY(), target.getZ());
			Vec3 velocity = lastSeenPosition == null || ticksSinceSeen > 0 ? Vec3.ZERO : position.subtract(lastSeenPosition);
			ticksSinceSeen = 0;
			lastSeenPosition = position;
			lastSeen = new TargetState(target.getId(), target.getPlainTextName(), position, velocity, target.getYRot(),
				target.getHealth(), target.getMaxHealth(), target.onGround(), target.hurtTime, target.isBlocking(), true, 0,
				target.getBbWidth() / 2.0, target.getBbHeight());
			return lastSeen;
		}
		if (lastSeen == null) {
			return null;
		}
		ticksSinceSeen++;
		if (ticksSinceSeen > FORGET_AFTER_TICKS) {
			reset();
			return null;
		}
		// Out of sight: only the stale memory, with no fresh motion information.
		return new TargetState(lastSeen.entityId(), lastSeen.name(), lastSeen.position(), Vec3.ZERO, lastSeen.yaw(), lastSeen.health(),
			lastSeen.maxHealth(), lastSeen.onGround(), 0, false, false, ticksSinceSeen, lastSeen.halfWidth(), lastSeen.height());
	}

	private static SelfState selfState(BotPlayer self) {
		ItemStack held = self.getMainHandItem();
		AttackRange range = held.get(DataComponents.ATTACK_RANGE);
		double reach = range != null ? range.effectiveMaxRange(self) : self.entityInteractionRange();
		net.minecraft.world.phys.Vec3 eye = self.getEyePosition();
		net.minecraft.world.phys.Vec3 velocity = self.getDeltaMovement();
		return new SelfState(
			new Vec3(self.getX(), self.getY(), self.getZ()),
			new Vec3(eye.x, eye.y, eye.z),
			new Vec3(velocity.x, velocity.y, velocity.z),
			self.getYRot(),
			self.getXRot(),
			self.getHealth(),
			self.getMaxHealth(),
			self.getAbsorptionAmount(),
			self.getFoodData().getFoodLevel(),
			self.onGround(),
			self.isSprinting(),
			self.isInWater(),
			self.horizontalCollision,
			self.fallDistance,
			self.getAttackStrengthScale(0.5F),
			reach,
			self.hurtTime,
			!held.has(DataComponents.PIERCING_WEAPON),
			dropDepths(self));
	}

	/**
	 * For each of 8 compass directions one block away, how far the ground drops below the bot's feet.
	 * Lava counts as a lethal drop; water counts as ground (a safe landing).
	 */
	private static int[] dropDepths(BotPlayer self) {
		int[] depths = new int[SelfState.DIRECTIONS];
		ServerLevel level = self.level();
		int feetY = BlockPos.containing(self.position()).getY();
		for (int i = 0; i < SelfState.DIRECTIONS; i++) {
			double yaw = Math.toRadians(i * 45.0);
			double x = self.getX() - Math.sin(yaw);
			double z = self.getZ() + Math.cos(yaw);
			depths[i] = SelfState.VOID_DROP;
			for (int drop = 0; drop <= MAX_DROP_SCAN; drop++) {
				BlockPos pos = BlockPos.containing(x, feetY - 1 - drop, z);
				if (pos.getY() < level.getMinY()) {
					break;
				}
				BlockState state = level.getBlockState(pos);
				if (state.getFluidState().is(FluidTags.LAVA)) {
					break;
				}
				if (!state.getCollisionShape(level, pos).isEmpty() || state.getFluidState().is(FluidTags.WATER)) {
					depths[i] = drop;
					break;
				}
			}
		}
		return depths;
	}
}
