package io.github.flick256.sparbot.kit.editor;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Bounded messages used only by the in-game kit editor. */
public final class KitEditorPayloads {
	private KitEditorPayloads() {}

	public record Open(String kitJson, String status) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sparbot", "kit_editor_open"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(65536), Open::kitJson, ByteBufCodecs.stringUtf8(1024), Open::status, Open::new);
		@Override public Type<Open> type() { return TYPE; }
	}

	public record Save(String kitJson) implements CustomPacketPayload {
		public static final Type<Save> TYPE = new Type<>(Identifier.fromNamespaceAndPath("sparbot", "kit_editor_save"));
		public static final StreamCodec<ByteBuf, Save> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(65536), Save::kitJson, Save::new);
		@Override public Type<Save> type() { return TYPE; }
	}
}
