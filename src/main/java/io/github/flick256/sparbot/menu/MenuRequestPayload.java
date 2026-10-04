package io.github.flick256.sparbot.menu;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "send me the menu's contents again" (after a change, or when it opens by key). */
public record MenuRequestPayload(boolean open) implements CustomPacketPayload {
	public static final Type<MenuRequestPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sparbot", "menu_request"));
	public static final StreamCodec<ByteBuf, MenuRequestPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL,
		MenuRequestPayload::open, MenuRequestPayload::new);

	@Override
	public Type<MenuRequestPayload> type() {
		return TYPE;
	}
}
