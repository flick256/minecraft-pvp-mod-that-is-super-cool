package io.github.flick256.sparbot.client;

import io.github.flick256.sparbot.core.study.StudyQuestion;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** A one-question study session with explicit, learner-owned self-review. */
public final class StudyScreen extends Screen {
	private static final int PANEL = 0xFF1C2937, PANEL_ALT = 0xFF202F3E;
	private static final int TEXT = 0xFFE7EEF6, MUTED = 0xFF9BAEC3, ACCENT = 0xFF73DDCA, GOLD = 0xFFE8BC73;
	private static final Pattern NUMBER = Pattern.compile("^[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?$");
	private enum Page { QUESTION, SUBJECTS, FEEDBACK }
	private enum ReviewKind { NONE, MARKED, SELF_REVIEW }
	private record Label(String text, int x, int y, int color) { }
	private record Card(int y, int height, boolean selected, boolean featured) { }
	public record LayoutRect(String kind, String text, int x, int y, int width, int height) { }

	private final List<Label> labels = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();
	private final List<Button> optionButtons = new ArrayList<>();
	private Page page;
	private StudyQuestion shownQuestion;
	private StudyQuestion feedbackQuestion;
	private Set<String> subjectSelection = new LinkedHashSet<>();
	private final Map<String, String> examDrafts = new LinkedHashMap<>();
	private String answerDraft = "";
	private int selectedChoice = -1, shownHints, scroll, totalHeight, cursorY;
	private boolean failedAttempt, solutionShown, selfReviewRevealed, selfReviewRecorded;
	private ReviewKind reviewKind = ReviewKind.NONE;
	private boolean selfReviewMatched;
	private String notice = "";
	private EditBox answerField;
	private MultiLineEditBox selfReviewField;
	private Button revealButton;
	private int bodyLeft, bodyWidth, bodyTop, bodyBottom, footerY;

	public StudyScreen() {
		super(Component.literal("Study"));
		shownQuestion = StudyClient.question();
		page = shownQuestion == null ? Page.SUBJECTS : Page.QUESTION;
		subjectSelection = new LinkedHashSet<>(StudyClient.selectedSubjects());
		if (subjectSelection.isEmpty()) subjectSelection.addAll(StudyClient.bank().subjects());
		for (String subject : StudyClient.bank().subjects()) examDrafts.put(subject, StudyClient.examDate(subject));
	}

	@Override protected void init() { rebuildPage(); }

	private void rebuildPage() {
		clearWidgets(); labels.clear(); cards.clear(); optionButtons.clear(); answerField = null; selfReviewField = null; revealButton = null;
		bodyLeft = 12;
		bodyWidth = Math.max(100, width - 24);
		bodyTop = 58;
		footerY = height - 32;
		bodyBottom = footerY - 22;
		cursorY = bodyTop - scroll;
		if (page == Page.SUBJECTS) buildSubjects();
		else if (page == Page.FEEDBACK) buildFeedback();
		else buildQuestion();
		totalHeight = cursorY - (bodyTop - scroll);
		int max = maxScroll();
		if (scroll > max) { scroll = max; rebuildPage(); return; }
		buildFooter();
	}

