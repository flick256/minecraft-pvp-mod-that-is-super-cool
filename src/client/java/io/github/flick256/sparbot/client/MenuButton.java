package io.github.flick256.sparbot.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Keyboard-accessible menu button: the game's own bevelled button tinted by role, or an oak board in the sidebar. */
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
		int x = getX(), y = getY(), w = getWidth(), h = getHeight();
		if (tone == Tone.NAV) {
			ForestTheme.navEntry(g, x, y, w, h, selected, hover);
		} else {
			ForestTheme.button(g, x, y, w, h, tone, active, selected, hover);
			if (isFocused()) { g.outline(x, y, w, h, ForestTheme.ACCENT); }
		}
		var font = Minecraft.getInstance().font;
		String message = getMessage().getString();
		boolean icon = tone == Tone.NAV && w >= 90 && h >= 18;
		String text = font.plainSubstrByWidth(message.equals("Up") ? "\u2191" : message.equals("Dn") ? "\u2193" : message, w - (icon ? 32 : 12));
		int color = !active ? 0xFFA0A0A0 : tone == Tone.NAV ? (selected ? ForestTheme.TEXT : ForestTheme.MUTED) : 0xFFFFFFFF;
		if (tone == Tone.NAV && hover > 0.5F && !selected) { color = ForestTheme.TEXT; }
		if (icon) { MenuArt.item(g, MenuArt.navigation(message), x + 5, y + (h - 16) / 2); }
		int textX = tone == Tone.NAV ? x + (icon ? 26 : 10) : x + (w - font.width(text)) / 2;
		g.text(font, text, textX, y + (h - 8) / 2, color, true);
	}
}
