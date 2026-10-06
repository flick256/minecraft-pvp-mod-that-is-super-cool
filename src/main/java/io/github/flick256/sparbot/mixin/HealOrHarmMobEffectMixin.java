package io.github.flick256.sparbot.mixin;

import io.github.flick256.sparbot.SparBot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the skill drills see how much of a splashed healing pot reached its thrower ({@code health} is 1 for
 * a pot that bursts on them, less the further away). Observes only.
 */
@Mixin(targets = "net.minecraft.world.effect.HealOrHarmMobEffect")
abstract class HealOrHarmMobEffectMixin {
	@Inject(method = "applyInstantaneousEffect", at = @At("HEAD"))
	private void sparbot$noteSplash(ServerLevel level, @Nullable Entity source, @Nullable Entity indirect, LivingEntity target, int amplifier,
		double health, CallbackInfo ci) {
		SparBot.drills().onInstantEffect((MobEffect) (Object) this, indirect, target, health);
	}
}