	private void buildSubjects() {
		section("CHOOSE YOUR SUBJECTS");
		wrap("Select the subjects to include in practice. You can change this list later.", 0, cursorY, bodyWidth, MUTED);
		cursorY += wrappedHeight("Select the subjects to include in practice. You can change this list later.", bodyWidth) + 8;
		for (String subject : StudyClient.bank().subjects()) {
			boolean selected = subjectSelection.contains(subject);
			int y = card(52, selected, false);
			labels.add(new Label(font.plainSubstrByWidth(subject, Math.max(12, bodyWidth - 120)), bodyLeft + 10, y + 9, TEXT));
			String date = examDrafts.getOrDefault(subject, "");
			String dateLabel = date.isBlank() ? "Exam date optional" : "Exam: " + date;
			labels.add(new Label(font.plainSubstrByWidth(dateLabel, Math.max(12, bodyWidth - 120)), bodyLeft + 10, y + 27, MUTED));
			Button toggle = addButton(new MenuButton(selected ? "Selected" : "Add", bodyLeft + bodyWidth - 88, y + 14, 76, 22,
				selected ? MenuButton.Tone.PRIMARY : MenuButton.Tone.NORMAL, selected, b -> {
					if (subjectSelection.contains(subject)) subjectSelection.remove(subject); else subjectSelection.add(subject);
					rebuildPage();
				}));
			toggle.visible = y >= bodyTop && y + 52 <= bodyBottom;
			toggle.setTooltip(Tooltip.create(Component.literal("Include " + subject + " in study practice")));
			cursorY += 8;
			EditBox dateField = new EditBox(font, bodyLeft + 10, cursorY, Math.min(180, bodyWidth - 20), 20, Component.literal("Exam date (YYYY-MM-DD)"));
			dateField.setHint(Component.literal("YYYY-MM-DD · optional"));
			dateField.setMaxLength(10); dateField.setValue(date);
			dateField.setResponder(value -> examDrafts.put(subject, value));
			addRenderableWidget(dateField);
			dateField.visible = dateField.getY() >= bodyTop && dateField.getBottom() <= bodyBottom;
			cards.add(new Card(cursorY - 2, 24, false, false));
			cursorY += 32;
		}
		int columns = bodyWidth >= 350 ? 2 : 1;
		int buttonWidth = columns == 2 ? (bodyWidth - 6) / 2 : bodyWidth;
		button("Select all", 0, cursorY, buttonWidth, MenuButton.Tone.NORMAL,
			() -> { subjectSelection.addAll(StudyClient.bank().subjects()); rebuildPage(); });
		if (columns == 2) button("Clear selection", buttonWidth + 6, cursorY, buttonWidth, MenuButton.Tone.NORMAL,
			() -> { subjectSelection.clear(); rebuildPage(); });
		cursorY += 30;
	}

	private void buildQuestion() {
		shownQuestion = StudyClient.question();
		if (shownQuestion == null) {
			section("STUDY IS NOT READY");
			wrap(StudyClient.status().isBlank() ? "Choose at least one subject to begin." : StudyClient.status(), 0, cursorY, bodyWidth, MUTED);
			return;
		}
		section(shownQuestion.subject().toUpperCase() + "  /  " + shownQuestion.topic());
		if (!StudyClient.reason().isBlank()) {
			int n = wrappedHeight(StudyClient.reason(), bodyWidth);
			int y = card(n + 18, false, true);
			wrap(StudyClient.reason(), 10, y + 9, bodyWidth - 20, GOLD);
			cursorY += 8;
		}
		int promptHeight = wrappedHeight(shownQuestion.prompt(), bodyWidth - 20);
		int promptY = card(promptHeight + 22, false, false);
		wrap(shownQuestion.prompt(), 10, promptY + 10, bodyWidth - 20, TEXT);
		cursorY += 8;
		if (shownQuestion.kind() == StudyQuestion.Kind.CHOICE) buildChoices();
		else if (shownQuestion.kind() == StudyQuestion.Kind.NUMBER) buildNumberAnswer();
		else buildSelfReviewAnswer();
		if (failedAttempt) {
			int msgHeight = wrappedHeight("That response does not match the expected answer. Try again, reveal another hint, or view the worked explanation.", bodyWidth - 20);
			int y = card(msgHeight + 18, false, true);
			wrap("That response does not match the expected answer. Try again, reveal another hint, or view the worked explanation.", 10, y + 9, bodyWidth - 20, 0xFFEEA09A);
			cursorY += 8;
		}
		for (int i = 0; i < shownHints; i++) {
			String hint = shownQuestion.hints().get(i);
			int h = wrappedHeight(hint, bodyWidth - 20);
			int y = card(h + 34, false, false);
			text("HINT " + (i + 1), 10, y + 7, ACCENT);
			wrap(hint, 10, y + 20, bodyWidth - 20, TEXT);
			cursorY += 8;
		}
		if (solutionShown) {
			int h = wrappedHeight(shownQuestion.explanation(), bodyWidth - 20);
			int y = card(h + 34, false, true);
			text("WORKED EXPLANATION", 10, y + 7, GOLD);
			wrap(shownQuestion.explanation(), 10, y + 20, bodyWidth - 20, TEXT);
			cursorY += 8;
		}
		if (shownHints < shownQuestion.hints().size()) {
			button("Show hint " + (shownHints + 1), 0, cursorY, Math.min(150, bodyWidth), MenuButton.Tone.NORMAL, () -> {
				shownHints++; rebuildPage();
			});
			cursorY += 30;
		}
		if (!solutionShown) {
			button("Show worked explanation", 0, cursorY, Math.min(190, bodyWidth), MenuButton.Tone.NORMAL, () -> {
				solutionShown = true; rebuildPage();
			});
			cursorY += 30;
		}
		String source = shownQuestion.source().isBlank()
			? "Original VCE-style practice item by SparBot. Not official VCAA material."
			: "Source: " + shownQuestion.source();
		int sourceHeight = wrappedHeight(source, bodyWidth);
		wrap(source, 0, cursorY, bodyWidth, MUTED);
		cursorY += sourceHeight + 10;
	}

