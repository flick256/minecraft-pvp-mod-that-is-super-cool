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
		RIVALS("Rivals", "A ladder worth climbing", "Choose a rival, learn their style, and earn the next challenge."),
		CAREER("Career", "Your season, your pace", "Track promotion progress and continue a series when you are ready."),
		REPLAY("Replay", "Study the exchange", "Pause, scrub and step through a recorded fight."),
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
	private boolean choosingKit, choosingRecording, rebuildNextTick;
	private int scroll, totalHeight, cursorY, refreshIn, ticks;
	private String replaySelection;
	private String replaySeekDraft = "";
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

	/** Current server recording snapshot, for accessible review controls and client UI integration checks. */
	public List<MenuState.ReplayEntry> recordingEntries() { return state.recordings(); }

	/** Read-only text and control bounds for compact-layout checks. Content text is clipped to its actual scissor. */
	public List<StudyScreen.LayoutRect> layoutSnapshot() {
		List<StudyScreen.LayoutRect> result = new ArrayList<>();
		addSnapshotText(result, "sidebar-label", "SPARBOT", left + 18, top + 13, left, top, left + sidebar, top + panelHeight);
		addSnapshotText(result, "sidebar-label", "PRACTICE", left + 18, top + 26, left, top, left + sidebar, top + panelHeight);
		if (panelHeight > 250) addSnapshotText(result, "sidebar-label", "v" + version, left + 18, top + panelHeight - 18,
			left, top, left + sidebar, top + panelHeight);
		int navigationBottom = top + (panelHeight < 320 ? 42 : 44) + 10 * (panelHeight < 320 ? 14 : 22) + (panelHeight < 320 ? 12 : 20);
		if (panelHeight > 330 && sidebar >= 110 && top + panelHeight - 105 >= navigationBottom + 8) {
			int cy = top + panelHeight - 91;
			addSnapshotText(result, "sidebar-label", "TRAIN. ADAPT.", left + 18, cy + 27, left, top, left + sidebar, top + panelHeight);
			addSnapshotText(result, "sidebar-label", "GO AGAIN.", left + 18, cy + 40, left, top, left + sidebar, top + panelHeight);
		}
		addSnapshotText(result, "header-label", font.plainSubstrByWidth(tab.heading, contentWidth), contentX, top + 16,
			contentX, top, contentX + contentWidth, top + 51);
		addSnapshotText(result, "header-label", font.plainSubstrByWidth(tab.description, contentWidth), contentX, top + 34,
			contentX, top, contentX + contentWidth, top + 51);
		for (Label label : labels) addSnapshotText(result, "content-label", label.text(), label.x(), label.y(),
			contentX, contentTop, contentX + contentWidth, contentBottom);
		if (contentWidth > 370) addSnapshotText(result, "status-label", status, contentX, top + panelHeight - 20,
			contentX, top, contentX + contentWidth, top + panelHeight);
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
			if (widget instanceof MenuButton nav && widget.getX() < contentX) {
				boolean hasIcon = widget.getWidth() >= 90 && widget.getHeight() >= 18;
				int reserve = hasIcon ? 32 : 12;
				String navText = font.plainSubstrByWidth(nav.getMessage().getString(), widget.getWidth() - reserve);
				int navX = widget.getX() + (hasIcon ? 26 : 10);
				int navY = widget.getY() + (widget.getHeight() - 8) / 2;
				addSnapshotText(result, "sidebar-label", navText, navX, navY, left, top, left + sidebar, top + panelHeight);
			}
			result.add(new StudyScreen.LayoutRect("widget", widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
		}
		return List.copyOf(result);
	}

	private void addSnapshotText(List<StudyScreen.LayoutRect> target, String kind, String text, int x, int y,
			int clipLeft, int clipTop, int clipRight, int clipBottom) {
		int x1 = Math.max(x, clipLeft), y1 = Math.max(y, clipTop);
		int x2 = Math.min(x + font.width(text), clipRight), y2 = Math.min(y + font.lineHeight, clipBottom);
		if (x2 > x1 && y2 > y1) target.add(new StudyScreen.LayoutRect(kind, text, x1, y1, x2 - x1, y2 - y1));
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
		// Compact screens still expose every destination. Tighten the row height so
		// the final entry stays above the panel edge at 400×225 and similar sizes.
		int navStep = panelHeight < 320 ? 14 : 22;
		int navHeight = panelHeight < 320 ? 12 : 20;
		int navY = top + (panelHeight < 320 ? 42 : 44);
		for (Tab each : Tab.values()) {
			if (each == Tab.TECHNIQUES) { continue; }
			MenuButton button = new MenuButton(each.label, left + 8, navY, sidebar - 16, navHeight, MenuButton.Tone.NAV,
				tab == each || each == Tab.BOTS && tab == Tab.TECHNIQUES, b -> show(each));
			addRenderableWidget(button);
			navY += navStep;
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
			case RIVALS -> rivals();
			case CAREER -> career();
			case REPLAY -> replay();
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
		if (max == 0 ? contentWidth >= actionWidth + 68 : contentWidth >= actionWidth + 116) {
			addRenderableWidget(new MenuButton("Refresh", edge - 56, y, 56, 20, MenuButton.Tone.NORMAL, false, b -> requestRefresh()));
			edge -= 62;
		}
		if (max > 0) {
			addRenderableWidget(new MenuButton("Up", edge - 47, y, 22, 20, MenuButton.Tone.NORMAL, false, b -> moveScroll(-72))).active = scroll > 0;
			addRenderableWidget(new MenuButton("Dn", edge - 22, y, 22, 20, MenuButton.Tone.NORMAL, false, b -> moveScroll(72))).active = scroll < max;
		}

	}

	private void overview() {
		String practiceDescription = "Head into practice with a complete kit, or build your own sparring partner.";
		int practiceLines = font.getSplitter().splitLines(practiceDescription, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
		int practiceButtonY = Math.max(64, 28 + practiceLines * 12 + 8);
		int y = card(practiceButtonY + 30, false);
		illustrate(y + 58, false);
		text("A better fight starts here.", 12, y + 12, TEXT);
		wrap(practiceDescription, 12, y + 28, contentWidth - 24, MUTED);
		button("Start practice", 12, y + practiceButtonY, Math.min(150, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> show(Tab.PRACTICE));
		String studyDescription = "Work through a question and sharpen your recall before the next fight.";
		int studyLines = font.getSplitter().splitLines(studyDescription, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
		y = card(61 + studyLines * 12, true);
		text("STUDY / EXAM PRACTICE", 12, y + 12, GOLD);
		wrap(studyDescription, 12, y + 29, contentWidth - 24, MUTED);
		button("Open study", 12, y + 35 + studyLines * 12, Math.min(120, contentWidth - 24), MenuButton.Tone.GOLD, StudyClient::openOptional);
		if (state.profiles().contains("swight")) {
			String swightDescription = "Utility with a plan. A demanding UHC opponent with human limits, built to challenge Demon.";
			int swightLines = font.getSplitter().splitLines(swightDescription, contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
			int challengeY = Math.max(82, 29 + swightLines * 12 + 8);
			y = card(challengeY + 30, true);
			illustrate(y + 78, true);
			text("SWIGHT  /  UHC SPECIALIST", 12, y + 12, GOLD);
			wrap(swightDescription, 12, y + 29, contentWidth - 24, MUTED);
			button("Challenge Swight", 12, y + challengeY, Math.min(160, contentWidth - 24), MenuButton.Tone.GOLD,
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
		section("ARENA TERRAIN");
		String terrain = setting("practiceTerrain");
		choices(List.of("flat", "uneven", "obstacles"), pick(terrain, List.of("flat", "uneven", "obstacles"), "flat"),
			value -> run("sparbot practice terrain " + value));
		flowButtons(List.of("Rematch"), List.of(() -> runAndClose("sparbot practice rematch")));
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
			boolean compactActions = !choosingKit && contentWidth < 300;
			int y = card(compactActions ? 98 : 78, choosingKit && kit.id().equals(spawnKit));
			text(kit.displayName(), 12, y + 10, TEXT);
			text(kit.verified() ? "Verified layout" : "Layout details unverified", 12, y + 27, kit.verified() ? ACCENT : MUTED);
			if (choosingKit) {
				button("Use kit", 12, y + 50, Math.min(130, contentWidth - 24), MenuButton.Tone.PRIMARY, () -> { spawnKit = kit.id(); choosingKit = false; show(Tab.SPAWN); });
			} else {
				int bw = compactActions ? (contentWidth - 30) / 2 : Math.min(76, (contentWidth - 42) / 4);
				boolean saved = state.myLayouts().contains(kit.id());
				int firstRowY = y + (compactActions ? 42 : 50), secondRowY = y + 70;
				button("Equip", 12, firstRowY, bw, MenuButton.Tone.PRIMARY, () -> run(MenuCommands.giveKit(kit.id())));
				button("Edit", 18 + bw, firstRowY, bw, MenuButton.Tone.NORMAL, () -> run("sparbot kiteditor " + kit.id()))
					.setTooltip(Tooltip.create(Component.literal("Open this template as a new draft, then save it with a new ID to clone it.")));
				button(saved ? "Save layout*" : "Save layout", compactActions ? 12 : 24 + bw * 2, compactActions ? secondRowY : firstRowY, bw, MenuButton.Tone.NORMAL, () -> run(MenuCommands.saveLayout(kit.id())))
					.setTooltip(Tooltip.create(Component.literal("Save your current inventory arrangement for this kit.")));
				button("Reset", compactActions ? 18 + bw : 30 + bw * 3, compactActions ? secondRowY : firstRowY, bw, MenuButton.Tone.NORMAL, () -> run(MenuCommands.resetLayout(kit.id()))).active = saved;
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
		List<MenuState.TrainingEntry> recommendations = state.training().isEmpty()
			? io.github.flick256.sparbot.core.drill.TrainingPlan.recommendations(state.myFights()).stream()
				.map(r -> new MenuState.TrainingEntry(r.drillId(), r.title(), r.reason(), r.priority())).toList()
			: state.training();
		section("NEXT SESSION");
		if (recommendations.isEmpty()) {
			empty("Build a little evidence", "Your coach will recommend a drill after a fight has enough attempts to show a pattern.");
		} else {
			var recommendation = recommendations.getFirst();
			int lines = font.getSplitter().splitLines(recommendation.reason(), contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
			y = card(70 + lines * 12, true);
			text(recommendation.title(), 12, y + 10, GOLD);
			wrap(recommendation.reason(), 12, y + 28, contentWidth - 24, MUTED);
			button("Start drill", 12, y + 42 + lines * 12, Math.min(112, contentWidth - 24), MenuButton.Tone.PRIMARY,
				() -> runAndClose(MenuCommands.drill(recommendation.drillId())));
		}
		button("Rematch", 0, cursorY, Math.min(112, contentWidth), MenuButton.Tone.NORMAL, () -> runAndClose("sparbot practice rematch"));
		cursorY += 32;
		String trend = state.myFights().stream().map(f -> f.numbers().get("Full charge")).filter(java.util.Objects::nonNull).reduce((a, b) -> a + " > " + b).orElse("");
		if (state.myFights().size() > 1 && !trend.isEmpty()) {
			section("FULL CHARGE OVER RECENT FIGHTS");
			wrap(trend, 0, cursorY, contentWidth, GOLD);
			cursorY += 12 * font.getSplitter().splitLines(trend, contentWidth, net.minecraft.network.chat.Style.EMPTY).size() + 12;
		}
	}

	private void rivals() {
		if (state.rivals().isEmpty()) { empty("Rivals are on their way", "Refresh after the server loads its challenge roster."); return; }
		for (MenuState.RivalEntry rival : state.rivals()) {
			int descriptionLines = font.getSplitter().splitLines(rival.description(), contentWidth - 24, net.minecraft.network.chat.Style.EMPTY).size();
			int detailY = 34 + descriptionLines * 12;
			int y = card(detailY + 84, rival.unlocked());
			text(rival.name() + "  /  " + pretty(rival.mode()), 12, y + 10, rival.unlocked() ? GOLD : TEXT);
			wrap(rival.description(), 12, y + 28, contentWidth - 24, MUTED);
			String detail = pretty(rival.style()) + "  ·  " + rival.wins() + "W " + rival.losses() + "L";
			text(detail, 12, y + detailY, ACCENT);
			text("Strength: " + rival.strength(), 12, y + detailY + 16, MUTED);
			text("Opening: " + rival.weakness(), 12, y + detailY + 32, MUTED);
			button(rival.unlocked() ? "Challenge" : "Locked", 12, y + detailY + 50, Math.min(112, contentWidth - 24), MenuButton.Tone.PRIMARY,
				() -> { if (rival.unlocked()) runAndClose("sparbot rival " + rival.id()); }).active = rival.unlocked();
		}
	}

	private void career() {
		if (state.career().isEmpty()) { empty("Your season starts with a fight", "Complete a ranked challenge to see promotion progress here."); return; }
		for (MenuState.CareerEntry entry : state.career()) {
			int y = card(entry.champion() ? 72 : 108, entry.champion());
			text(pretty(entry.mode()) + "  /  " + entry.tier(), 12, y + 10, entry.champion() ? GOLD : TEXT);
			text(entry.wins() + " wins  ·  " + entry.losses() + " losses", 12, y + 28, MUTED);
			text(entry.champion() ? "Champion" : "Promotion: " + entry.progress() + " / " + entry.required(), 12, y + 46, entry.champion() ? GOLD : ACCENT);
			if (!entry.champion()) text("Next: " + entry.nextRival(), 12, y + 62, MUTED);
			if (!entry.champion()) {
				button("Continue", 12, y + 82, Math.min(120, contentWidth - 24), MenuButton.Tone.PRIMARY,
					() -> runAndClose("sparbot career " + entry.mode() + " continue"));
			}
		}
		section("PRACTICE ADAPTATION");
		boolean on = "true".equals(setting("adaptivePractice"));
		button("Adaptation: " + (on ? "On" : "Off"), 0, cursorY, Math.min(160, contentWidth), MenuButton.Tone.NORMAL,
			() -> run("sparbot career adapt " + (on ? "off" : "on")));
		cursorY += 32;
	}

	private void replay() {
		if (state.recordings().isEmpty()) { empty("No recordings yet", "Record a fight, then return here to study the key moments."); return; }
		MenuState.ReplayEntry selectedChoice = state.recordings().stream().filter(r -> r.name().equals(replaySelection)).findFirst().orElse(null);
		if (selectedChoice == null) selectedChoice = state.recordings().stream().filter(MenuState.ReplayEntry::active).findFirst().orElse(state.recordings().getFirst());
		final MenuState.ReplayEntry selected = selectedChoice;
		if (choosingRecording) {
			button("Back to review", 0, cursorY, Math.min(140, contentWidth), MenuButton.Tone.NORMAL, () -> {
				choosingRecording = false; scroll = 0; rebuildWidgets();
			});
			cursorY += 34;
			section("RECORDING LIBRARY");
			for (MenuState.ReplayEntry replay : state.recordings()) {
				int y = card(58, replay.name().equals(selected.name()));
				int actionWidth = Math.min(112, Math.max(92, contentWidth / 3));
				int textWidth = Math.max(40, contentWidth - actionWidth - 36);
				labels.add(new Label(font.plainSubstrByWidth(replay.name(), textWidth), contentX + 12, y + 9, TEXT));
				String summary = (replay.active() ? replay.paused() ? "Paused" : "Playing" : "Saved") + " · " + replay.durationTicks() + " ticks";
				labels.add(new Label(font.plainSubstrByWidth(summary, textWidth), contentX + 12, y + 29, MUTED));
				button("Select recording", contentWidth - actionWidth - 12, y + 18, actionWidth, MenuButton.Tone.NORMAL, () -> {
					replaySelection = replay.name(); replaySeekDraft = ""; choosingRecording = false; scroll = 0; rebuildWidgets();
				});
			}
			return;
		}
		int columns = contentWidth >= 300 ? 4 : 2;
		int reviewY = card(columns == 4 ? 128 : 152, selected.active());
		text(selected.name(), 12, reviewY + 10, TEXT);
		text((selected.active() ? selected.paused() ? "Paused" : "Playing" : "Ready") + "  ·  " + selected.frame() + " / " + selected.durationTicks() + " ticks  ·  " + selected.speed() + "×", 12, reviewY + 28, MUTED);
		int bw = Math.min(86, (contentWidth - 36) / 3);
		MenuButton playback = button(selected.active() ? selected.paused() ? "Resume" : "Pause" : "Play", 12, reviewY + 50, bw,
			selected.active() ? MenuButton.Tone.PRIMARY : MenuButton.Tone.GOLD,
			() -> run(selected.active() ? "sparbot replay " + (selected.paused() ? "resume " : "pause ") + selected.name() : "sparbot replay " + selected.name()));
		button("Step −", 18 + bw, reviewY + 50, bw, MenuButton.Tone.NORMAL, () -> run("sparbot replay step " + selected.name() + " back")).active = selected.active();
		button("Step +", 24 + bw * 2, reviewY + 50, bw, MenuButton.Tone.NORMAL, () -> run("sparbot replay step " + selected.name())).active = selected.active();
		int rowWidth = Math.min(132, (contentWidth - 30) / 2);
		button("Stop", 12, reviewY + 74, rowWidth, MenuButton.Tone.DANGER, () -> run("sparbot replay stop " + selected.name())).active = selected.active();
		button("Choose recording", 18 + rowWidth, reviewY + 74, rowWidth, MenuButton.Tone.NORMAL, () -> {
			choosingRecording = true; scroll = 0; rebuildWidgets();
		});
		List<String> speeds = List.of("0.25", "0.5", "1", "2");
		int speedWidth = (contentWidth - 24 - (columns - 1) * 4) / columns;
		for (int i = 0; i < speeds.size(); i++) {
			String speed = speeds.get(i);
			button(speed + "×", 12 + i % columns * (speedWidth + 4), reviewY + 98 + i / columns * 24, speedWidth,
				MenuButton.Tone.NORMAL, () -> run("sparbot replay speed " + selected.name() + " " + speed)).active = selected.active();
		}
		playback.setTooltip(Tooltip.create(Component.literal((selected.active() ? "Pause or resume " : "Start ") + selected.name() + " replay.")));
		section("SEEK / EVENTS · " + selected.name());
		EditBox seek = field("replaySeek", replaySeekDraft, "Tick", 0, cursorY, Math.min(contentWidth - 62, 150), 8, v -> replaySeekDraft = v);
		button("Seek", Math.min(contentWidth - 52, 156), cursorY, 52, MenuButton.Tone.NORMAL, () -> {
			try { int tick = Integer.parseInt(seek.getValue()); if (tick >= 0 && tick <= selected.durationTicks()) run("sparbot replay seek " + selected.name() + " " + tick); }
			catch (NumberFormatException ignored) { status = "Enter a tick number"; }
		}).active = selected.active();
		cursorY += 36;
		for (MenuState.ReplayEventEntry event : selected.events()) {
			int textLines = font.getSplitter().splitLines(event.text(), Math.max(48, contentWidth - 96), net.minecraft.network.chat.Style.EMPTY).size();
			int height = Math.max(48, 32 + textLines * 12);
			int y = card(height, false);
			text(event.tick() + "t  ·  " + pretty(event.kind()), 12, y + 8, ACCENT);
			wrap(event.text(), 12, y + 24, Math.max(48, contentWidth - 96), MUTED);
			button("Go", Math.max(12, contentWidth - 58), y + (height - 22) / 2, 46, MenuButton.Tone.NORMAL,
				() -> run("sparbot replay seek " + selected.name() + " " + event.tick())).active = selected.active();
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
	List<MenuState.Setting> settings = state.settings().stream().filter(s -> !s.type().equals("preference"))
			.filter(s -> matches(settingsSearch, s.key())).toList();
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
		int navigationBottom = top + (panelHeight < 320 ? 42 : 44) + 10 * (panelHeight < 320 ? 14 : 22) + (panelHeight < 320 ? 12 : 20);
		if (panelHeight > 330 && sidebar >= 110 && top + panelHeight - 105 >= navigationBottom + 8) {
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
