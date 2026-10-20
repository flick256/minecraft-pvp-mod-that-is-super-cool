package io.github.flick256.sparbot.client;

import com.google.gson.Gson;
import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.core.study.StudyBank;
import io.github.flick256.sparbot.core.study.StudyGate;
import io.github.flick256.sparbot.core.study.StudyQuestion;
import io.github.flick256.sparbot.core.study.StudyProgress;
import io.github.flick256.sparbot.core.study.StudyScheduler;
import io.github.flick256.sparbot.core.study.StudyStore;
import io.github.flick256.sparbot.core.study.StudyTimetable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;

/** Personal offline revision, scoped to the current connection and kept off the server. */
@Environment(EnvType.CLIENT)
public final class StudyClient {
	private static final ZoneId EXAM_ZONE = ZoneId.of("Australia/Melbourne");
	private static final StudyGate GATE = new StudyGate();
	private static StudyBank bank;
	private static StudyStore store;
	private static ClientPacketListener connection;
	private static Screen parent;
	private static StudyQuestion question;
	private static StudyScreen sessionScreen;
	private static String lastQuestionId, status = "", reason = "";
	private static String lastTip = "English: connect each piece of evidence to your interpretation and its effect on the audience.";
	private static boolean openNextTick, replaying, questionCompleted;
	private static int joinedTicks;

	private StudyClient() { }

