package io.github.flick256.sparbot.bot;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

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
 *   <li>After each movement step it reports how far it moved ({@link #setKnownMovement}), as a client's
 *       movement packet does. Vanilla reads that for spear charges, sweep attacks and the momentum a
 *       thrown or shot projectile inherits.</li>
 *   <li>{@link #applyInput()} turns the held keys into movement exactly like the vanilla client's
 *       LocalPlayer#applyInput.</li>
 *   <li>A hit blocked with a shield pushes the blocker's server-side velocity (LivingEntity#blockedByItem
 *       and #dealDefaultKnockback) but isn't marked as a hurt, so vanilla never sends that push to a
 *       client and a human player doesn't feel it. The bot moves by its server-side velocity, so it
 *       skips those two pushes ({@link #blockUsingItem}, {@link #knockback}, {@link #dealDefaultKnockback}).</li>
 * </ul>
 * Nothing here touches health, damage, invulnerability or abilities.
 */
public final class BotPlayer extends ServerPlayer {
	private final Bot bot;
	/** Inside a shield block (see the class comment). */
	private boolean blockingHit;

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
			bot.beginKineticTick(this);
			try {
				doTick();
			} finally {
				bot.endKineticTick();
			}
			// ServerGamePacketListenerImpl#handlePlayerKnownMovement: what a client reports having moved.
			setKnownMovement(new Vec3(getX() - xo, getY() - yo, getZ() - zo));
			level().getChunkSource().move(this);
		}
	}

	@Override
	protected void blockUsingItem(ServerLevel level, LivingEntity attacker, DamageSource source, float damage) {
		blockingHit = true;
		try {
			super.blockUsingItem(level, attacker, source, damage);
		} finally {
			blockingHit = false;
		}
	}

	@Override
	public void knockback(double power, double xd, double zd, DamageSource source, float damage, boolean comesFromEffect) {
		if (blockingHit) {
			return; // LivingEntity#blockedByItem's push: never sent to a client
		}
		super.knockback(power, xd, zd, source, damage, comesFromEffect);
	}

	@Override
	public void dealDefaultKnockback(DamageSource source, float damage, boolean blocked) {
		if (blocked && damage <= 0.0F) {
			return; // a fully blocked hit isn't marked hurt, so a client never gets this push
		}
		super.dealDefaultKnockback(source, damage, blocked);
	}

	@Override
	protected void applyInput() {
		bot.client().applyMovementInput(this);
	}
}
