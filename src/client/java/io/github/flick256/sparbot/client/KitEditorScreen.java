package io.github.flick256.sparbot.client;

import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import io.github.flick256.sparbot.kit.editor.KitEditorPayloads;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.ItemStack;

/** Draft-only kit editor laid out for both normal and compact Minecraft GUI scales. */
public final class KitEditorScreen extends Screen {
	private static final int PANEL = 0xEE17212B, CELL = 0xFF263442, SELECTED = 0xFF356D68;
	private Kit draft;
	private String status, search = "", selectedTarget = "inventory";
	private String idDraft = "", nameDraft = "", countDraft = "1", enchantDraft = "", enchantLevelDraft = "1";
	private String selectedPaletteId;
	private int selectedSlot, paletteScroll, page;
	private EditBox idField, nameField, searchField, countField, enchantField, enchantLevelField;
	private final List<Button> widgets = new ArrayList<>();
	private final Map<String, Button> slotAccessButtons = new LinkedHashMap<>();
	private final List<Button> paletteAccessButtons = new ArrayList<>();

	public KitEditorScreen(String json, String status) {
		super(Component.literal("Kit Editor"));
		this.status = status;
		try { draft = Kits.parse(new StringReader(json)); }
		catch (IllegalArgumentException e) { this.status = "Could not load draft: " + e.getMessage(); }
		if (draft != null) {
			idDraft = draft.id() + "_copy";
			nameDraft = draft.displayName() + " copy";
		}
	}

	public void setStatus(String status) { this.status = status; }

	@Override protected void init() { rebuildPage(); }

	private void addButton(Button button) { addRenderableWidget(button); widgets.add(button); }

	private void rebuildPage() {
		clearWidgets(); widgets.clear(); slotAccessButtons.clear(); paletteAccessButtons.clear();
		// Detached fields must not overwrite a newly selected slot's count when changing tabs.
		idField = null; nameField = null; searchField = null; countField = null; enchantField = null; enchantLevelField = null;
		int tabWidth = Math.max(58, Math.min(78, (width - 24) / 3));
		addButton(tab("Layout", 0, tabWidth));
		addButton(tab("Items", 1, tabWidth));
		addButton(tab("Details", 2, tabWidth));
		if (page == 0) buildLayoutPage();
		else if (page == 1) buildPalettePage();
		else buildDetailsPage();
	}

	private Button tab(String label, int index, int tabWidth) {
		return Button.builder(Component.literal((page == index ? "[ " : "") + label), b -> {
			storeFields(); page = index; rebuildPage();
		}).bounds(8 + index * (tabWidth + 4), 7, tabWidth, 20).build();
	}

	private void buildLayoutPage() {
		int cell = layoutInventoryCell(), x = layoutInventoryX(cell), y = height < 300 ? 52 : 50;
		for (int slot = 0; slot < 36; slot++) {
			final int index = slot;
			Kit.KitItem spec = getInventorySlot(slot);
			String label = (slot < 9 ? "Hotbar slot " + (slot + 1) : "Inventory slot " + slot) + ": " + specDescription(spec);
			Button access = Button.builder(Component.literal(label), b -> selectInventory(index))
				.bounds(x + (slot % 9) * cell, y + (slot / 9) * cell, cell - 2, cell - 2).build();
			access.setAlpha(0); access.setTooltip(Tooltip.create(Component.literal(label)));
			addButton(access); slotAccessButtons.put("inventory:" + slot, access);
		}
		int targetCell = layoutSpecialCell();
		int sx = width >= 520 ? width - 5 * (targetCell + 3) - 8 : (width - 5 * targetCell) / 2;
		int sy = width >= 520 ? (height - targetCell) / 2 : height - targetCell - 22;
		String[] targets = {"armor_head", "armor_chest", "armor_legs", "armor_feet", "offhand"};
		String[] labels = {"Head armor", "Chest armor", "Leg armor", "Foot armor", "Offhand"};
		for (int i = 0; i < targets.length; i++) {
			final String target = targets[i]; Kit.KitItem item = getSpecial(target);
			String label = labels[i] + ": " + specDescription(item);
			int targetX = sx + i * (targetCell + (width >= 520 ? 3 : 0));
			Button access = Button.builder(Component.literal(label), b -> selectSpecial(target))
				.bounds(targetX, sy, targetCell - 2, targetCell - 2).build();
			access.setAlpha(0); access.setTooltip(Tooltip.create(Component.literal(label)));
			addButton(access); slotAccessButtons.put(target, access);
		}
	}

