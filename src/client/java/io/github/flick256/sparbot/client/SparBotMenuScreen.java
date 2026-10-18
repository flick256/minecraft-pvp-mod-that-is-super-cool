package io.github.flick256.sparbot.client;

import io.github.flick256.sparbot.core.brain.Technique;
import io.github.flick256.sparbot.core.drill.Drill;
import io.github.flick256.sparbot.core.drill.Drills;
import io.github.flick256.sparbot.core.ui.MenuCommands;
import io.github.flick256.sparbot.core.ui.MenuState;
import io.github.flick256.sparbot.menu.MenuRequestPayload;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A scrollable practice workspace. Actions use the same server permission checks as chat commands. */
public final class SparBotMenuScreen extends Screen {
	private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9BAEC3, ACCENT = 0xFF73DDCA, GOLD = 0xFFE8BC73;
	private enum Tab {
		HOME("Overview", "Your next session", "Choose a fight, build a bot, or sharpen a skill."),
		PRACTICE("Practice", "Make every fight count", "Choose a mode and an opponent, then step into the arena."),
		BOTS("Bots", "Your sparring partners", "Fight, customise or manage the bots in this world."),
		SPAWN("Spawn", "Build your opponent", "A name, a skill level and a kit. Make the fight yours."),
		KITS("Kits", "Ready for the next round", "Equip a kit or save the arrangement that works for you."),
		DRILLS("Drills", "One skill at a time", "Short practice, instant feedback, and a medal to work towards."),
		YOU("You", "Your training journal", "Recover, review your last fight, and see what to improve."),
		SETTINGS("Settings", "Your practice, your rules", "Changes apply to this server and require permission."),
		TECHNIQUES("Techniques", "Tune your opponent", "Choose the techniques this bot can use.");
		final String label, heading, description;
		Tab(String label, String heading, String description) { this.label = label; this.heading = heading; this.description = description; }
	}

	private record Label(String text, int x, int y, int color) { }
	private record Card(int y, int height, boolean featured) { }
	private record Illustration(int y, boolean uhc) { }
	private final List<Label> labels = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();
	private final List<Illustration> illustrations = new ArrayList<>();
	private final Map<Tab, Integer> positions = new EnumMap<>(Tab.class);
	private final Map<String, String> settingDrafts = new HashMap<>();
	private final Map<String, EditBox> fields = new HashMap<>();
	private final Map<String, EditBox> previousFields = new HashMap<>();
	private final String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("sparbot")
		.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
	private MenuState state;
	private Tab tab = Tab.HOME;
	private String spawnName = "Bot1", spawnProfile, spawnKit, spawnStyle;
	private String practiceProfile, practiceMode, techniquesBot;
	private String kitSearch = "", settingsSearch = "", status = "Ready";
	private boolean choosingKit, rebuildNextTick;
	private int scroll, totalHeight, cursorY, refreshIn, ticks;
	private int left, top, panelWidth, panelHeight, sidebar, contentX, contentWidth, contentTop, contentBottom;

	public SparBotMenuScreen(MenuState state) {
		super(Component.literal("SparBot"));
		this.state = state;
	}

	/** Preserve the form, focus, scroll position and selection when a server snapshot arrives. */
	public void update(MenuState newState) {
		state = newState;
		status = "Ready";
		if (tab == Tab.TECHNIQUES && state.bots().stream().noneMatch(b -> b.name().equals(techniquesBot))) {
			show(Tab.BOTS);
		} else { rebuildKeepingFocus(); }
	}

	private void rebuildKeepingFocus() {
		String focused = fields.entrySet().stream().filter(e -> e.getValue() == getFocused()).map(Map.Entry::getKey).findFirst().orElse(null);
		EditBox previous = focused == null ? null : fields.get(focused);
		int caret = previous == null ? 0 : previous.getCursorPosition();
		rebuildWidgets();
		if (focused != null && fields.containsKey(focused) && fields.get(focused).visible) {
			EditBox box = fields.get(focused);
			setFocused(box);
			if (box != previous) { box.setCursorPosition(Math.min(caret, box.getValue().length())); }
		}
	}