	private void buildChoices() {
		for (int i = 0; i < shownQuestion.options().size(); i++) {
			final int index = i;
			String option = shownQuestion.options().get(i);
			int h = wrappedHeight(option, bodyWidth - 40);
			int itemHeight = Math.max(38, h + 16);
			int y = card(itemHeight, selectedChoice == i, false);
			text((char) ('A' + i) + ".", 10, y + 10, ACCENT);
			wrap(option, 30, y + 10, bodyWidth - 42, TEXT);
			Button access = Button.builder(Component.literal("Option " + (char) ('A' + i) + ": " + option), b -> {
				selectedChoice = index; rebuildPage();
			}).bounds(bodyLeft, y, bodyWidth, itemHeight).build();
			access.setAlpha(0); access.setTooltip(Tooltip.create(Component.literal(option)));
			addRenderableWidget(access); access.visible = y >= bodyTop && y + itemHeight <= bodyBottom;
			optionButtons.add(access);
			cursorY += 6;
		}
	}

	private void buildNumberAnswer() {
		text("Your answer" + (shownQuestion.unit().isBlank() ? "" : " · " + shownQuestion.unit()), 0, cursorY, MUTED);
		cursorY += 13;
		answerField = new EditBox(font, bodyLeft + 8, cursorY, Math.min(240, bodyWidth - 16), 20, Component.literal("Answer"));
		answerField.setHint(Component.literal("Enter a number")); answerField.setMaxLength(64); answerField.setValue(answerDraft);
		answerField.setResponder(value -> answerDraft = value); addRenderableWidget(answerField);
		answerField.visible = answerField.getY() >= bodyTop && answerField.getBottom() <= bodyBottom;
		cards.add(new Card(cursorY - 3, 26, false, false));
		cursorY += 34;
	}

	private void buildSelfReviewAnswer() {
		text("Write at least three words before you reveal the rubric.", 0, cursorY, MUTED);
		cursorY += 17;
		int fieldHeight = Math.min(78, Math.max(56, bodyBottom - bodyTop - 8));
		selfReviewField = MultiLineEditBox.builder()
			.setX(bodyLeft + 8).setY(cursorY).setPlaceholder(Component.literal("Your written attempt"))
			.setTextColor(TEXT).setCursorColor(ACCENT).setShowBackground(false).setShowDecorations(true)
			.build(font, bodyWidth - 16, fieldHeight, Component.literal("Written attempt"));
		selfReviewField.setCharacterLimit(1500); selfReviewField.setLineLimit(8); selfReviewField.setValue(answerDraft);
		selfReviewField.setValueListener(value -> {
			answerDraft = value;
			if (revealButton != null) revealButton.active = answerWordCount() >= 3;
		});
		addRenderableWidget(selfReviewField);
		selfReviewField.visible = selfReviewField.getY() >= bodyTop && selfReviewField.getY() + fieldHeight <= bodyBottom;
		cards.add(new Card(cursorY - 3, fieldHeight + 6, false, false));
		cursorY += fieldHeight + 14;
		if (answerWordCount() < 3) {
			text("Add a little more detail to continue.", 0, cursorY, MUTED);
			cursorY += 16;
		}
	}

