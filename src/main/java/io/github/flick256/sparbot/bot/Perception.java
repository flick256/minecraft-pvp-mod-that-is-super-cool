package io.github.flick256.sparbot.bot;

import io.github.flick256.sparbot.core.item.ItemKind;
import io.github.flick256.sparbot.core.math.Vec3;
import io.github.flick256.sparbot.core.sense.BlockSpot;
import io.github.flick256.sparbot.core.sense.EffectInfo;
import io.github.flick256.sparbot.core.sense.Observation;
import io.github.flick256.sparbot.core.sense.SelfState;
import io.github.flick256.sparbot.core.sense.Surroundings;
import io.github.flick256.sparbot.core.sense.TargetState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.vehicle.minecart.AbstractMinecart;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
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
	/** How far ahead (blocks) the bot looks for lava and fire at its own level. */
	private static final double HAZARD_LOOKAHEAD = 1.6;
	/** How far around its feet the bot looks for blocks to put crystals or obsidian on. */
	private static final int BLOCK_SCAN = 5;
	/** Forget an opponent not seen for this long (10 s). */
	private static final int FORGET_AFTER_TICKS = 200;

	private @Nullable UUID trackedId;
	private @Nullable TargetState lastSeen;
	private @Nullable Vec3 lastSeenPosition;
	private int ticksSinceSeen;

	Observation observe(Bot bot, BotPlayer self, long tick, double awarenessRadius, boolean autoTarget, boolean autoTargetBots) {
		LivingEntity target = chooseTarget(bot, self, awarenessRadius, autoTarget, autoTargetBots);
		return new Observation(tick, selfState(self, target), targetState(self, target, awarenessRadius), surroundings(self, awarenessRadius));
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
				target.getBbWidth() / 2.0, target.getBbHeight(),
				ItemClassifier.classify(target.getMainHandItem()), ItemClassifier.classify(target.getOffhandItem()),
				target.isUsingItem() ? ItemClassifier.classify(target.getUseItem()) : ItemKind.EMPTY, target.getArmorValue(), target.isOnFire(), inWeb(target));
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
			lastSeen.maxHealth(), lastSeen.onGround(), 0, false, false, ticksSinceSeen, lastSeen.halfWidth(), lastSeen.height(),
			lastSeen.mainHand(), lastSeen.offhand(), ItemKind.EMPTY, lastSeen.armorPoints());
	}

	private static SelfState selfState(BotPlayer self, @Nullable LivingEntity target) {
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
			dropDepths(self),
			ItemClassifier.inventory(self),
			effects(self),
			self.isOnFire(),
			self.isInLava(),
			inWeb(self));
	}

	private static List<EffectInfo> effects(BotPlayer self) {
		List<EffectInfo> effects = new ArrayList<>();
		for (MobEffectInstance effect : self.getActiveEffects()) {
			effects.add(new EffectInfo(effect.getEffect().getRegisteredName(), effect.getAmplifier(),
				effect.isInfiniteDuration() ? -1 : effect.getDuration()));
		}
		return effects;
	}

	/**
	 * Crystals and TNT minecarts in sight, and blocks within reach that a crystal, obsidian, a rail or a
	 * minecart could go on. Only scanned while the bot carries end crystals or TNT minecarts: nothing
	 * else uses it.
	 */
	private static Surroundings surroundings(BotPlayer self, double awarenessRadius) {
		if (!self.getInventory().contains(stack -> stack.is(Items.END_CRYSTAL) || stack.is(Items.TNT_MINECART))) {
			return Surroundings.EMPTY;
		}
		ServerLevel level = self.level();
		AABB around = self.getBoundingBox().inflate(awarenessRadius);
		List<Vec3> crystals = new ArrayList<>();
		for (EndCrystal crystal : level.getEntitiesOfClass(EndCrystal.class, around, c -> c.isAlive() && self.hasLineOfSight(c))) {
			crystals.add(new Vec3(crystal.getX(), crystal.getY(), crystal.getZ()));
		}
		List<Vec3> carts = new ArrayList<>();
		for (MinecartTNT cart : level.getEntitiesOfClass(MinecartTNT.class, around, c -> c.isAlive() && self.hasLineOfSight(c))) {
			carts.add(new Vec3(cart.getX(), cart.getY(), cart.getZ()));
		}
		List<BlockSpot> bases = new ArrayList<>();
		List<BlockSpot> groundSpots = new ArrayList<>();
		List<BlockSpot> rails = new ArrayList<>();
		BlockPos feet = self.blockPosition();
		for (int dx = -BLOCK_SCAN; dx <= BLOCK_SCAN; dx++) {
			for (int dz = -BLOCK_SCAN; dz <= BLOCK_SCAN; dz++) {
				for (int dy = -3; dy <= 1; dy++) {
					BlockPos pos = feet.offset(dx, dy, dz);
					if (!self.isWithinBlockInteractionRange(pos, 0.0)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.is(BlockTags.RAILS)) {
						if (level.getEntitiesOfClass(AbstractMinecart.class, new AABB(pos)).isEmpty()) {
							rails.add(new BlockSpot(pos.getX(), pos.getY(), pos.getZ()));
						}
						continue;
					}
					BlockPos above = pos.above();
					if (!level.isEmptyBlock(above)) {
						continue;
					}
					if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.BEDROCK)) {
						// EndCrystalItem#useOn: no entity may be in the 1x2x1 space the crystal fills.
						AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0, above.getY() + 2.0, above.getZ() + 1.0);
						if (level.getEntities((Entity) null, space).isEmpty()) {
							bases.add(new BlockSpot(pos.getX(), pos.getY(), pos.getZ()));
						}
					} else if (state.isFaceSturdy(level, pos, Direction.UP) && level.isEmptyBlock(above.above())
						&& level.isUnobstructed(Blocks.OBSIDIAN.defaultBlockState(), above, CollisionContext.empty())) {
						groundSpots.add(new BlockSpot(pos.getX(), pos.getY(), pos.getZ()));
					}
				}
			}
		}
		return new Surroundings(crystals, bases, groundSpots, carts, rails);
	}

	/**
	 * For each of 8 compass directions one block away, how far the ground drops below the bot's feet.
	 * Lava counts as a lethal drop, also lava or fire at the bot's own level; water counts as ground (a
	 * safe landing).
	 */
	/** Whether any cobweb overlaps the entity's box (what slows it to a crawl, and what a player can see). */
	private static boolean inWeb(LivingEntity entity) {
		return entity.level().getBlockStates(entity.getBoundingBox().deflate(1.0E-3)).anyMatch(s -> s.is(Blocks.COBWEB));
	}

	private static boolean burns(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE);
	}

	private static int[] dropDepths(BotPlayer self) {
		int[] depths = new int[SelfState.DIRECTIONS];
		ServerLevel level = self.level();
		int feetY = BlockPos.containing(self.position()).getY();
		for (int i = 0; i < SelfState.DIRECTIONS; i++) {
			double yaw = Math.toRadians(i * 45.0);
			double x = self.getX() - Math.sin(yaw);
			double z = self.getZ() + Math.cos(yaw);
			depths[i] = SelfState.VOID_DROP;
			// Lava or fire at the bot's own level is as deadly as a cliff; it is looked for a little
			// further ahead, since a sprinting player can't stop within a block.
			if (burns(level, BlockPos.containing(x, feetY, z))
				|| burns(level, BlockPos.containing(self.getX() - Math.sin(yaw) * HAZARD_LOOKAHEAD, feetY, self.getZ() + Math.cos(yaw) * HAZARD_LOOKAHEAD))) {
				continue;
			}
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
