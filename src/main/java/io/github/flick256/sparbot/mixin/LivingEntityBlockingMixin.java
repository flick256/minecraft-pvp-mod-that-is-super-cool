package io.github.flick256.sparbot.mixin;

import io.github.flick256.sparbot.SparBot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the skill drills see a hit landing on a raised shield. Observes only. */
@Mixin(LivingEntity.class)
abstract class LivingEntityBlockingMixin {
	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void sparbot$noteBlock(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValueF() > 0) {
			SparBot.drills().onBlocked((LivingEntity) (Object) this, source);
		}
	}
}