	private void buildFeedback() {
		if (feedbackQuestion == null) return;
		section(reviewKind == ReviewKind.SELF_REVIEW
			? selfReviewRecorded ? "SELF-REVIEW RECORDED" : "SELF-REVIEW / CHECK YOUR WORK"
			: "ANSWER REVIEW");
		String title;
		int color;
		if (reviewKind == ReviewKind.SELF_REVIEW) {
			title = selfReviewRecorded
				? selfReviewMatched ? "You marked the response as meeting the criteria." : "You marked this response as needing more work."
				: "Compare your attempt with the reference answer and criteria, then assess it below.";
			color = selfReviewMatched && selfReviewRecorded ? ACCENT : GOLD;
		} else { title = "Correct. Take a moment to review the reasoning."; color = ACCENT; }
		int th = wrappedHeight(title, bodyWidth - 20);
		int y = card(th + 18, false, true);
		wrap(title, 10, y + 9, bodyWidth - 20, color);
		cursorY += 8;
		if (reviewKind == ReviewKind.MARKED) {
			String response = markedAnswerSummary();
			int responseHeight = wrappedHeight(response, bodyWidth - 20);
			int responseY = card(responseHeight + 18, false, false);
			wrap(response, 10, responseY + 9, bodyWidth - 20, TEXT);
			cursorY += 8;
		} else if (!answerDraft.isBlank()) {
			String response = "Your written attempt: " + answerDraft;
			int responseHeight = wrappedHeight(response, bodyWidth - 20);
			int responseY = card(responseHeight + 18, false, false);
			wrap(response, 10, responseY + 9, bodyWidth - 20, TEXT);
			cursorY += 8;
		}
		String heading = reviewKind == ReviewKind.SELF_REVIEW ? "REFERENCE ANSWER / EXPLANATION" : "WHY THIS WORKS";
		text(heading, 10, cursorY, GOLD); cursorY += 16;
		String explanation = feedbackQuestion.explanation();
		int h = wrappedHeight(explanation, bodyWidth - 20);
		int detailY = card(h + 18, false, false);
		wrap(explanation, 10, detailY + 9, bodyWidth - 20, TEXT);
		cursorY += 8;
		if (reviewKind == ReviewKind.SELF_REVIEW) {
			text("ASSESS AGAINST THESE CRITERIA", 10, cursorY, GOLD); cursorY += 15;
			for (String criterion : feedbackQuestion.rubric()) {
				int rh = wrappedHeight(criterion, bodyWidth - 20);
				int ry = card(rh + 16, false, false);
				wrap("• " + criterion, 10, ry + 8, bodyWidth - 20, TEXT);
				cursorY += 7;
			}
		}
		String source = feedbackQuestion.source().isBlank()
			? "Original VCE-style practice item by SparBot. Not official VCAA material."
			: "Source: " + feedbackQuestion.source();
		wrap(source, 0, cursorY, bodyWidth, MUTED);
		cursorY += wrappedHeight(source, bodyWidth) + 10;
	}

	private String markedAnswerSummary() {
		if (feedbackQuestion.kind() == StudyQuestion.Kind.CHOICE) {
			int selected = selectedChoice;
			int correct = feedbackQuestion.answerIndex();
			return "Your answer: " + (char) ('A' + selected) + ". " + feedbackQuestion.options().get(selected)
				+ "  ·  Correct answer: " + (char) ('A' + correct) + ". " + feedbackQuestion.options().get(correct);
		}
		String unit = feedbackQuestion.unit().isBlank() ? "" : " " + feedbackQuestion.unit();
		return "Your answer: " + answerDraft + unit + "  ·  Expected: " + feedbackQuestion.numericAnswer() + unit
			+ " (accepted tolerance ±" + feedbackQuestion.tolerance() + unit + ").";
	}

