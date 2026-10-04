package io.github.flick256.sparbot.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the menu's contents as JSON ({@link io.github.flick256.sparbot.core.ui.MenuState}). */
public record MenuStatePayload(String json) implements CustomPacketPayload {
	public static final Type<MenuStatePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sparbot", "menu_state"));
	public static final StreamCodec<ByteBuf, MenuStatePayload> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(262144), MenuStatePayload::json,
		MenuStatePayload::new);

	@Override
	public Type<MenuStatePayload> type() {
		return TYPE;
	}
}