	private int layoutInventoryCell() {
		return width < 400 ? Math.min(30, Math.max(26, (width - 20) / 9)) : Math.min(38, Math.max(28, (width - 20) / 9));
	}
	private int layoutInventoryX(int cell) { return width >= 520 ? 10 : Math.max(8, (width - cell * 9) / 2); }
	private int layoutSpecialCell() {
		return width < 400 ? Math.min(44, Math.max(32, (width - 24) / 5)) : Math.min(46, Math.max(36, (width - 24) / 5));
	}
	private void selectInventory(int slot) {
		selectedTarget = "inventory"; selectedSlot = slot;
		countDraft = Integer.toString(selectedItem() == null ? 1 : selectedItem().countOrDefault());
	}
	private void selectSpecial(String target) {
		selectedTarget = target;
		countDraft = Integer.toString(selectedItem() == null ? 1 : selectedItem().countOrDefault());
	}

	private void drawLayout(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int cell = layoutInventoryCell();
		int x = layoutInventoryX(cell), y = height < 300 ? 52 : 50;
		for (int slot = 0; slot < 36; slot++) {
			int cx = x + (slot % 9) * cell, cy = y + (slot / 9) * cell;
			Kit.KitItem spec = getInventorySlot(slot);
			Button access = slotAccessButtons.get("inventory:" + slot);
			graphicsCell(graphics, cx, cy, cell - 2, cell - 2,
				selectedTarget.equals("inventory") && selectedSlot == slot || access != null && access.isFocused());
			String label = slot < 9 ? "H" + (slot + 1) : Integer.toString(slot);
			drawSpecIcon(graphics, spec, cx + (cell - 18) / 2, cy + 1, label + ": " + specDescription(spec), mouseX, mouseY);
			if (font.width(label) < cell) graphicsText(graphics, label, cx + (cell - font.width(label)) / 2, cy + cell - 10, 0xFFE2EAF0);
		}
		int specialCell = layoutSpecialCell();
		int sx = width >= 520 ? width - 5 * (specialCell + 3) - 8 : (width - 5 * specialCell) / 2;
		int sy = width >= 520 ? (height - specialCell) / 2 : height - specialCell - 22;
		String[] targets = {"armor_head", "armor_chest", "armor_legs", "armor_feet", "offhand"};
		String[] labels = {"Head", "Chest", "Legs", "Feet", "Offhand"};
		for (int i = 0; i < targets.length; i++) {
			int cx = sx + i * (specialCell + (width >= 520 ? 3 : 0)), cy = sy;
			Kit.KitItem item = getSpecial(targets[i]);
			Button access = slotAccessButtons.get(targets[i]);
			graphicsCell(graphics, cx, cy, specialCell - 2, specialCell - 2,
				selectedTarget.equals(targets[i]) || access != null && access.isFocused());
			drawSpecIcon(graphics, item, cx + (specialCell - 18) / 2, cy + 2, labels[i] + ": " + specDescription(item), mouseX, mouseY);
			graphicsText(graphics, labels[i], cx + 2, cy + specialCell - 10, 0xFFE2EAF0);
		}
	}

	private void buildPalettePage() {
		searchField = new EditBox(font, 12, 34, width - 24, 20, Component.literal("Search registered items"));
		searchField.setHint(Component.literal("Search registered items")); searchField.setMaxLength(80); searchField.setValue(search);
		searchField.setResponder(s -> { search = s; paletteScroll = 0; updatePaletteAccessButtons(); }); addRenderableWidget(searchField);
		addButton(Button.builder(Component.literal("Previous"), b -> {
			paletteScroll = Math.max(0, paletteScroll - pageSize()); updatePaletteAccessButtons();
		}).bounds(12, height - 35, 72, 20).build());
		addButton(Button.builder(Component.literal("Next"), b -> {
			paletteScroll = Math.min(Math.max(0, paletteMatches().size() - pageSize()), paletteScroll + pageSize()); updatePaletteAccessButtons();
		}).bounds(width - 84, height - 35, 72, 20).build());
		int columns = width < 400 ? 2 : 4, gap = 3, cellW = (width - 24 - (columns - 1) * gap) / columns;
		for (int i = 0; i < pageSize(); i++) {
			final int index = i; int x = 12 + (i % columns) * (cellW + gap), y = 62 + (i / columns) * 25;
			Button access = Button.builder(Component.literal("Search result " + (i + 1)), b -> selectPaletteIndex(index))
				.bounds(x, y, cellW, 23).build();
			access.setAlpha(0); addButton(access); paletteAccessButtons.add(access);
		}
		updatePaletteAccessButtons();
	}

