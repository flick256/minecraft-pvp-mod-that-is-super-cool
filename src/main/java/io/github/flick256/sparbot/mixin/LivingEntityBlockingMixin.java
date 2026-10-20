package io.github.flick256.sparbot.mixin;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.record.CombatReviewEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets the skill drills see a hit landing on a raised shield. Observes only. */
@Mixin(LivingEntity.class)
abstract class LivingEntityBlockingMixin {
	@org.spongepowered.asm.mixin.Unique private boolean sparbot$raised;
	@org.spongepowered.asm.mixin.Unique private boolean sparbot$usingShield;
	@org.spongepowered.asm.mixin.Unique private boolean sparbot$cooling;
	@org.spongepowered.asm.mixin.Unique private String sparbot$angle;

	@Inject(method = "applyItemBlocking", at = @At("HEAD"))
	private void sparbot$captureBlockState(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		sparbot$raised = self.isBlocking();
		sparbot$usingShield = self.getUseItem().is(Items.SHIELD);
		sparbot$cooling = self instanceof Player player && CombatReviewEvents.shieldCooling(player);
		sparbot$angle = CombatReviewEvents.angle(self, source);
	}

	@Inject(method = "applyItemBlocking", at = @At("RETURN"))
	private void sparbot$noteBlock(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Float> cir) {
		if (cir.getReturnValueF() > 0) {
			SparBot.drills().onBlocked((LivingEntity) (Object) this, source);
		}
		CombatReviewEvents.blocked((LivingEntity) (Object) this, source, damage, cir.getReturnValueF(), sparbot$raised, sparbot$usingShield,
			sparbot$cooling, sparbot$angle);
	}
}
