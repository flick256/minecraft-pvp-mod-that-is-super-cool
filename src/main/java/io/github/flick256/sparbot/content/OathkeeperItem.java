package io.github.flick256.sparbot.content;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Oathkeeper, Vaelor's greatsword. As a sword it does 14 (slower than a sword, a little faster than an axe); used,
 * it brings the blade down for Starbreak: a shockwave along the ground in front of you that does 10 to everything in
 * it (up to seven blocks out, a cone about ninety degrees wide) and throws it back. Then it needs twelve seconds.
 */
public class OathkeeperItem extends Item {
	public static final int COOLDOWN = 240;
	static final float DAMAGE = 10.0F;
	static final double RANGE = 7.0;

	public OathkeeperItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			starbreak(server, player);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			stack.hurtAndBreak(4, player, hand);
		}
		return InteractionResult.SUCCESS;
	}

	static void starbreak(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = Vec3.directionFromRotation(0, player.getYRot());
		}
		flat = flat.normalize();
		Vec3 from = player.position();
		for (double s = 1; s <= RANGE; s += 0.5) {
			Vec3 p = from.add(flat.scale(s));
			double spread = s * 0.5;
			level.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 0.2, p.z, 3, spread * 0.4, 0.05, spread * 0.4, 0.02);
			if ((int) (s * 2) % 3 == 0) {
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y + 0.6, p.z, 1, 0, 0, 0, 0);
			}
		}
		level.playSound(null, from.x, from.y, from.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.9F, 0.55F);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.TRIDENT_THUNDER, SoundSource.PLAYERS, 0.6F, 1.6F);
		AABB box = player.getBoundingBox().inflate(RANGE, 2, RANGE);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = new Vec3(e.getX() - from.x, 0, e.getZ() - from.z);
			double dist = to.length();
			if (dist > RANGE + e.getBbWidth() / 2 || dist < 1.0E-3 || to.normalize().dot(flat) < Math.cos(Math.toRadians(45))) {
				continue;
			}
			if (e.hurtServer(level, player.damageSources().playerAttack(player), DAMAGE)) {
				Vec3 push = to.normalize().scale(1.1);
				e.push(push.x, 0.45, push.z);
				e.hurtMarked = true;
			}
		}
	}
}