	private void updatePaletteAccessButtons() {
		List<String> matches = paletteMatches();
		for (int i = 0; i < paletteAccessButtons.size(); i++) {
			Button button = paletteAccessButtons.get(i); int index = paletteScroll + i;
			boolean available = index < matches.size(); button.active = available;
			String label = available ? matches.get(index) : "No item";
			button.setMessage(Component.literal(available ? "Select " + label : label));
			button.setTooltip(Tooltip.create(Component.literal(label)));
		}
	}

	private void selectPaletteIndex(int index) {
		List<String> matches = paletteMatches(); int absolute = paletteScroll + index;
		if (absolute >= matches.size()) return;
		selectedPaletteId = matches.get(absolute); applyPaletteItem(selectedPaletteId);
	}

	private void buildDetailsPage() {
		int compact = width < 430 ? 1 : 0;
		int colWidth = compact == 1 ? width - 24 : (width - 36) / 2;
		int idY = 49;
		idField = new EditBox(font, 12, idY, colWidth, 20, Component.literal("New kit ID"));
		idField.setHint(Component.literal("New kit ID")); idField.setTooltip(Tooltip.create(Component.literal("New kit ID")));
		idField.setMaxLength(48); idField.setValue(idDraft); idField.setResponder(v -> idDraft = v); addRenderableWidget(idField);
		nameField = new EditBox(font, compact == 1 ? 12 : 24 + colWidth, compact == 1 ? 76 : 49, colWidth, 20, Component.literal("Display name"));
		nameField.setHint(Component.literal("Display name")); nameField.setTooltip(Tooltip.create(Component.literal("Display name")));
		nameField.setMaxLength(80); nameField.setValue(nameDraft); nameField.setResponder(v -> nameDraft = v); addRenderableWidget(nameField);
		int y = compact == 1 ? 104 : 80;
		countField = new EditBox(font, 12, y, 48, 20, Component.literal("Count"));
		countField.setHint(Component.literal("Count"));
		countField.setMaxLength(2); countField.setValue(countDraft); countField.setResponder(v -> countDraft = v); addRenderableWidget(countField);
		int enchantY = compact == 1 ? 146 : 117;
		enchantField = new EditBox(font, 12, enchantY, Math.max(100, width - 112), 20, Component.literal("Enchantment ID"));
		enchantField.setHint(Component.literal("minecraft:sharpness")); enchantField.setMaxLength(100);
		enchantField.setValue(enchantDraft); enchantField.setResponder(v -> enchantDraft = v); addRenderableWidget(enchantField);
		enchantLevelField = new EditBox(font, width - 88, enchantY, 42, 20, Component.literal("Level"));
		enchantLevelField.setMaxLength(3); enchantLevelField.setValue(enchantLevelDraft); enchantLevelField.setResponder(v -> enchantLevelDraft = v); addRenderableWidget(enchantLevelField);
		addButton(Button.builder(Component.literal("Apply count"), b -> applyCount()).bounds(68, y, 90, 20).build());
		int enchantButtonsY = compact == 1 ? 172 : 144;
		addButton(Button.builder(Component.literal("Add / update enchant"), b -> changeEnchantment(false)).bounds(12, enchantButtonsY, Math.min(width - 24, 164), 20).build());
		addButton(Button.builder(Component.literal("Remove enchant"), b -> changeEnchantment(true)).bounds(Math.min(width - 108, 184), enchantButtonsY, Math.min(104, width - 24), 20).build());
		int clearX = compact == 1 ? 166 : 174;
		addButton(Button.builder(Component.literal("Clear selected slot"), b -> { removeSelected(); status = "Slot cleared in draft."; })
			.bounds(clearX, y, compact == 1 ? width - clearX - 12 : Math.min(150, width - clearX - 12), 20).build());
		addButton(Button.builder(Component.literal("Save new custom kit"), b -> save()).bounds(12, height - 34, Math.min(160, width - 24), 20).build());
		addButton(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width - 72, height - 34, 60, 20).build());
	}

	private int pageSize() { return width < 400 ? 10 : 16; }

	private void storeFields() {
		if (idField != null) idDraft = idField.getValue();
		if (nameField != null) nameDraft = nameField.getValue();
		if (searchField != null) search = searchField.getValue();
		if (countField != null) countDraft = countField.getValue();
		if (enchantField != null) enchantDraft = enchantField.getValue();
		if (enchantLevelField != null) enchantLevelDraft = enchantLevelField.getValue();
	}

	private Kit.KitItem getInventorySlot(int slot) { return draft == null || draft.slots() == null ? null : draft.slots().get(Integer.toString(slot)); }
	private Kit.KitItem getSpecial(String target) {
		if (draft == null) return null;
		if (target.equals("offhand")) return draft.offhand();
		Kit.Armor a = draft.armor(); if (a == null) return null;
		return switch (target) { case "armor_head" -> a.head(); case "armor_chest" -> a.chest(); case "armor_legs" -> a.legs(); default -> a.feet(); };
	}
	private Kit.KitItem selectedItem() { return selectedTarget.equals("inventory") ? getInventorySlot(selectedSlot) : getSpecial(selectedTarget); }

	private void applyPaletteItem(String itemId) {
		if (draft == null) return;
		Identifier identifier = Identifier.tryParse(itemId);
		if (identifier == null || BuiltInRegistries.ITEM.get(identifier).isEmpty()) { status = "Choose a registered item from the palette."; return; }
		int count; try { count = Integer.parseInt(countDraft); } catch (NumberFormatException e) { status = "Count must be a number from 1 to 99."; return; }
		int max = BuiltInRegistries.ITEM.get(identifier).orElseThrow().value().getDefaultMaxStackSize();
		if (count < 1 || count > max) { status = "That item stacks from 1 to " + max + "."; return; }
		Kit.KitItem previous = selectedItem();
		Kit.KitItem next = previous != null && previous.id().equals(itemId)
			? new Kit.KitItem(itemId, count == 1 ? null : count, previous.enchantments(), previous.potion(), previous.components())
			: new Kit.KitItem(itemId, count == 1 ? null : count, null, null, null);
		writeSelected(next);
		status = "Updated " + targetLabel() + " in the draft.";
	}

	private void applyCount() {
		Kit.KitItem item = selectedItem();
		if (item == null) { status = "Choose an item for the selected slot first."; return; }
		int count; try { count = Integer.parseInt(countDraft); } catch (NumberFormatException e) { status = "Count must be a number from 1 to 99."; return; }
		int max = BuiltInRegistries.ITEM.get(Identifier.parse(item.id())).orElseThrow().value().getDefaultMaxStackSize();
		if (count < 1 || count > max) { status = "That item stacks from 1 to " + max + "."; return; }
		writeSelected(new Kit.KitItem(item.id(), count == 1 ? null : count, item.enchantments(), item.potion(), item.components()));
		status = "Stack count updated in the draft.";
	}

	private void changeEnchantment(boolean remove) {
		Kit.KitItem item = selectedItem();
		if (item == null) { status = "Choose an item for the selected slot first."; return; }
		Identifier identifier = Identifier.tryParse(enchantDraft.trim());
		if (identifier == null || !enchantmentExists(identifier)) { status = "Choose a registered enchantment ID."; return; }
		String id = identifier.toString();
		Map<String, Integer> levels = new LinkedHashMap<>(item.enchantments() == null ? Map.of() : item.enchantments());
		if (remove) levels.remove(id);
		else {
			int level; try { level = Integer.parseInt(enchantLevelDraft); } catch (NumberFormatException e) { status = "Enchantment level must be 1–255."; return; }
			if (level < 1 || level > 255) { status = "Enchantment level must be 1–255."; return; }
			levels.put(id, level);
		}
		writeSelected(new Kit.KitItem(item.id(), item.count(), levels.isEmpty() ? null : levels, item.potion(), item.components()));
		status = remove ? "Enchantment removed from draft." : "Enchantment updated in draft.";
	}

	private boolean enchantmentExists(Identifier id) {
		if (minecraft.level == null) return false;
		return minecraft.level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
			.get(ResourceKey.create(Registries.ENCHANTMENT, id)).isPresent();
	}

	private void removeSelected() {
		if (draft == null) return;
		if (selectedTarget.equals("inventory")) {
			Map<String, Kit.KitItem> slots = new LinkedHashMap<>(draft.slots() == null ? Map.of() : draft.slots());
			slots.remove(Integer.toString(selectedSlot)); draft = copy(draft.id(), draft.displayName(), slots, draft.provenance());
		} else writeSelected(null);
	}

	private void writeSelected(Kit.KitItem item) {
		if (selectedTarget.equals("inventory")) {
			Map<String, Kit.KitItem> slots = new LinkedHashMap<>(draft.slots() == null ? Map.of() : draft.slots());
			if (item == null) slots.remove(Integer.toString(selectedSlot)); else slots.put(Integer.toString(selectedSlot), item);
			draft = copy(draft.id(), draft.displayName(), slots, draft.provenance()); return;
		}
		Kit.Armor a = draft.armor() == null ? new Kit.Armor(null, null, null, null) : draft.armor();
		switch (selectedTarget) {
			case "offhand" -> draft = new Kit(draft.id(), draft.displayName(), draft.mode(), draft.provenance(), a, item, draft.slots(), draft.effects());
			case "armor_head" -> draft = new Kit(draft.id(), draft.displayName(), draft.mode(), draft.provenance(), new Kit.Armor(item, a.chest(), a.legs(), a.feet()), draft.offhand(), draft.slots(), draft.effects());
			case "armor_chest" -> draft = new Kit(draft.id(), draft.displayName(), draft.mode(), draft.provenance(), new Kit.Armor(a.head(), item, a.legs(), a.feet()), draft.offhand(), draft.slots(), draft.effects());
			case "armor_legs" -> draft = new Kit(draft.id(), draft.displayName(), draft.mode(), draft.provenance(), new Kit.Armor(a.head(), a.chest(), item, a.feet()), draft.offhand(), draft.slots(), draft.effects());
			default -> draft = new Kit(draft.id(), draft.displayName(), draft.mode(), draft.provenance(), new Kit.Armor(a.head(), a.chest(), a.legs(), item), draft.offhand(), draft.slots(), draft.effects());
		}
	}

	private void save() {
		if (draft == null) { status = "No valid draft to save."; return; }
		storeFields();
		Kit.Provenance source = draft.provenance();
		Kit.Provenance provenance = new Kit.Provenance("user-supplied", null, draft.mode(), source.minecraftVersion(), "low", false,
			"Edited from " + draft.id() + " in the in-game kit editor; unverified user draft.", source.notes(), source.deviations());
		draft = new Kit(idDraft.trim().toLowerCase(Locale.ROOT).replace(' ', '_'), nameDraft.trim(), draft.mode(), provenance,
			draft.armor(), draft.offhand(), draft.slots(), draft.effects());
		ClientPlayNetworking.send(new KitEditorPayloads.Save(Kits.toJson(draft)));
		status = "Saving custom kit…";
	}

	private Kit copy(String id, String name, Map<String, Kit.KitItem> slots, Kit.Provenance provenance) {
		return new Kit(id, name, draft.mode(), provenance, draft.armor(), draft.offhand(), slots, draft.effects());
	}

	private String targetLabel() {
		return switch (selectedTarget) { case "inventory" -> "slot " + selectedSlot; case "armor_head" -> "head armor";
			case "armor_chest" -> "chest armor"; case "armor_legs" -> "leg armor"; case "armor_feet" -> "foot armor"; default -> "offhand"; };
	}
	private static String shortId(String id) { int colon = id.indexOf(':'); return colon < 0 ? id : id.substring(colon + 1); }
	private static String specDescription(Kit.KitItem item) {
		if (item == null) return "empty";
		String enchantments = item.enchantments() == null || item.enchantments().isEmpty() ? "" : " · Enchantments: " + item.enchantments();
		return item.id() + " ×" + item.countOrDefault() + enchantments;
	}

	private void drawSpecIcon(GuiGraphicsExtractor graphics, Kit.KitItem spec, int x, int y, String tooltip, int mouseX, int mouseY) {
		if (spec == null) return;
		Identifier id = Identifier.tryParse(spec.id());
		if (id == null) return;
		BuiltInRegistries.ITEM.get(id).ifPresent(holder -> {
			ItemStack stack = new ItemStack(holder.value(), Math.max(1, spec.countOrDefault()));
			graphics.item(stack, x, y); graphics.itemDecorations(font, stack, x, y);
			if (mouseX >= x - 2 && mouseY >= y - 2 && mouseX < x + 20 && mouseY < y + 20)
				graphics.setTooltipForNextFrame(Component.literal(tooltip), mouseX, mouseY);
		});
	}

	private void graphicsCell(GuiGraphicsExtractor graphics, int x, int y, int w, int h, boolean selected) {
		graphics.fill(x, y, x + w, y + h, selected ? SELECTED : CELL);
		graphics.outline(x, y, x + w, y + h, selected ? 0xFF8DE7D6 : 0xFF536473);
	}
	private void graphicsText(GuiGraphicsExtractor graphics, String text, int x, int y, int color) { graphics.text(font, text, x, y, color); }

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(0, 0, width, height, PANEL);
		if (page == 0) {
			graphics.text(font, "Kit editor · " + (draft == null ? "invalid draft" : draft.displayName()), 12, 31, 0xFFF2F5F7);
			String help = width < 400 ? "Choose a slot. Your inventory stays untouched." : "Choose an inventory or equipment slot. Your live inventory stays untouched.";
			graphics.text(font, font.plainSubstrByWidth(help, width - 24), 12, 43, 0xFFB5C4D0);
			drawLayout(graphics, mouseX, mouseY);
			if (width >= 520) graphics.text(font, "Equipment", width - 5 * 49 - 8, 36, 0xFFE7EEF6);
		} else if (page == 1) {
			drawPalette(graphics, mouseX, mouseY);
		} else {
			Kit.KitItem item = selectedItem();
			graphics.text(font, "Copy preset into a new custom kit", 12, 32, 0xFFE7EEF6);
			graphics.text(font, "New kit ID", 12, 43, 0xFFB5C4D0);
			graphics.text(font, "Display name", width < 430 ? 12 : 24 + (width - 36) / 2, width < 430 ? 70 : 43, 0xFFB5C4D0);
			graphics.text(font, "Count", 12, width < 430 ? 97 : 73, 0xFFB5C4D0);
			String enchants = item == null || item.enchantments() == null || item.enchantments().isEmpty()
				? "Enchantments: none" : "Enchantments: " + item.enchantments();
			graphics.text(font, font.plainSubstrByWidth(enchants, width < 430 ? width - 24 : width - 342), width < 430 ? 12 : 330, width < 430 ? 126 : 84, 0xFFB5C4D0);
			graphics.text(font, "Enchantment ID", 12, width < 430 ? 137 : 110, 0xFFB5C4D0);
			graphics.text(font, "Level", width - 88, width < 430 ? 137 : 110, 0xFFB5C4D0);
		}
		String visibleStatus = status == null ? "" : font.plainSubstrByWidth(status.replace('\n', ' '), width - 24);
		graphics.text(font, visibleStatus, 12, height - 13, 0xFFFFD277);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	private List<String> paletteMatches() {
		return BuiltInRegistries.ITEM.keySet().stream().map(Identifier::toString)
			.filter(id -> id.toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).sorted().toList();
	}
	private void drawPalette(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		List<String> matches = paletteMatches(); int columns = width < 400 ? 2 : 4, size = pageSize();
		int gap = 3, cellW = (width - 24 - (columns - 1) * gap) / columns, top = 62;
		for (int i = 0; i < size && paletteScroll + i < matches.size(); i++) {
			int x = 12 + (i % columns) * (cellW + gap), y = top + (i / columns) * 25;
			String itemId = matches.get(paletteScroll + i);
			Button access = i < paletteAccessButtons.size() ? paletteAccessButtons.get(i) : null;
			graphicsCell(graphics, x, y, cellW, 23, itemId.equals(selectedPaletteId) || access != null && access.isFocused());
			Identifier itemKey = Identifier.tryParse(itemId);
			if (itemKey != null) BuiltInRegistries.ITEM.get(itemKey).ifPresent(holder -> graphics.item(new ItemStack(holder.value()), x + 2, y + 3));
			String label = shortId(itemId); if (font.width(label) > cellW - 24) label = font.plainSubstrByWidth(label, cellW - 26) + "…";
			graphics.text(font, label, x + 21, y + 8, 0xFFE7EEF6);
			if (mouseX >= x && mouseY >= y && mouseX < x + cellW && mouseY < y + 23)
				graphics.setTooltipForNextFrame(Component.literal(itemId), mouseX, mouseY);
		}
	}

	@Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) { return super.mouseClicked(event, doubleClick); }

	@Override public void onClose() { minecraft.gui.setScreen(null); }
	@Override public boolean isPauseScreen() { return false; }
}