	private void buildFooter() {
		int y = footerY;
		if (page == Page.SUBJECTS) {
			int returnWidth = 78;
			int saveWidth = Math.min(150, width - 24 - returnWidth - 6);
			Button save = addButton(new MenuButton("Save & continue", 12, y, saveWidth, 22, MenuButton.Tone.PRIMARY, false, b -> saveSubjects()));
			save.active = !subjectSelection.isEmpty();
			addButton(new MenuButton("Return", width - returnWidth - 12, y, returnWidth, 22, MenuButton.Tone.NORMAL, false, b -> onClose()));
		} else if (page == Page.FEEDBACK) {
			if (reviewKind == ReviewKind.SELF_REVIEW && !selfReviewRecorded) {
				int third = (width - 48) / 3;
				Button work = addButton(new MenuButton("Needs work", 12, y, third, 22, MenuButton.Tone.NORMAL, false, b -> recordSelfReview(false)));
				Button met = addButton(new MenuButton("Met criteria", 18 + third, y, third, 22, MenuButton.Tone.GOLD, false, b -> recordSelfReview(true)));
				work.setTooltip(Tooltip.create(Component.literal("I could not meet most of the shown criteria yet.")));
				met.setTooltip(Tooltip.create(Component.literal("I addressed most of the shown criteria in my attempt.")));
				addButton(new MenuButton("Return", 24 + third * 2, y, third, 22, MenuButton.Tone.NORMAL, false, b -> onClose()));
			} else {
				addButton(new MenuButton("Continue", 12, y, Math.min(150, width - 100), 22, MenuButton.Tone.PRIMARY, false, b -> StudyClient.finish()));
				addButton(new MenuButton("Return", width - 82, y, 70, 22, MenuButton.Tone.NORMAL, false, b -> onClose()));
			}
		} else {
			int returnWidth = 70, subjectsWidth = 68, gap = 6;
			int primaryWidth = Math.max(48, Math.min(130, width - 24 - returnWidth - subjectsWidth - 2 * gap));
			if (shownQuestion != null && shownQuestion.kind() == StudyQuestion.Kind.SELF_REVIEW) {
				revealButton = addButton(new MenuButton("Reveal rubric", 12, y, primaryWidth, 22, MenuButton.Tone.GOLD, false, b -> revealSelfReview()));
				revealButton.active = answerWordCount() >= 3;
			} else {
				addButton(new MenuButton("Check answer", 12, y, primaryWidth, 22, MenuButton.Tone.PRIMARY, false, b -> checkAnswer()));
			}
			int subjectsX = 12 + primaryWidth + gap;
			addButton(new MenuButton("Subjects", subjectsX, y, subjectsWidth, 22, MenuButton.Tone.NORMAL, false, b -> openSubjects()));
			addButton(new MenuButton("Return", subjectsX + subjectsWidth + gap, y, returnWidth, 22, MenuButton.Tone.NORMAL, false, b -> onClose()));
		}
	}

	private void saveSubjects() {
		if (subjectSelection.isEmpty()) { notice = "Choose at least one subject."; return; }
		for (Map.Entry<String, String> entry : examDrafts.entrySet()) {
			String value = entry.getValue().trim();
			if (!value.isEmpty()) {
				try { LocalDate.parse(value); }
				catch (DateTimeParseException e) { notice = "Use YYYY-MM-DD for exam dates."; return; }
			}
		}
		for (Map.Entry<String, String> entry : examDrafts.entrySet()) StudyClient.configureExamDate(entry.getKey(), entry.getValue().trim());
		StudyClient.configureSubjects(Set.copyOf(subjectSelection));
		shownQuestion = StudyClient.question();
		if (shownQuestion == null) { notice = StudyClient.status(); rebuildPage(); return; }
		answerDraft = ""; selectedChoice = -1; shownHints = 0; failedAttempt = false; solutionShown = false;
		feedbackQuestion = null; reviewKind = ReviewKind.NONE; selfReviewRevealed = false; selfReviewRecorded = false;
		page = Page.QUESTION; scroll = 0; notice = ""; rebuildPage();
	}

	private void openSubjects() {
		subjectSelection = new LinkedHashSet<>(StudyClient.selectedSubjects());
		for (String subject : StudyClient.bank().subjects()) examDrafts.put(subject, StudyClient.examDate(subject));
		page = Page.SUBJECTS; scroll = 0; rebuildPage();
	}

