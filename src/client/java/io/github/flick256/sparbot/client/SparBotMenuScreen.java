package io.github.flick256.sparbot.client;

import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.ui.MenuCommands;
import io.github.flick256.sparbot.core.ui.MenuState;
import io.github.flick256.sparbot.menu.MenuRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The SparBot menu: spawn bots, manage the ones that exist, equip yourself with a kit, and change settings. Every button sends an
 * ordinary {@code /sparbot} command, so the server checks permissions and values exactly as if it had
 * been typed, and replies (in chat) the same way. After each action the menu asks for fresh contents.
 */
public final class SparBotMenuScreen extends Screen {
	private static final int MAX_ROWS = 7;
	private static final int ROW_HEIGHT = 24;
	private static final int TOP = 56;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREY = 0xFFA0A0A0;
	private static final int REFRESH_DELAY = 5;

	private enum Tab {
		BOTS,
		SPAWN,
		KITS,
		SETTINGS,
		/** One bot's techniques (opened from its row in the Bots tab). */
		TECHNIQUES
	}

	/** The tabs along the top, in order. */
	private static final Tab[] TAB_BAR = {Tab.BOTS, Tab.SPAWN, Tab.KITS, Tab.SETTINGS};

	private MenuState state;
	private Tab tab = Tab.BOTS;
	private int page;
	private String spawnName = "Bot1";
	private String spawnProfile;
	private String spawnKit;
	private String spawnStyle;
	/** The bot whose techniques the TECHNIQUES page shows. */
	private String techniquesBot;
	private final List<Label> labels = new ArrayList<>();
	/** Ticks until the menu asks for fresh contents after an action (0: not waiting). */
	private int refreshIn;

	private record Label(String text, int x, int y, int color) {
	}

	public SparBotMenuScreen(MenuState state) {
		super(Component.literal("SparBot"));
		this.state = state;
	}

	/** New contents from the server; keeps the tab and page. */
	public void update(MenuState newState) {
		this.state = newState;
		rebuildWidgets();
	}

	/** As many rows as fit between the tabs and the page buttons above Done. */
	private int rows() {
		return Math.max(2, Math.min(MAX_ROWS, (height - TOP - 56) / ROW_HEIGHT));
	}