	public static void register() {
		load();
		StudyLoadingTips.register();
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			connection = handler;
			GATE.joined();
			parent = null; question = null; sessionScreen = null; joinedTicks = 0; openNextTick = false; questionCompleted = false;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			GATE.disconnected();
			connection = null; parent = null; question = null; sessionScreen = null; openNextTick = false; questionCompleted = false;
		});
		ClientSendMessageEvents.ALLOW_COMMAND.register(command -> {
			if (replaying || !enabled() || !StudyGate.startsActivity(command)) return true;
			Minecraft client = Minecraft.getInstance();
			if (client.getConnection() == null || client.getConnection() != connection) return true;
			if (GATE.allow(command)) return true;
			if (!(client.gui.screen() instanceof StudyScreen)) parent = client.gui.screen();
			openNextTick = true;
			return false;
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!enabled() || client.player == null || client.level == null || client.getConnection() != connection) return;
			joinedTicks++;
			if (client.gui.screen() instanceof StudyScreen) return;
			boolean stableScreen = client.gui.screen() == null || client.gui.screen() instanceof SparBotMenuScreen || client.gui.screen() instanceof ChatScreen;
			if (!stableScreen) return; // Let dimension-loading and pause/settings screens finish normally.
			if (sessionScreen != null && GATE.required()) {
				// A delayed dimension packet can clear a screen after a fast rematch click. Keep
				// the same attempt and feedback until the learner explicitly continues or cancels.
				client.gui.setScreen(sessionScreen);
				return;
			}
			boolean readyForEntry = joinedTicks >= 10 && GATE.worldRequired()
				&& (client.gui.screen() == null || client.gui.screen() instanceof SparBotMenuScreen);
			if (openNextTick || readyForEntry) {
				openNextTick = false;
				if (parent == null && client.gui.screen() instanceof SparBotMenuScreen) parent = client.gui.screen();
				begin(client);
			}
		});
	}

	private static void load() {
		var questions = new ArrayList<StudyQuestion>();
		Gson gson = new Gson();
		for (String resource : new String[] {"/study/questions.json", "/study/languages-history.json"}) {
			try (var input = StudyClient.class.getResourceAsStream(resource)) {
				if (input == null) throw new IOException("Missing " + resource);
				StudyQuestion[] loaded = gson.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), StudyQuestion[].class);
				if (loaded == null) throw new IOException("Empty " + resource);
				questions.addAll(java.util.List.of(loaded));
			} catch (IOException | IllegalArgumentException e) { throw new IllegalStateException("Cannot load bundled study questions", e); }
		}
		bank = new StudyBank(questions);
		var path = FabricLoader.getInstance().getConfigDir().resolve("sparbot").resolve("study.json");
		boolean fresh = !Files.exists(path);
		store = new StudyStore(path);
		try {
			store.load();
			if (fresh) {
				store.setSelectedSubjects(Set.copyOf(bank.subjects()));
				StudyTimetable.defaults().forEach(store::setExamDate);
				store.save();
			}
		} catch (IOException e) {
			status = "Progress could not be loaded. Existing file preserved; study works for this session.";
			SparBot.LOGGER.error("Could not load personal study progress; preserving the file", e);
		}
	}

	private static void begin(Minecraft client) {
		question = selectQuestion();
		questionCompleted = false;
		reason = GATE.pendingCommand() != null ? "One question, then your requested fight starts."
			: GATE.worldRequired() ? "Before you start. This question counts toward your first fight."
			: "Optional revision. Completing this counts toward your next fight.";
		sessionScreen = new StudyScreen();
		client.gui.setScreen(sessionScreen);
	}

	private static StudyQuestion selectQuestion() {
		var pool = bank.all().stream().filter(q -> selectedSubjects().contains(q.subject())).toList();
		Map<String, Long> exams = new LinkedHashMap<>();
		LocalDate today = LocalDate.now(EXAM_ZONE);
		store.examDates().forEach((subject, date) -> {
			LocalDate effective = StudyTimetable.effectiveDate(subject, date, today);
			// An exam stays current throughout its date; Methods automatically rolls to Exam 2.
			exams.put(subject, effective.plusDays(1).atStartOfDay(EXAM_ZONE).toInstant().toEpochMilli() - 1);
		});
		long now = System.currentTimeMillis();
		var upcoming = pool.stream().filter(q -> !exams.containsKey(q.subject()) || exams.get(q.subject()) >= now).toList();
		if (!upcoming.isEmpty()) pool = upcoming;
		return StudyScheduler.select(pool, store.allProgress(), now, lastQuestionId, exams);
	}

	public static void openOptional() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null || client.gui.screen() instanceof StudyScreen) return;
		parent = client.gui.screen();
		begin(client);
	}

	public static StudyBank bank() { return bank; }
	public static Set<String> selectedSubjects() { return store.settings().selectedSubjects(); }
	public static StudyQuestion question() { return question; }
	public static String reason() { return reason; }
	public static boolean required() { return enabled() && GATE.required(); }
	public static String status() { return status; }
	public static String loadingTip() { return lastTip; }
	public static StudyProgress progress(String id) { return store.allProgress().getOrDefault(id, new StudyProgress()); }
	public static String examDate(String subject) { return store.examDates().getOrDefault(subject, ""); }
	public static boolean enabled() { return store != null && store.settings().enabled(); }

	public static void setEnabled(boolean enabled) {
		store.setEnabled(enabled);
		if (!enabled) { GATE.disconnected(); openNextTick = false; sessionScreen = null; }
		else if (Minecraft.getInstance().player != null) GATE.joined();
		save();
	}

	public static void configureSubjects(Set<String> subjects) {
		if (!bank.subjects().containsAll(subjects)) throw new IllegalArgumentException("Choose subjects in the question bank");
		store.setSelectedSubjects(subjects);
		save();
		question = selectQuestion();
		questionCompleted = false;
	}

	public static void configureExamDate(String subject, String isoDate) { store.setExamDate(subject, isoDate); save(); }

	public static void recordAnswer(boolean correct, boolean assisted) {
		if (question == null || questionCompleted) return;
		store.progress(question.id()).record(correct, assisted, System.currentTimeMillis());
		// Written answers are self-assessed: even "needs work" completes this learning interaction.
		questionCompleted = correct || question.kind() == StudyQuestion.Kind.SELF_REVIEW;
		if (questionCompleted) {
			lastQuestionId = question.id();
			lastTip = question.subject() + ": " + question.hints().getFirst();
		}
		save();
	}

	public static void finish() {
		Minecraft client = Minecraft.getInstance();
		if (!questionCompleted || client.getConnection() == null || client.getConnection() != connection) return;
		String command = GATE.completed();
		Screen back = parent;
		parent = null; question = null; sessionScreen = null; questionCompleted = false;
		client.gui.setScreen(command == null ? back : null);
		if (command != null) {
			replaying = true;
			try { connection.sendCommand(command); }
			finally { replaying = false; }
		}
	}

	public static void cancel() {
		Minecraft client = Minecraft.getInstance();
		GATE.cancelFight();
		openNextTick = false;
		Screen back = parent;
		parent = null; question = null; sessionScreen = null; questionCompleted = false;
		client.gui.setScreen(GATE.worldRequired() ? new PauseScreen(true) : back);
	}

	private static void save() {
		try { store.save(); status = "Progress saved on this computer."; }
		catch (IOException e) { status = "Progress could not be saved. Your existing file was preserved."; SparBot.LOGGER.warn("Could not save personal study progress", e); }
	}
}
