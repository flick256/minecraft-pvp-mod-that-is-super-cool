package io.github.flick256.sparbot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Keyboard-accessible menu button with a quiet hover transition and clear selected/focus states. */
final class MenuButton extends Button {
	enum Tone { NORMAL, PRIMARY, GOLD, DANGER, NAV }
	private final Tone tone;
	private final boolean selected;
	private float hover;
	private long lastFrame;

	MenuButton(String text, int x, int y, int width, int height, Tone tone, boolean selected, OnPress action) {
		super(x, y, width, height, Component.literal(text), action, DEFAULT_NARRATION);
		this.tone = tone;
		this.selected = selected;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		long now = System.nanoTime() / 1_000_000;
		float step = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 90.0F);
		lastFrame = now;
		hover += ((isHoveredOrFocused() && active ? 1 : 0) - hover) * step;
		int accent = tone == Tone.GOLD ? 0xFFE8BC73 : tone == Tone.DANGER ? 0xFFEE8A89 : 0xFF73DDCA;
		int base = tone == Tone.PRIMARY ? 0xFF20594F : tone == Tone.GOLD ? 0xFF53432C : selected ? 0xFF263F43 : 0xFF222D3C;
		if (tone == Tone.NAV && !selected) { base = 0xFF171E2B; }
		int fill = mix(base, tone == Tone.DANGER ? 0xFF553039 : 0xFF35495B, hover);
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		g.fill(x + 1, y, x + w - 1, y + h, fill);
		g.fill(x, y + 1, x + w, y + h - 1, fill);
		if (selected || isFocused()) {
			g.outline(x, y, w, h, accent);
		} else {
			g.horizontalLine(x + 1, x + w - 2, y + h - 1, mix(0xFF344254, accent, hover * 0.5F));
		}
		if (tone == Tone.NAV && selected) { g.fill(x, y + 4, x + 2, y + h - 4, accent); }
		var font = Minecraft.getInstance().font;
		String message = getMessage().getString();
		String text = font.plainSubstrByWidth(message.equals("Up") ? "↑" : message.equals("Dn") ? "↓" : message, w - 12);
		int color = !active ? 0xFF738092 : selected || tone == Tone.PRIMARY || tone == Tone.GOLD ? 0xFFF0F8F5 : 0xFFCAD6E3;
		if (tone == Tone.DANGER && active) { color = accent; }
		int textX = tone == Tone.NAV ? x + 10 : x + (w - font.width(text)) / 2;
		g.text(font, text, textX, y + (h - 8) / 2, color, false);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int blue = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return 0xFF000000 | r << 16 | g << 8 | blue;
	}
}
