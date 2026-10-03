package io.github.flick256.sparbot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.flick256.sparbot.match.MatchRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * UHC rules for match fighters only: FoodData#tick reads the server-wide natural_health_regeneration
 * game rule, and for a fighter in a match without natural regeneration that read returns false. Nothing
 * else about hunger, saturation or healing changes, and it applies to humans and bots alike.
 */
@Mixin(FoodData.class)
abstract class FoodDataMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/gamerules/GameRules;get(Lnet/minecraft/world/level/gamerules/GameRule;)Ljava/lang/Object;"))
	private Object sparbot$matchRegeneration(GameRules rules, GameRule<?> rule, Operation<Object> original, @Local(argsOnly = true) ServerPlayer player) {
		Object value = original.call(rules, rule);
		if (rule == GameRules.NATURAL_HEALTH_REGENERATION && !MatchRules.regenerates(player)) {
			return Boolean.FALSE;
		}
		return value;
	}
}
