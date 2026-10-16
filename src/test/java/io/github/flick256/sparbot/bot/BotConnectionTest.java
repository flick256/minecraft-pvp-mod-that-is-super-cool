package io.github.flick256.sparbot.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class BotConnectionTest {
	@Test
	@Timeout(5)
	void disconnectDeliveryCallbackClosesTheChannelSynchronously() {
		BotConnection connection = new BotConnection();
		AtomicBoolean delivered = new AtomicBoolean();
		try {
			assertTrue(connection.isConnected());
			connection.bindTo(7);
			connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(7,
				new net.minecraft.world.phys.Vec3(0.2, 0.4, 0)), null, true);
			Component reason = Component.literal("shutdown test");
			connection.send(new ClientboundDisconnectPacket(reason), future -> {
				assertTrue(future.isSuccess());
				delivered.set(true);
				connection.disconnect(reason);
			}, true);
			assertTrue(delivered.get(), "disconnect completion must not wait for another game tick");
			assertFalse(connection.isConnected());
			connection.disconnect(reason); // shutdown/removal can reach the same connection twice
			assertEquals(-1, connection.takePendingTeleport());
			assertNull(connection.takePendingMotion());
		} finally {
			connection.disconnect(Component.literal("test cleanup"));
		}
	}
}