	private void checkAnswer() {
		if (shownQuestion == null) return;
		boolean correct;
		if (shownQuestion.kind() == StudyQuestion.Kind.CHOICE) {
			if (selectedChoice < 0) { notice = "Choose an option first."; rebuildPage(); return; }
			correct = shownQuestion.checkChoice(selectedChoice);
		} else if (shownQuestion.kind() == StudyQuestion.Kind.NUMBER) {
			String response = answerDraft.trim();
			if (response.isEmpty() || !NUMBER.matcher(response).matches()) { notice = "Enter a signed or decimal number to check."; rebuildPage(); return; }
			try {
				if (!Double.isFinite(Double.parseDouble(response))) { notice = "Enter a finite number to check."; rebuildPage(); return; }
			} catch (NumberFormatException e) { notice = "Enter a valid number to check."; rebuildPage(); return; }
			correct = shownQuestion.checkNumber(response);
		}
		else return;
		boolean assisted = shownHints > 0 || solutionShown || failedAttempt;
		StudyClient.recordAnswer(correct, assisted);
		feedbackQuestion = shownQuestion;
		if (correct) {
			reviewKind = ReviewKind.MARKED; page = Page.FEEDBACK; scroll = 0; notice = "";
		} else {
			failedAttempt = true; notice = "Not quite. Try again, use a hint, or reveal the worked explanation.";
		}
		rebuildPage();
	}

	private void revealSelfReview() {
		if (answerWordCount() < 3) { notice = "Write at least three words before reviewing."; return; }
		feedbackQuestion = shownQuestion;
		selfReviewRevealed = true;
		selfReviewRecorded = false;
		reviewKind = ReviewKind.SELF_REVIEW;
		page = Page.FEEDBACK; scroll = 0; rebuildPage();
	}

	private void recordSelfReview(boolean matched) {
		selfReviewMatched = matched;
		StudyClient.recordAnswer(matched, true);
		selfReviewRecorded = true;
		rebuildPage();
	}

	private int answerWordCount() {
		String value = answerDraft.trim();
		return value.isEmpty() ? 0 : value.split("\\s+").length;
	}

	/** Rendered labels and visible widgets for client layout checks at compact GUI scales. */
	public List<LayoutRect> layoutSnapshot() {
		List<LayoutRect> result = new ArrayList<>();
		String heading = page == Page.SUBJECTS ? "STUDY / SUBJECTS" : page == Page.FEEDBACK ? "STUDY / REVIEW" : "STUDY / PRACTICE";
		String sub = shownQuestion == null ? "Choose a subject to begin" : shownQuestion.subject() + " · " + shownQuestion.topic() + " · " + shownQuestion.marks() + " marks";
		addTextRect(result, "header", heading, 22, 12);
		addTextRect(result, "header", font.plainSubstrByWidth(sub, width - 34), 22, 26);
		for (Label label : labels) {
			int h = font.lineHeight;
			if (label.y() >= bodyTop && label.y() + h <= bodyBottom && label.x() >= bodyLeft && label.x() + font.width(label.text()) <= bodyLeft + bodyWidth) {
				addTextRect(result, "label", label.text(), label.x(), label.y());
			}
		}
		for (var child : children()) {
			if (!(child instanceof AbstractWidget widget) || !widget.visible) continue;
			String kind = optionButtons.contains(widget) ? "hitbox" : "widget";
			result.add(new LayoutRect(kind, widget.getMessage().getString(), widget.getX(), widget.getY(), widget.getWidth(), widget.getHeight()));
		}
		String status = !notice.isBlank() ? notice : StudyClient.status();
		if (status != null && !status.isBlank()) addTextRect(result, "status", font.plainSubstrByWidth(status, width - 24), 12, footerY - 15);
		return List.copyOf(result);
	}

	private void addTextRect(List<LayoutRect> target, String kind, String text, int x, int y) {
		target.add(new LayoutRect(kind, text, x, y, font.width(text), font.lineHeight));
	}

	private Button button(String name, int dx, int y, int w, MenuButton.Tone tone, Runnable action) {
		Button button = addButton(new MenuButton(name, bodyLeft + dx, y, Math.max(24, w), 22, tone, false, b -> action.run()));
		button.visible = button.getY() >= bodyTop && button.getBottom() <= bodyBottom;
		return button;
	}

