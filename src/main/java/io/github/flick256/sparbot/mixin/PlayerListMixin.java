package io.github.flick256.sparbot.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import io.github.flick256.sparbot.bot.BotPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla's respawn builds a brand-new {@code ServerPlayer} for the respawned player. For a bot it
 * must be a new {@link BotPlayer} attached to the same bot, otherwise the respawned body would have no
 * brain. Nothing else about respawning changes.
 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
	@WrapOperation(method = "respawn", at = @At(value = "NEW", target = "net/minecraft/server/level/ServerPlayer"))
	private ServerPlayer sparbot$respawnBotBody(
		MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info, Operation<ServerPlayer> original,
		@Local(argsOnly = true) ServerPlayer previous
	) {
		if (previous instanceof BotPlayer bot) {
			return new BotPlayer(server, level, profile, info, bot.bot());
		}
		return original.call(server, level, profile, info);
	}
}