	@Override
	protected void init() {
		labels.clear(); cards.clear(); illustrations.clear();
		previousFields.clear(); previousFields.putAll(fields); fields.clear();
		panelWidth = Math.min(760, width - 16);
		panelHeight = Math.min(480, height - 16);
		left = (width - panelWidth) / 2; top = (height - panelHeight) / 2;
		sidebar = width < 560 ? 82 : 112;
		contentX = left + sidebar + 14;
		contentWidth = panelWidth - sidebar - 28;
		contentTop = top + 62; contentBottom = top + panelHeight - 36;
		int navY = top + 44;
		for (Tab each : Tab.values()) {
			if (each == Tab.TECHNIQUES) { continue; }
			MenuButton button = new MenuButton(each.label, left + 8, navY, sidebar - 16, 20, MenuButton.Tone.NAV,
				tab == each || each == Tab.BOTS && tab == Tab.TECHNIQUES, b -> show(each));
			addRenderableWidget(button);
			navY += 22;
		}
		cursorY = contentTop - scroll;
		switch (tab) {
			case HOME -> overview();
			case PRACTICE -> practice();
			case BOTS -> bots();
			case SPAWN -> spawn();
			case KITS -> kits();
			case DRILLS -> drills();
			case YOU -> you();
			case SETTINGS -> settings();
			case TECHNIQUES -> techniques();
		}
		totalHeight = cursorY - (contentTop - scroll);
		int max = maxScroll();
		if (scroll > max) { scroll = max; rebuildWidgets(); return; }
		int y = top + panelHeight - 27;
		boolean actionPage = tab == Tab.PRACTICE || tab == Tab.SPAWN;
		int actionWidth = actionPage ? 110 : 50;
		String action = tab == Tab.PRACTICE ? "Enter arena" : tab == Tab.SPAWN ? "Spawn bot" : "Done";
		MenuButton primary = addRenderableWidget(new MenuButton(action, contentX + contentWidth - actionWidth, y, actionWidth, 20,
			tab == Tab.PRACTICE && "swight".equals(practiceProfile) ? MenuButton.Tone.GOLD : MenuButton.Tone.PRIMARY, false,
			b -> { if (tab == Tab.PRACTICE) { runAndClose(MenuCommands.practiceFight(practiceMode, practiceProfile)); }
				else if (tab == Tab.SPAWN) { spawnBot(); } else { onClose(); } }));
		if (tab == Tab.SPAWN) { primary.active = validSpawn(); }
		if (tab == Tab.PRACTICE) { primary.active = !state.modes().isEmpty() && !state.profiles().isEmpty(); }
		int edge = contentX + contentWidth - actionWidth - 6;
		if (contentWidth >= actionWidth + 112) {
			addRenderableWidget(new MenuButton("Refresh", edge - 56, y, 56, 20, MenuButton.Tone.NORMAL, false, b -> requestRefresh()));
			edge -= 62;
		}
		if (max > 0) {
			addRenderableWidget(new MenuButton("Up", edge - 47, y, 22, 20, MenuButton.Tone.NORMAL, false, b -> moveScroll(-72))).active = scroll > 0;
			addRenderableWidget(new MenuButton("Dn", edge - 22, y, 22, 20, MenuButton.Tone.NORMAL, false, b -> moveScroll(72))).active = scroll < max;
		}

	}

