package io.github.flick256.sparbot.bot;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The bot's "network connection". There is no socket: an in-memory {@link EmbeddedChannel} makes
 * vanilla treat the connection as open (exactly like vanilla's own GameTest mock players), and
 * outgoing packets are dropped because nobody is on the other end.
 *
 * <p>A real client reacts to a few packets about its own player, and skipping them would give the bot
 * an unfair advantage. Most importantly, when a player is hit vanilla sends the knockback to the
 * victim's client in a {@link ClientboundSetEntityMotionPacket} and then <b>resets the server-side
 * velocity</b> (Player#causeExtraKnockback). Ignoring that packet would make the bot immune to
 * knockback, so it is queued and applied on the bot's next tick, the way a client applies it on its next
 * frame. Explosion knockback is different: vanilla already pushes the player's server-side velocity
 * (ServerExplosion#hurtEntities) and the client copy in ClientboundExplodePacket must not be applied
 * a second time, so it is ignored here.
 */
public final class BotConnection extends Connection {
	private final EmbeddedChannel channel;
	private int selfEntityId = -1;
	private @Nullable Vec3 pendingMotion;

	public BotConnection() {
		super(PacketFlow.SERVERBOUND);
		// Registers this connection as the channel's handler; channelActive() stores the channel.
		this.channel = new EmbeddedChannel(this);
	}

	void bindTo(int entityId) {
		this.selfEntityId = entityId;
	}

	/** Returns and clears knockback received since the last tick, or null. */
	@Nullable Vec3 takePendingMotion() {
		Vec3 motion = pendingMotion;
		pendingMotion = null;
		return motion;
	}

	@Override
	public void send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush) {
		if (packet instanceof ClientboundSetEntityMotionPacket motion && motion.id() == selfEntityId) {
			pendingMotion = motion.movement();
		} else if (packet instanceof ClientboundPlayerPositionPacket) {
			// The teleport already set position and velocity server-side and supersedes earlier motion.
			pendingMotion = null;
		}
		// Everything else would go to the client's screen; there is no screen, so drop it, but report
		// it as delivered: vanilla chains follow-up work on delivery (e.g. disconnecting only after the
		// disconnect packet is sent, ServerCommonPacketListenerImpl#disconnect).
		if (listener != null) {
			try {
				listener.operationComplete(channel.newSucceededFuture());
			} catch (Exception e) {
				throw new IllegalStateException("Packet send listener failed", e);
			}
		}
	}

	@Override
	public void flushChannel() {
	}

	/** Releases anything the embedded pipeline buffered (protocol switch messages). */
	void drain() {
		channel.releaseOutbound();
		channel.releaseInbound();
	}
}
