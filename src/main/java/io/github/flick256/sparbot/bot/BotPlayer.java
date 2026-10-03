package io.github.flick256.sparbot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * The bot's body: an ordinary {@link ServerPlayer} with the full vanilla player rules (inventory,
 * hunger, effects, armor, totems, attack cooldown, damage pipeline). It overrides only what is
 * needed because no game client is attached:
 * <ul>
 *   <li>{@link #isClientAuthoritative()} is false. For human players vanilla trusts the client to
 *       simulate movement and to report fall damage (Entity#move only checks fall damage for the
 *       authoritative side). With no client, the server must be authoritative, otherwise the bot would
 *       never take fall damage. Vanilla's own GameTest mock players do the same.</li>
 *   <li>{@link #tick()} also runs the per-tick movement/physics step ({@code doTick}), which vanilla
 *       only runs when a client's packets are processed, and lets the {@link Bot} brain press keys first.</li>
 *   <li>{@link #applyInput()} turns the held keys into movement exactly like the vanilla client's
 *       LocalPlayer#applyInput.</li>
 * </ul>
 * Nothing here touches health, damage, invulnerability or abilities.
 */
public final class BotPlayer extends ServerPlayer {
	private final Bot bot;

	public BotPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info, Bot bot) {
		super(server, level, profile, info);
		this.bot = bot;
		bot.attachBody(this);
	}

	public Bot bot() {
		return bot;
	}

	@Override
	public boolean isClientAuthoritative() {
		return false;
	}

	@Override
	public void tick() {
		bot.beforeTick(this);
		super.tick();
		if (!isRemoved()) {
			// Mirrors ServerGamePacketListenerImpl#tickPlayer, which never runs for this connection.
			xo = getX();
			yo = getY();
			zo = getZ();
			doTick();
			level().getChunkSource().move(this);
		}
	}

	@Override
	protected void applyInput() {
		bot.client().applyMovementInput(this);
	}
}
