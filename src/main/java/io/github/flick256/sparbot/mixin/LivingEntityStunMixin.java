package io.github.flick256.sparbot.mixin;

import io.github.flick256.sparbot.SparBot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shield stuns (the shieldStuns setting). In 26.2 a hit the shield blocks completely still starts the
 * blocker's invulnerability (LivingEntity#hurtServer carries on with 0 damage: 20 ticks, last hurt 0), so
 * the second click of a stun (axe on the shield, then straight away the axe or the sword again) lands
 * inside it: no knockback and next to no damage. PvP servers let that second hit through as a hit of its
 * own; with the setting on, a fully blocked hit leaves the blocker's invulnerability as it was. The block
 * itself (no damage, the shield disabled by an axe) is untouched. For players and bots alike.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityStunMixin {
	@Shadow
	protected float lastHurt;
	@Shadow
	public int hurtTime;
	@Shadow
	public int hurtDuration;

	@Unique
	private int sparbot$invulnerableBefore;
	@Unique
	private float sparbot$lastHurtBefore;
	@Unique
	private int sparbot$hurtTimeBefore;
	@Unique
	private int sparbot$hurtDurationBefore;

	@Inject(method = "hurtServer", at = @At("HEAD"))
	private void sparbot$rememberInvulnerability(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		sparbot$invulnerableBefore = self.invulnerableTime;
		sparbot$lastHurtBefore = lastHurt;
		sparbot$hurtTimeBefore = hurtTime;
		sparbot$hurtDurationBefore = hurtDuration;
	}

	@Inject(method = "hurtServer", at = @At("RETURN"))
	private void sparbot$stun(ServerLevel level, DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		// hurtServer returns false for a hit that did nothing: fully blocked (the only such case that touches the
		// invulnerability) or turned away earlier (which changes nothing, so restoring is a no-op).
		if (!cir.getReturnValueZ() && SparBot.config() != null && SparBot.config().shieldStuns) {
			self.invulnerableTime = sparbot$invulnerableBefore;
			lastHurt = sparbot$lastHurtBefore;
			hurtTime = sparbot$hurtTimeBefore;
			hurtDuration = sparbot$hurtDurationBefore;
		}
	}
}