	private void overview() {
		int y = card(94, false);
		illustrate(y + 58, false);
		text("A better fight starts here.", 12, y + 12, TEXT);
		wrap("Head into practice with a complete kit, or build your own sparring partner.", 12, y + 28, contentWidth - 24, MUTED);
		button("Start practice", 12, y + 64, Math.min(150, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> show(Tab.PRACTICE));
		if (state.profiles().contains("swight")) {
			y = card(112, true);
			illustrate(y + 78, true);
			text("SWIGHT  /  UHC SPECIALIST", 12, y + 12, GOLD);
			wrap("Utility with a plan. A demanding UHC opponent with human limits, built to challenge Demon.", 12, y + 29, contentWidth - 24, MUTED);
			button("Challenge Swight", 12, y + 82, Math.min(160, contentWidth - 24), MenuButton.Tone.GOLD,
				() -> runAndClose(MenuCommands.practiceFight(uhcMode(), "swight")));
		}
		y = card(74, false);
		text(state.bots().size() + " sparring partners", 12, y + 12, TEXT);
		text("Create one, then fine-tune its techniques.", 12, y + 28, MUTED);
		button("Manage bots", 12, y + 48, Math.min(120, contentWidth - 24), MenuButton.Tone.NORMAL, () -> show(Tab.BOTS));
		y = card(76, false);
		long golds = state.myMedals().values().stream().filter(v -> v >= 3).count();
		text(golds + " / " + Drills.ALL.size() + " gold medals", 12, y + 12, GOLD);
		button("Find a drill", 12, y + 47, Math.min(120, contentWidth - 24), MenuButton.Tone.NORMAL, () -> show(Tab.DRILLS));
	}

	private void practice() {
		practiceProfile = pick(practiceProfile, state.profiles(), setting("defaultProfile"));
		practiceMode = pick(practiceMode, practiceModes(), uhcMode());
		section("01  CHOOSE YOUR MODE");
		choices(practiceModes(), practiceMode, value -> { practiceMode = value; rebuildKeepingFocus(); });
		section("02  CHOOSE YOUR OPPONENT");
		choices(profiles(), practiceProfile, value -> { practiceProfile = value; if (value.equals("swight")) { practiceMode = uhcMode(); } rebuildKeepingFocus(); });
		boolean swight = "swight".equals(practiceProfile);
		String intro = "Swight specialises in UHC. Deliberate utility, finite resources and room to punish mistakes.";
		String rules = "Shield timing matters: raise early and face the hit. Axes disable a block; lava and fire still hurt.";
		int rulesY = 41 + font.getSplitter().splitLines(intro, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size() * 12;
		int artY = rulesY + font.getSplitter().splitLines(rules, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size() * 12 + 12;
		int y = card(swight ? artY + (contentWidth >= 350 ? 34 : 2) : 76, swight);
		illustrate(y + (swight ? artY : 42), swight);
		text(pretty(practiceMode) + "  /  " + pretty(practiceProfile), 12, y + 12, swight ? GOLD : TEXT);
		if (swight) {
			wrap(intro, 12, y + 29, contentWidth - 24, MUTED);
			wrap(rules, 12, y + rulesY, contentWidth - 24, MUTED);
		}

		section("EXPLORE THE PRACTICE WORLD");
		flowButtons(List.of("Hub", "Colosseum", "Leave"), List.of(() -> runAndClose(MenuCommands.practiceHub()),
			() -> runAndClose(MenuCommands.practiceColosseum()), () -> runAndClose(MenuCommands.practiceLeave())));
		section("GRAND BOWL");
		flowButtons(List.of("Waves", "Hill", "Stop game"), List.of(() -> runAndClose(MenuCommands.colosseumWaves()),
			() -> runAndClose(MenuCommands.colosseumHill(practiceProfile)), () -> run(MenuCommands.colosseumStop())));
	}

	private void bots() {
		if (state.bots().isEmpty()) {
			int y = card(96, false);
			text("Your first opponent awaits.", 12, y + 12, TEXT);
			wrap("Create a bot, choose its kit, then start a fight here.", 12, y + 30, contentWidth - 24, MUTED);
			button("Create a bot", 12, y + 66, Math.min(140, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> show(Tab.SPAWN));
			return;
		}
		String me = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getGameProfile().name();
		for (MenuState.BotEntry bot : state.bots()) {
			int y = card(74, "swight".equals(bot.profile()));
			text(bot.name(), 12, y + 10, TEXT);
			String hp = bot.alive() ? String.format(Locale.ROOT, "%.1f hp", bot.health()) : "Defeated";
			text(pretty(bot.profile()) + " / " + hp, 12, y + 26, bot.alive() ? MUTED : 0xFFEE8A89);
			int bw = Math.min(82, (contentWidth - 36) / 3);
			button(bot.alive() ? "Fight" : "Respawn", 12, y + 47, bw, MenuButton.Tone.PRIMARY,
				() -> { if (bot.alive() && me != null) { runAndClose(MenuCommands.fight(bot.name(), me)); } else if (!bot.alive()) { run(MenuCommands.respawn(bot.name())); } }).active = !bot.alive() || me != null && MenuCommands.validName(me);
			button("Tech", 18 + bw, y + 47, bw, MenuButton.Tone.NORMAL, () -> { techniquesBot = bot.name(); show(Tab.TECHNIQUES); });
			button("Remove", 24 + bw * 2, y + 47, bw, MenuButton.Tone.DANGER, () -> run(MenuCommands.remove(bot.name())));
		}
	}

	private void spawn() {
		spawnProfile = pick(spawnProfile, state.profiles(), setting("defaultProfile"));
		spawnKit = pick(spawnKit, state.kits(), setting("defaultKit"));
		spawnStyle = pick(spawnStyle, state.styles(), setting("defaultPlaystyle"));
		section("NAME YOUR OPPONENT");
		field("spawnName", spawnName, "Name", 0, cursorY, contentWidth, 16, v -> { spawnName = v; rebuildNextTick = true; });
		cursorY += 34;
		if (!MenuCommands.validName(spawnName) || state.bots().stream().anyMatch(b -> b.name().equalsIgnoreCase(spawnName))) {
			String error = MenuCommands.validName(spawnName) ? "That name is already in use." : "Use 1-16 letters, numbers or underscores.";
			wrap(error, 0, cursorY, contentWidth, 0xFFEE8A89);
			cursorY += 28;
		}
		section("SKILL LEVEL");
		choices(profiles(), spawnProfile, v -> {
			spawnProfile = v;
			if ("swight".equals(v)) {
				if (spawnName.matches("Bot[0-9]+")) { spawnName = "Swight"; }
				spawnKit = pick(null, state.kits(), state.kits().contains("pvphq_uhc") ? "pvphq_uhc" : "sparbot_uhc");
			}
			rebuildKeepingFocus();
		});
		section("LOADOUT");
		int y = card(70, false);
		String kitName = state.kitInfo().stream().filter(k -> k.id().equals(spawnKit)).map(MenuState.KitEntry::displayName).findFirst().orElse(spawnKit);
		text(kitName, 12, y + 12, TEXT);
		button("Choose kit", 12, y + 42, Math.min(140, contentWidth - 24), MenuButton.Tone.NORMAL, () -> { choosingKit = true; show(Tab.KITS); });
		section("PLAYSTYLE");
		choices(state.styles(), spawnStyle, v -> { spawnStyle = v; rebuildKeepingFocus(); });
		wrap(validSpawn() ? "Ready when you are." : "Use a unique name: 1-16 letters, numbers or underscores.", 0, cursorY, contentWidth, validSpawn() ? MUTED : 0xFFEE8A89);
		cursorY += 32;
	}

	private boolean validSpawn() {
		return MenuCommands.validName(spawnName) && !state.profiles().isEmpty() && !state.kits().isEmpty()
			&& state.bots().stream().noneMatch(b -> b.name().equalsIgnoreCase(spawnName));
	}

	private void spawnBot() {
		if (!validSpawn()) { return; }
		run(MenuCommands.spawn(spawnName, spawnProfile, spawnKit), MenuCommands.style(spawnName, spawnStyle));
		spawnName = nextName(spawnName);
		show(Tab.BOTS);
	}

	private List<String> profiles() {
		List<String> result = new ArrayList<>();
		for (String id : io.github.flick256.sparbot.core.profile.SkillProfiles.PRESET_IDS) { if (state.profiles().contains(id)) { result.add(id); } }
		for (String id : state.profiles()) { if (!result.contains(id)) { result.add(id); } }
		return result;
	}

	private List<String> practiceModes() {
		List<String> result = new ArrayList<>();
		for (String id : List.of("uhc_duel", "sword_pvphq", "sword_duel", "cart_low", "cart_high", "crystal_pvphq", "mace_pvphq")) {
			if (state.modes().contains(id)) { result.add(id); }
		}
		for (String id : state.modes()) { if (!result.contains(id)) { result.add(id); } }
		return result;
	}

	private String uhcMode() { return state.modes().contains("uhc_duel") ? "uhc_duel" : pick(null, state.modes(), "uhc"); }

	private void kits() {
		if (choosingKit) {
			button("Back to spawn", 0, cursorY, Math.min(140, contentWidth), MenuButton.Tone.NORMAL, () -> { choosingKit = false; show(Tab.SPAWN); });
			cursorY += 32;
		}
		field("kitSearch", kitSearch, "Search kits", 0, cursorY, contentWidth, 64, v -> { kitSearch = v; scroll = 0; rebuildNextTick = true; });
		cursorY += 34;
		List<MenuState.KitEntry> kits = state.kitInfo().stream().filter(k -> matches(kitSearch, k.id() + " " + k.displayName())).toList();
		if (kits.isEmpty()) { empty("No matching kits", "Try a shorter name or clear the search."); }
		for (MenuState.KitEntry kit : kits) {
			int y = card(78, choosingKit && kit.id().equals(spawnKit));
			text(kit.displayName(), 12, y + 10, TEXT);
			text(kit.verified() ? "Verified layout" : "Layout details unverified", 12, y + 27, kit.verified() ? ACCENT : MUTED);
			if (choosingKit) {
				button("Use kit", 12, y + 50, Math.min(130, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> { spawnKit = kit.id(); choosingKit = false; show(Tab.SPAWN); });
			} else {
				int bw = Math.min(88, (contentWidth - 36) / 3);
				boolean saved = state.myLayouts().contains(kit.id());
				button("Equip", 12, y + 50, bw, MenuButton.Tone.PRIMARY, () -> run(MenuCommands.giveKit(kit.id())));
				button(saved ? "Save layout*" : "Save layout", 18 + bw, y + 50, bw, MenuButton.Tone.NORMAL, () -> run(MenuCommands.saveLayout(kit.id())))
					.setTooltip(Tooltip.create(Component.literal("Save your current inventory arrangement for this kit.")));
				button("Reset", 24 + bw * 2, y + 50, bw, MenuButton.Tone.NORMAL, () -> run(MenuCommands.resetLayout(kit.id()))).active = saved;
			}
		}
	}

	private void drills() {
		flowButtons(List.of("Training hall", "Stop drill"), List.of(() -> runAndClose(MenuCommands.practiceHall()), () -> run(MenuCommands.drillStop())));
		for (Drill drill : Drills.ALL) {
			int medal = state.myMedals().getOrDefault(drill.id(), 0);
			int y = card(76, medal >= 3);
			text(drill.name(), 12, y + 10, TEXT);
			text(drill.discipline() + " / " + (medal == 0 ? "Not completed" : Drill.medal(medal)), 12, y + 27, medal >= 3 ? GOLD : MUTED);
			button("Start", 12, y + 48, Math.min(110, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> runAndClose(MenuCommands.drill(drill.id())));
		}
	}

	private void you() {
		int y = card(100, false);
		text("Reset. Refocus. Go again.", 12, y + 12, TEXT);
		button("Full heal", 12, y + 35, Math.min(130, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> run(MenuCommands.heal()));
		boolean regen = !"false".equals(setting("naturalRegeneration"));
		button("Natural regen: " + (regen ? "On" : "Off"), 12, y + 66, Math.min(210, contentWidth - 24), MenuButton.Tone.NORMAL,
			() -> run(MenuCommands.setConfig("naturalRegeneration", Boolean.toString(!regen))));
		section("YOUR LAST FIGHT");
		if (state.myFights().isEmpty()) { empty("A fresh page", "Finish a practice fight to see your report and coach feedback here."); return; }
		var last = state.myFights().getLast();
		y = card(48, false);
		text(pretty(last.mode()) + " vs " + last.opponent(), 12, y + 10, TEXT);
		text(last.result(), 12, y + 27, ACCENT);
		for (var number : last.numbers().entrySet()) {
			y = card(32, false);
			text(number.getKey() + ": " + number.getValue(), 12, y + 12, TEXT);
		}
		for (String line : last.coach()) {
			int lines = font.getSplitter().splitLines(line, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
			y = card(24 + lines * 12, false);
			wrap(line, 12, y + 12, contentWidth - 24, ACCENT);
		}
		String trend = state.myFights().stream().map(f -> f.numbers().get("Full charge")).filter(java.util.Objects::nonNull).reduce((a, b) -> a + " > " + b).orElse("");
		if (state.myFights().size() > 1 && !trend.isEmpty()) {
			section("FULL CHARGE OVER RECENT FIGHTS");
			wrap(trend, 0, cursorY, contentWidth, GOLD);
			cursorY += 12 * font.getSplitter().splitLines(trend, contentWidth, net.minecraft.network.chat.Style.EMPTY).size() + 12;
		}
	}

	private void techniques() {
		MenuState.BotEntry bot = state.bots().stream().filter(b -> b.name().equals(techniquesBot)).findFirst().orElse(null);
		if (bot == null) { empty("Bot removed", "Choose another opponent in Bots."); return; }
		section(bot.name().toUpperCase(Locale.ROOT) + " / " + pretty(bot.style()));
		flowButtons(List.of("Back to bots", bot.alive() ? "Kill" : "Respawn"), List.of(() -> show(Tab.BOTS),
			() -> run(bot.alive() ? MenuCommands.kill(bot.name()) : MenuCommands.respawn(bot.name()))));
		for (Technique technique : Technique.values()) {
			boolean on = !bot.disabledTechniques().contains(technique.id());
			int y = card(56, false);
			text(techniqueName(technique), 12, y + 10, TEXT);
			button(technique.id() + ": " + (on ? "On" : "Off"), 12, y + 28, Math.min(150, contentWidth - 24), MenuButton.Tone.NORMAL,
				() -> run(MenuCommands.technique(bot.name(), technique.id(), !on)));
		}
	}

	private void settings() {
		field("settingsSearch", settingsSearch, "Search settings", 0, cursorY, contentWidth, 64, v -> { settingsSearch = v; scroll = 0; rebuildNextTick = true; });
		cursorY += 34;
		List<MenuState.Setting> settings = state.settings().stream().filter(s -> matches(settingsSearch, s.key())).toList();
		if (settings.isEmpty()) { empty("No matching settings", "Try a shorter search."); }
		for (MenuState.Setting s : settings) {
			int y = card(62, false);
			text(pretty(s.key()), 12, y + 10, TEXT);
			if (s.type().equals("boolean")) {
				boolean value = Boolean.parseBoolean(s.value());
				button(value ? "On" : "Off", 12, y + 33, Math.min(110, contentWidth - 24), MenuButton.Tone.NORMAL,
					() -> run(MenuCommands.setConfig(s.key(), Boolean.toString(!value))));
			} else {
				EditBox box = field("setting:" + s.key(), settingDrafts.getOrDefault(s.key(), s.value()), pretty(s.key()), 12, y + 33,
					contentWidth - 86, 128, v -> settingDrafts.put(s.key(), v));
				button("Set", contentWidth - 66, y + 32, 54, MenuButton.Tone.NORMAL, () -> {
					if (box.getValue().isBlank()) { status = "Enter a value"; return; }
					run(MenuCommands.setConfig(s.key(), box.getValue()));
					settingDrafts.remove(s.key());
				});
			}
		}
	}

	private void choices(List<String> values, String chosen, Consumer<String> select) {
		int columns = contentWidth >= 360 ? 4 : contentWidth >= 240 ? 3 : 2;
		int w = (contentWidth - (columns - 1) * 6) / columns;
		for (int i = 0; i < values.size(); i++) {
			String value = values.get(i);
			MenuButton button = new MenuButton(pretty(value), contentX + i % columns * (w + 6), cursorY + i / columns * 28, w, 22,
				"swight".equals(value) ? MenuButton.Tone.GOLD : MenuButton.Tone.NORMAL, value.equals(chosen), b -> select.accept(value));
			button.setTooltip(Tooltip.create(Component.literal(value)));
			addContent(button);
		}
		cursorY += ((values.size() + columns - 1) / columns) * 28 + 8;
	}

	private void flowButtons(List<String> names, List<Runnable> actions) {
		int columns = contentWidth < 260 ? 2 : 3;
		int w = (contentWidth - (columns - 1) * 6) / columns;
		for (int i = 0; i < names.size(); i++) { button(names.get(i), i % columns * (w + 6), cursorY + i / columns * 28, w, MenuButton.Tone.NORMAL, actions.get(i)); }
		cursorY += ((names.size() + columns - 1) / columns) * 28 + 8;
	}

	private void illustrate(int y, boolean uhc) {
		if (contentWidth >= 350) { illustrations.add(new Illustration(y, uhc)); }
	}

	private int card(int height, boolean featured) {
		int y = cursorY;
		cards.add(new Card(y, height, featured));
		cursorY += height + 10;
		return y;
	}

	private void section(String title) { text(title, 0, cursorY + 4, MUTED); cursorY += 24; }
	private void text(String text, int dx, int y, int color) { labels.add(new Label(font.plainSubstrByWidth(text, contentWidth - dx - 10), contentX + dx, y, color)); }
	private void wrap(String text, int dx, int y, int w, int color) {
		for (var line : font.getSplitter().splitLines(text, w, net.minecraft.network.chat.Style.EMPTY)) {
			labels.add(new Label(line.getString(), contentX + dx, y, color)); y += 12;
		}
	}
	private void empty(String title, String hint) {
		int n = font.getSplitter().splitLines(hint, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
		int y = card(44 + n * 12, false); text(title, 12, y + 12, TEXT); wrap(hint, 12, y + 32, contentWidth - 24, MUTED);
	}

	private MenuButton button(String name, int dx, int y, int w, MenuButton.Tone tone, Runnable action) {
		return addContent(new MenuButton(name, contentX + dx, y, w, 22, tone, false, b -> action.run()));
	}

	private EditBox field(String id, String value, String hint, int dx, int y, int w, int maxLength, Consumer<String> responder) {
		// Reuse editors so snapshots and live search preserve selection, caret, and in-progress text input.
		EditBox field = previousFields.get(id);
		if (field == null) { field = new EditBox(font, contentX + dx + 7, y + 5, w - 14, 18, Component.literal(hint)); }
		field.setResponder(v -> { });
		field.setX(contentX + dx + 7); field.setY(y + 5); field.setWidth(w - 14);
		field.setMaxLength(maxLength);
		if (!field.getValue().equals(value)) { field.setValue(value); }
		field.setBordered(false); field.setTextColor(TEXT);
		field.setHint(Component.literal(hint));
		field.setResponder(responder);
		fields.put(id, field);
		addContent(field);
		// Keep fields visually distinct from action buttons.
		cards.add(new Card(y, 26, false));
		return field;
	}

	private <T extends AbstractWidget> T addContent(T widget) {
		widget.visible = widget.getY() >= contentTop && widget.getBottom() <= contentBottom;
		return addRenderableWidget(widget);
	}

	private void show(Tab next) {
		positions.put(tab, scroll);
		if (tab == Tab.KITS && next != Tab.SPAWN && next != Tab.KITS) { choosingKit = false; }
		tab = next; scroll = positions.getOrDefault(next, 0); setFocused(null); rebuildWidgets();
	}

	private int maxScroll() { return Math.max(0, totalHeight - (contentBottom - contentTop)); }
	private void moveScroll(int delta) {
		int next = Math.max(0, Math.min(maxScroll(), scroll + delta));
		if (next != scroll) { scroll = next; rebuildKeepingFocus(); }
	}

	@Override
	public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (x >= contentX && x < contentX + contentWidth + 8 && y >= contentTop && y < contentBottom) {
			moveScroll((int) Math.round(-vertical * 28)); return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	private void run(String... commands) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null) { return; }
		for (String command : commands) { minecraft.player.connection.sendCommand(command); }
		refreshIn = 5; status = "Updating...";
	}
	private void runAndClose(String command) { run(command); onClose(); }
	private void requestRefresh() {
		if (ClientPlayNetworking.canSend(MenuRequestPayload.TYPE)) { ClientPlayNetworking.send(new MenuRequestPayload(false)); status = "Updating..."; }
	}

	@Override
	public void tick() {
		super.tick(); ticks++;
		if (rebuildNextTick) { rebuildNextTick = false; rebuildKeepingFocus(); }
		if (refreshIn > 0 && --refreshIn == 0) { requestRefresh(); }
		if (ticks % 60 == 0 && refreshIn == 0 && !(getFocused() instanceof EditBox)) { requestRefresh(); }
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		g.fillGradient(0, 0, width, height, 0xEF101B2A, 0xF5080E19);
		// The quiet grid belongs to the backdrop; content remains a solid, readable surface.
		for (int x = 12; x < width; x += 24) {
			for (int y = 12; y < height; y += 24) { g.fill(x, y, x + 1, y + 1, 0x303D7083); }
		}
		g.fill(left + 3, top + 4, left + panelWidth + 3, top + panelHeight + 4, 0x70000000);
		g.fill(left, top, left + panelWidth, top + panelHeight, 0xFF111A28);
		g.outline(left, top, panelWidth, panelHeight, 0xFF314153);
		g.fillGradient(left + 1, top + 1, left + sidebar, top + panelHeight - 1, 0xFF1B2835, 0xFF141C29);
		MenuArt.corners(g, left, top, panelWidth, panelHeight, 0xFF53747E);
		g.fillGradient(contentX, top + 1, left + panelWidth - 1, top + 49, 0xFF182C38, 0xFF111A28);
		if (panelHeight > 330 && sidebar >= 110) {
			int cy = top + panelHeight - 91;
			g.horizontalLine(left + 18, left + sidebar - 18, cy - 14, 0xFF293D4B);
			MenuArt.item(g, net.minecraft.world.item.Items.DIAMOND_SWORD, left + 31, cy);
			MenuArt.item(g, net.minecraft.world.item.Items.SHIELD, left + 56, cy);
			g.text(font, "TRAIN. ADAPT.", left + 18, cy + 27, MUTED, false);
			g.text(font, "GO AGAIN.", left + 18, cy + 40, ACCENT, false);
		}
		g.fill(left + 10, top + 14, left + 12, top + 30, ACCENT);
		g.text(font, "SPARBOT", left + 18, top + 13, TEXT, false);
		g.text(font, "PRACTICE", left + 18, top + 26, MUTED, false);
		if (panelHeight > 250) { g.text(font, "v" + version, left + 18, top + panelHeight - 18, MUTED, false); }
		g.text(font, font.plainSubstrByWidth(tab.heading, contentWidth), contentX, top + 16, TEXT, false);
		g.text(font, font.plainSubstrByWidth(tab.description, contentWidth), contentX, top + 34, MUTED, false);
		g.horizontalLine(contentX, contentX + contentWidth, top + 51, 0xFF293747);
		g.enableScissor(contentX, contentTop, contentX + contentWidth, contentBottom);
		for (Card card : cards) {
			g.fillGradient(contentX, card.y(), contentX + contentWidth, card.y() + card.height(), card.featured() ? 0xFF373027 : 0xFF20313F, card.featured() ? 0xFF252421 : 0xFF192331);
			g.outline(contentX, card.y(), contentWidth, card.height(), card.featured() ? 0xFF6C5639 : 0xFF2B3A4D);
			if (card.featured()) {
				g.fill(contentX, card.y() + 1, contentX + 2, card.y() + card.height() - 1, GOLD);
				MenuArt.corners(g, contentX, card.y(), contentWidth, card.height(), GOLD);
			}
			if (card.height() > 60) {
				for (int i = 0; i < 6; i++) {
					int x = contentX + contentWidth - 12 - i * 6;
					g.fill(x, card.y() + 7, x + 2, card.y() + 9, card.featured() ? 0xFF655036 : 0xFF354D5E);
				}
			}
		}
		for (Illustration art : illustrations) { MenuArt.loadout(g, contentX + contentWidth - 12, art.y(), art.uhc()); }
		for (Label label : labels) { g.text(font, label.text(), label.x(), label.y(), label.color(), false); }
		g.disableScissor();
		if (maxScroll() > 0) {
			int track = contentBottom - contentTop;
			int thumb = Math.max(18, track * track / Math.max(1, totalHeight));
			int y = contentTop + (track - thumb) * scroll / maxScroll();
			g.fill(contentX + contentWidth + 4, contentTop, contentX + contentWidth + 6, contentBottom, 0xFF253344);
			g.fill(contentX + contentWidth + 4, y, contentX + contentWidth + 6, y + thumb, ACCENT);
		}
		if (contentWidth > 370) { g.text(font, status, contentX, top + panelHeight - 20, MUTED, false); }
	}

	private String setting(String key) { return state.settings().stream().filter(s -> s.key().equals(key)).map(MenuState.Setting::value).findFirst().orElse(""); }
	private static String pick(String current, List<String> options, String fallback) {
		if (current != null && options.contains(current)) { return current; }
		return options.contains(fallback) ? fallback : options.isEmpty() ? "-" : options.getFirst();
	}
	private static boolean matches(String query, String text) { return text.toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT)); }
	private static String pretty(String id) {
		if (id.equals("uhc") || id.equals("uhc_duel")) { return "UHC"; }
		if (id.equals("sword_pvphq")) { return "Sword PvPHQ"; }
		if (id.equals("crystal_pvphq")) { return "Crystal PvPHQ"; }
		if (id.equals("mace_pvphq")) { return "Mace PvPHQ"; }
		if (id.equals("smp")) { return "SMP"; }
		String[] words = id.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').split(" ");
		return java.util.Arrays.stream(words).filter(w -> !w.isEmpty()).map(w -> Character.toUpperCase(w.charAt(0)) + w.substring(1)).reduce((a, b) -> a + " " + b).orElse(id);
	}
	private static String techniqueName(Technique t) {
		return switch (t) { case W_TAP -> "W-tap"; case S_TAP -> "S-tap"; case JUMP_RESET -> "Jump reset"; case BLOCK_HIT -> "Block hitting";
			case SHIELD_STUN -> "Shield follow-up"; default -> pretty(t.id()); };
	}
	/** Bot1 -> Bot2; large numeric suffixes cannot overflow or trap the form. */
	static String nextName(String name) {
		int i = name.length(); while (i > 0 && Character.isDigit(name.charAt(i - 1))) { i--; }
		String base = name.substring(0, i);
		try {
			long n = i == name.length() ? 1 : Long.parseLong(name.substring(i));
			String next = base + Math.addExact(n, 1);
			return next.length() <= 16 ? next : "Bot1";
		} catch (ArithmeticException | NumberFormatException e) { return "Bot1"; }
	}
	@Override public boolean isPauseScreen() { return false; }
}