	private Button addButton(Button button) { addRenderableWidget(button); return button; }
	private int card(int height, boolean selected, boolean featured) {
		int y = cursorY; cards.add(new Card(y, height, selected, featured)); cursorY += height + 8; return y;
	}
	private void section(String value) { text(value, 0, cursorY, GOLD); cursorY += 20; }
	private void text(String value, int dx, int y, int color) {
		labels.add(new Label(font.plainSubstrByWidth(value, Math.max(12, bodyWidth - dx - 10)), bodyLeft + dx, y, color));
	}
	private void wrap(String value, int dx, int y, int width, int color) {
		for (var line : font.getSplitter().splitLines(value, Math.max(12, width), net.minecraft.network.chat.Style.EMPTY)) {
			labels.add(new Label(line.getString(), bodyLeft + dx, y, color)); y += 12;
		}
	}
	private int wrappedHeight(String value, int width) {
		return Math.max(12, font.getSplitter().splitLines(value, Math.max(12, width), net.minecraft.network.chat.Style.EMPTY).size() * 12);
	}
	private int maxScroll() { return Math.max(0, totalHeight - (bodyBottom - bodyTop)); }
	private void moveScroll(int delta) {
		int next = Math.max(0, Math.min(maxScroll(), scroll + delta));
		if (next != scroll) { scroll = next; rebuildPage(); }
	}

	@Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
		if (selfReviewField != null && selfReviewField.visible && selfReviewField.isMouseOver(x, y)
			&& selfReviewField.mouseScrolled(x, y, horizontal, vertical)) return true;
		if (x >= bodyLeft && x < bodyLeft + bodyWidth && y >= bodyTop && y < bodyBottom) {
			moveScroll((int) Math.round(-vertical * 24)); return true;
		}
		return super.mouseScrolled(x, y, horizontal, vertical);
	}

	@Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		graphics.fillGradient(0, 0, width, height, 0xF6101B2A, 0xF5080E19);
		graphics.fill(0, 0, width, 42, 0xFF182C38);
		graphics.fill(12, 13, 14, 30, ACCENT);
		String heading = page == Page.SUBJECTS ? "STUDY / SUBJECTS" : page == Page.FEEDBACK ? "STUDY / REVIEW" : "STUDY / PRACTICE";
		graphics.text(font, heading, 22, 12, TEXT, false);
		String sub = shownQuestion == null ? "Choose a subject to begin" : shownQuestion.subject() + " · " + shownQuestion.topic() + " · " + shownQuestion.marks() + " marks";
		graphics.text(font, font.plainSubstrByWidth(sub, width - 34), 22, 26, MUTED, false);
		graphics.fill(bodyLeft - 2, bodyTop - 8, bodyLeft + bodyWidth + 2, bodyBottom + 2, 0xDD111A28);
		graphics.enableScissor(bodyLeft - 1, bodyTop, bodyLeft + bodyWidth + 1, bodyBottom);
		for (Card card : cards) {
			int fill = card.featured() ? 0xFF373027 : card.selected() ? 0xFF263F43 : PANEL;
			graphics.fillGradient(bodyLeft, card.y(), bodyLeft + bodyWidth, card.y() + card.height(), fill, card.featured() ? 0xFF252421 : PANEL_ALT);
			graphics.outline(bodyLeft, card.y(), bodyWidth, card.height(), card.featured() ? GOLD : card.selected() ? ACCENT : 0xFF34485A);
			if (card.featured()) graphics.fill(bodyLeft, card.y() + 1, bodyLeft + 2, card.y() + card.height() - 1, GOLD);
		}
		for (Button option : optionButtons) {
			if (option.isFocused()) graphics.outline(option.getX(), option.getY(), option.getWidth(), option.getHeight(), ACCENT);
		}
		for (Label label : labels) graphics.text(font, label.text(), label.x(), label.y(), label.color(), false);
		graphics.disableScissor();
		if (maxScroll() > 0) {
			int track = bodyBottom - bodyTop;
			int thumb = Math.max(18, track * track / Math.max(1, totalHeight));
			int thumbY = bodyTop + (track - thumb) * scroll / maxScroll();
			graphics.fill(width - 6, bodyTop, width - 4, bodyBottom, 0xFF253344);
			graphics.fill(width - 6, thumbY, width - 4, thumbY + thumb, ACCENT);
		}
		String status = !notice.isBlank() ? notice : StudyClient.status();
		if (status != null && !status.isBlank()) graphics.text(font, font.plainSubstrByWidth(status, width - 24), 12, footerY - 15, GOLD, false);
		graphics.fill(0, footerY - 4, width, height, 0xFF111A28);
		graphics.horizontalLine(0, width, footerY - 4, 0xFF34485A);
	}

	@Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override public void onClose() { StudyClient.cancel(); }
	@Override public boolean isPauseScreen() { return true; }
}
