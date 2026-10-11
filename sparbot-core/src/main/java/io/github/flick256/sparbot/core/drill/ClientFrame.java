package io.github.flick256.sparbot.core.drill;

/**
 * One client tick of a real player, as the server hears it: the keys held that tick (the client sends its
 * key state since 1.21.2), whether it was sprinting, and where it ended the tick. Frames are numbered by the
 * client's own ticks (one {@code ServerboundClientTickEndPacket} each), so timing read from them is exact to
 * the tick whatever the ping.
 */
public record ClientFrame(int tick, boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sprintKey,
	boolean sprinting, double x, double y, double z, boolean onGround) {
}
