package io.github.flick256.sparbot.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Style;

/** Small revision cards on real world-loading screens. This never adds a loading delay. */
final class StudyLoadingTips {
	private StudyLoadingTips() { }
	static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof LevelLoadingScreen) || !StudyClient.enabled()) return;
			String tip = StudyClient.loadingTip();
			ScreenEvents.afterExtract(screen).register((active, graphics, mouseX, mouseY, delta) -> {
				int cardWidth = Math.min(520, active.width - 32);
				var lines = client.font.getSplitter().splitLines(tip, cardWidth - 24, Style.EMPTY);
				int lineCount = Math.min(3, lines.size());
				int cardHeight = 28 + lineCount * 12;
				int x = (active.width - cardWidth) / 2, y = active.height - cardHeight - 12;
				graphics.fillGradient(x, y, x + cardWidth, y + cardHeight, 0xF020313F, 0xF0111A28);
				graphics.outline(x, y, cardWidth, cardHeight, 0xFF53747E);
				graphics.text(client.font, "REVISION TIP", x + 12, y + 8, 0xFF73DDCA, false);
				for (int i = 0; i < lineCount; i++) graphics.text(client.font, lines.get(i).getString(), x + 12, y + 24 + i * 12, 0xFFE7EEF6, false);
			});
		});
	}
}
