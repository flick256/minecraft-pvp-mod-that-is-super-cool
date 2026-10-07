package io.github.flick256.sparbot.content;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The Heart of Aster: the star that beat in Vaelor's chest. Use it and it mends you (three hearts at once, then
 * Regeneration II for five seconds), shields you (Absorption II for forty) and hardens you (Resistance I for
 * eight). It isn't used up, but it needs a minute and a half before it answers again.
 */
public class HeartOfAsterItem extends Item {
	public static final int COOLDOWN = 1800;

	public HeartOfAsterItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server) {
			player.heal(6.0F);
			player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
			player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 800, 1));
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 160, 0));
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			server.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.5, 0.8, 0.5, 0.08);
			server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.6, player.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.8F);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.4F);
		}
		return InteractionResult.SUCCESS;
	}
}
