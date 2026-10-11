package io.github.flick256.sparbot.mixin;

import io.github.flick256.sparbot.record.HumanInputs;
import io.github.flick256.sparbot.drill.ClientTicks;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets recordings see a human's left clicks (a client sends an arm swing for every left click, hit or miss),
 * and the drills a human's keys and client ticks ({@link ClientTicks}). Observes only, on the server thread
 * (after the packet was moved there).
 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
	@Shadow
	public ServerPlayer player;

	@Inject(method = "handleAnimate", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;swing(Lnet/minecraft/world/InteractionHand;)V"))
	private void sparbot$noteSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
		HumanInputs.noteSwing(this.player);
		io.github.flick256.sparbot.SparBot.drills().onSwing(this.player);
	}

	@Inject(method = "handlePlayerInput", at = @At("TAIL"))
	private void sparbot$noteInput(ServerboundPlayerInputPacket packet, CallbackInfo ci) {
		ClientTicks.input(this.player, packet.input());
	}

	@Inject(method = "handleClientTickEnd", at = @At("TAIL"))
	private void sparbot$noteTickEnd(ServerboundClientTickEndPacket packet, CallbackInfo ci) {
		ClientTicks.tickEnd(this.player);
	}
}