	@Override
	protected void init() {
		labels.clear();
		int center = width / 2;
		String[] names = {"Bots", "Spawn", "Kits", "Settings"};
		for (int i = 0; i < TAB_BAR.length; i++) {
			Tab each = TAB_BAR[i];
			addRenderableWidget(Button.builder(Component.literal(names[i]), b -> show(each)).bounds(center - 158 + i * 80, 28, 76, 20).build()).active = tab != each;
		}
		switch (tab) {
			case BOTS -> initBots(center);
			case SPAWN -> initSpawn(center);
			case KITS -> initKits(center);
			case SETTINGS -> initSettings(center);
			case TECHNIQUES -> initTechniques(center);
		}
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(center - 50, height - 28, 100, 20).build());
	}

	private void initBots(int center) {
		List<MenuState.BotEntry> bots = state.bots();
		if (bots.isEmpty()) {
			labels.add(new Label("No bots yet: spawn one in the Spawn tab", center - 100, TOP + 6, GREY));
			return;
		}
		int first = clampPage(bots.size()) * rows();
		String me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getGameProfile().name();
		for (int i = first; i < Math.min(first + rows(), bots.size()); i++) {
			MenuState.BotEntry bot = bots.get(i);
			int y = TOP + (i - first) * ROW_HEIGHT;
			String status = bot.alive() ? String.format(Locale.ROOT, "%.1f hp", bot.health()) : "dead";
			labels.add(new Label(font.plainSubstrByWidth(bot.name() + "  " + bot.profile() + " / " + bot.style() + "  " + status, 196), center - 190, y + 6,
				bot.alive() ? WHITE : GREY));
			addRenderableWidget(Button.builder(Component.literal("Tech"), b -> {
				techniquesBot = bot.name();
				show(Tab.TECHNIQUES);
			}).bounds(center + 10, y, 40, 20).build());
			if (me != null && MenuCommands.validName(me)) {
				addRenderableWidget(Button.builder(Component.literal("Fight"), b -> run(MenuCommands.fight(bot.name(), me))).bounds(center + 54, y, 44, 20)
					.build()).active = bot.alive();
			}
			if (bot.alive()) {
				addRenderableWidget(Button.builder(Component.literal("Kill"), b -> run(MenuCommands.kill(bot.name()))).bounds(center + 102, y, 50, 20).build());
			} else {
				addRenderableWidget(Button.builder(Component.literal("Respawn"), b -> run(MenuCommands.respawn(bot.name()))).bounds(center + 102, y, 50, 20)
					.build());
			}
			addRenderableWidget(Button.builder(Component.literal("Remove"), b -> run(MenuCommands.remove(bot.name()))).bounds(center + 156, y, 48, 20).build());
		}
		pager(center, bots.size());
	}

	private void initSpawn(int center) {
		spawnProfile = pick(spawnProfile, state.profiles(), setting("defaultProfile"));
		spawnKit = pick(spawnKit, state.kits(), setting("defaultKit"));
		spawnStyle = pick(spawnStyle, state.styles(), setting("defaultPlaystyle"));
		int x = center - 100;
		labels.add(new Label("Name", x - 60, TOP + 6, WHITE));
		EditBox name = new EditBox(font, x, TOP, 200, 20, Component.literal("Name"));
		name.setMaxLength(16);
		name.setValue(spawnName);
		name.setResponder(value -> spawnName = value);
		addRenderableWidget(name);
		addRenderableWidget(cycle("Profile", state.profiles(), spawnProfile, v -> spawnProfile = v, x, TOP + ROW_HEIGHT));
		addRenderableWidget(cycle("Kit", state.kits(), spawnKit, v -> spawnKit = v, x, TOP + 2 * ROW_HEIGHT));
		addRenderableWidget(cycle("Style", state.styles(), spawnStyle, v -> spawnStyle = v, x, TOP + 3 * ROW_HEIGHT));
		addRenderableWidget(Button.builder(Component.literal("Spawn bot"), b -> {
			if (!MenuCommands.validName(spawnName)) {
				labels.add(new Label("Names are 1-16 letters, digits or _", x, TOP + 5 * ROW_HEIGHT + 6, 0xFFFF6060));
				return;
			}
			run(MenuCommands.spawn(spawnName, spawnProfile, spawnKit), MenuCommands.style(spawnName, spawnStyle));
			spawnName = nextName(spawnName);
		}).bounds(x, TOP + 4 * ROW_HEIGHT, 200, 20).build());
	}

	/** Every kit with an Equip button: it replaces your inventory with the kit, like a server's kit menu. */
	private void initKits(int center) {
		List<MenuState.KitEntry> kits = state.kitInfo();
		if (kits.isEmpty()) {
			labels.add(new Label("No kits loaded", center - 40, TOP + 6, GREY));
			return;
		}
		int first = clampPage(kits.size()) * rows();
		for (int i = first; i < Math.min(first + rows(), kits.size()); i++) {
			MenuState.KitEntry kit = kits.get(i);
			int y = TOP + (i - first) * ROW_HEIGHT;
			String name = kit.displayName();
			int nameWidth = 220;
			if (!kit.verified() && !name.toLowerCase(Locale.ROOT).contains("unverified")) {
				labels.add(new Label("unverified", center + 40, y + 6, GREY));
			}
			String shown = font.plainSubstrByWidth(name, nameWidth);
			labels.add(new Label(shown.length() < name.length() ? font.plainSubstrByWidth(name, nameWidth - font.width("...")) + "..." : name, center - 190, y + 6,
				WHITE));
			addRenderableWidget(Button.builder(Component.literal("Equip"), b -> run(MenuCommands.giveKit(kit.id()))).bounds(center + 110, y, 80, 20).build());
		}
		labels.add(new Label("Replaces your inventory", center + 70, TOP + rows() * ROW_HEIGHT + 10, GREY));
		pager(center, kits.size());
	}

	/** On/off switches for each of one bot's techniques. */
	private void initTechniques(int center) {
		MenuState.BotEntry bot = state.bots().stream().filter(b -> b.name().equals(techniquesBot)).findFirst().orElse(null);
		if (bot == null) {
			labels.add(new Label("That bot is gone", center - 40, TOP + 6, GREY));
			return;
		}
		Technique[] all = Technique.values();
		int first = clampPage(all.length) * rows();
		for (int i = first; i < Math.min(first + rows(), all.length); i++) {
			Technique technique = all[i];
			int y = TOP + (i - first) * ROW_HEIGHT;
			labels.add(new Label(bot.name() + ": " + technique.id(), center - 190, y + 6, WHITE));
			boolean on = !bot.disabledTechniques().contains(technique.id());
			addRenderableWidget(CycleButton.onOffBuilder(on).displayOnlyValue().create(center + 20, y, 120, 20, Component.literal(technique.id()),
				(button, value) -> run(MenuCommands.technique(bot.name(), technique.id(), value))));
		}
		pager(center, all.length);
	}

	private void initSettings(int center) {
		List<MenuState.Setting> settings = state.settings();
		int first = clampPage(settings.size()) * rows();
		for (int i = first; i < Math.min(first + rows(), settings.size()); i++) {
			MenuState.Setting setting = settings.get(i);
			int y = TOP + (i - first) * ROW_HEIGHT;
			labels.add(new Label(setting.key(), center - 190, y + 6, WHITE));
			if (setting.type().equals("boolean")) {
				addRenderableWidget(CycleButton.onOffBuilder(Boolean.parseBoolean(setting.value())).displayOnlyValue()
					.create(center + 20, y, 120, 20, Component.literal(setting.key()), (button, value) -> run(MenuCommands.setConfig(setting.key(), value.toString()))));
			} else {
				EditBox box = new EditBox(font, center + 20, y, 116, 20, Component.literal(setting.key()));
				box.setMaxLength(128);
				box.setValue(setting.value());
				addRenderableWidget(box);
				addRenderableWidget(Button.builder(Component.literal("Set"), b -> run(MenuCommands.setConfig(setting.key(), box.getValue())))
					.bounds(center + 140, y, 50, 20).build());
			}
		}
		pager(center, settings.size());
	}

	private void pager(int center, int items) {
		int pages = Math.max(1, (items + rows() - 1) / rows());
		if (pages <= 1) {
			return;
		}
		int y = TOP + rows() * ROW_HEIGHT + 4;
		addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1)).bounds(center - 60, y, 20, 20).build()).active = page > 0;
		labels.add(new Label((page + 1) + " / " + pages, center - 14, y + 6, GREY));
		addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1)).bounds(center + 40, y, 20, 20).build()).active = page < pages - 1;
	}

	private CycleButton<String> cycle(String name, List<String> values, String initial, java.util.function.Consumer<String> onChange, int x, int y) {
		List<String> options = values.isEmpty() ? List.of("-") : values;
		return CycleButton.builder(Component::literal, options.contains(initial) ? initial : options.get(0)).withValues(options)
			.create(x, y, 200, 20, Component.literal(name), (button, value) -> onChange.accept(value));
	}

	private void show(Tab next) {
		tab = next;
		page = 0;
		rebuildWidgets();
	}

	private void turn(int delta) {
		page += delta;
		rebuildWidgets();
	}

	private int clampPage(int items) {
		int pages = Math.max(1, (items + rows() - 1) / rows());
		page = Math.max(0, Math.min(page, pages - 1));
		return page;
	}

	/**
	 * Sends commands in order, then asks for fresh contents a few ticks later: the client sends commands
	 * through its chat pipeline, so a request sent at once could overtake them.
	 */
	private void run(String... commands) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) {
			return;
		}
		for (String command : commands) {
			minecraft.player.connection.sendCommand(command);
		}
		refreshIn = REFRESH_DELAY;
	}

	@Override
	public void tick() {
		super.tick();
		if (refreshIn > 0 && --refreshIn == 0 && ClientPlayNetworking.canSend(MenuRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new MenuRequestPayload(false));
		}
	}

	private String setting(String key) {
		return state.settings().stream().filter(s -> s.key().equals(key)).map(MenuState.Setting::value).findFirst().orElse("");
	}

	private static String pick(String current, List<String> options, String fallback) {
		if (current != null && options.contains(current)) {
			return current;
		}
		if (options.contains(fallback)) {
			return fallback;
		}
		return options.isEmpty() ? "-" : options.get(0);
	}

	/** Bot1 -> Bot2, Steve -> Steve2. */
	static String nextName(String name) {
		int i = name.length();
		while (i > 0 && Character.isDigit(name.charAt(i - 1))) {
			i--;
		}
		String base = name.substring(0, i);
		int n = i == name.length() ? 1 : Integer.parseInt(name.substring(i));
		String next = base + (n + 1);
		return next.length() <= 16 ? next : name;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractRenderState(graphics, mouseX, mouseY, a);
		graphics.centeredText(font, title, width / 2, 10, WHITE);
		for (Label label : labels) {
			graphics.text(font, label.text(), label.x(), label.y(), label.color());
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
