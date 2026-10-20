package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.practice.colosseum.ColosseumSurvey;
import io.github.flick256.sparbot.client.SparBotMenuScreen;
import io.github.flick256.sparbot.client.KitEditorScreen;
import io.github.flick256.sparbot.client.StudyClient;
import io.github.flick256.sparbot.client.StudyScreen;
import io.github.flick256.sparbot.core.study.StudyQuestion;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import org.lwjgl.glfw.GLFW;

/**
 * Milestone 7: the client menu, driven like a player would in a real (headless) client: press B, spawn a
 * bot from the Spawn tab, see it in the Bots tab, change a setting and remove the bot. Screenshots of
 * each tab land in the client game test run directory.
 */
public class MenuClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		if (Boolean.getBoolean("sparbot.shutdownOnly")) {
			context.runOnClient(client -> StudyClient.setEnabled(false));
			shutdown(context);
			return;
		}
		if (Boolean.getBoolean("sparbot.layoutOnly")) {
			context.runOnClient(client -> { client.options.guiScale().set(1); StudyClient.setEnabled(false); });
			try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
				menuLayoutOnly(context);
			}
			return;
		}
		if (Boolean.getBoolean("sparbot.studyOnly")) {
			context.runOnClient(client -> {
				client.options.guiScale().set(1);
				StudyClient.setEnabled(true);
				StudyClient.configureSubjects(java.util.Set.of("Mathematical Methods"));
			});
			try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
				studyFlow(context, world);
			}
			return;
		}
		// The existing menu and practice scenarios exercise normal game flows; keep the personal study gate
		// out of their way, including before the world joins and could trigger its first-entry question.
		context.runOnClient(client -> StudyClient.setEnabled(false));
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
			world.getServer().runCommand("sparbot config set autoTarget false");
			context.waitTicks(5);
			if (Boolean.getBoolean("sparbot.drillsOnly")) {
				drills(context, world);
				return;
			}

			menuSmoke(context, world);
			if (Boolean.getBoolean("sparbot.v4Only")) {
				menuV4Smoke(context, world);
				return;
			}
			if (Boolean.getBoolean("sparbot.menuOnly")) { return; }
			context.getInput().pressKey(GLFW.GLFW_KEY_B);
			context.waitForScreen(SparBotMenuScreen.class);
			clickMenu(context, "Practice");
			clickMenu(context, "Hub");
			world.getServer().waitFor(server -> human(server) != null && human(server).level().dimension().equals(
				io.github.flick256.sparbot.practice.PracticeWorld.DIMENSION), 600);
			String pond = world.getServer().computeOnServer(server -> human(server).level().getBlockState(new net.minecraft.core.BlockPos(15, 63, 156)).toString());
			if (!pond.contains("water")) {
				throw new AssertionError("the meadow's pond has no water: " + pond);
			}
			world.getServer().runCommand("time set noon");
			view(context, world, "sparbot-practice-hub", 0.5, 72, -24, 0, 18);
			view(context, world, "sparbot-practice-hub-gateway", 0.5, 70, 6, 0, 10);
			// The Celestial Colosseum, built in the background: wait for it, then look round outside and in.
			world.getServer().waitFor(server -> SparBot.practice().colosseumReady(), 12000);
			int cx = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_X;
			int cz = io.github.flick256.sparbot.core.practice.PracticeLayout.GRAND_Z;
			if (Boolean.getBoolean("sparbot.deepOnly")) {
				context.runOnClient(client -> client.options.renderDistance().set(10));
				for (ColosseumSurvey.View v : io.github.flick256.sparbot.practice.colosseum.DeepSurvey.views()) {
					view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
				}
				deep(context, world, cx, cz);
				return;
			}
			// It is six hundred blocks across: look further than usual.
			int distance = context.computeOnClient(client -> client.options.renderDistance().get());
			context.runOnClient(client -> client.options.renderDistance().set(12));
			view(context, world, "colosseum-arrival", cx + 0.5, 64, cz + 226.5, 180, -8);
			view(context, world, "colosseum-approach", cx + 0.5, 110, cz + 262, 180, 12);
			view(context, world, "colosseum-aerial", cx + 0.5, 230, cz + 170, 180, 45);
			view(context, world, "colosseum-knights", cx + 70, 110, cz + 250, 150, 8);
			view(context, world, "colosseum-tunnel", cx + 0.5, 65, cz + 56, 180, -4);
			view(context, world, "colosseum-field", cx + 0.5, 66, cz + 0.5, 0, -22);
			view(context, world, "colosseum-field-north", cx + 0.5, 70, cz + 30, 180, -18);
			view(context, world, "colosseum-sky", cx + 0.5, 66, cz + 20, 180, -62);
			view(context, world, "colosseum-top-tier", cx + 0.5, 164, cz + 140, 180, 18);
			view(context, world, "colosseum-imperial-box", cx + 0.5, 96, cz + 70, 180, 12);
			view(context, world, "colosseum-imperial-box-front", cx + 0.5, 82, cz + 40, 0, -14);
			view(context, world, "colosseum-undercroft", cx + 70.5, 68, cz + 70.5, 45, 0);
			view(context, world, "colosseum-dragon", cx - 40, 240, cz - 40, 142, 5);
			view(context, world, "colosseum-whale", cx - 180, 215, cz + 90, 136, 5);
			view(context, world, "colosseum-phoenix", cx + 170, 250, cz + 20, -134, 5);
			view(context, world, "colosseum-fire", cx + 116, 120, cz - 116, -135, 30);
			view(context, world, "colosseum-frost", cx + 116, 120, cz + 116, -45, 30);
			view(context, world, "colosseum-citadel", cx + 0.5, 190, cz - 190, 180, 4);
			view(context, world, "colosseum-corridor", cx + 64.0, 65.6, cz + 53.7, 40.0F, 5.0F);
			view(context, world, "colosseum-atrium", cx + 88.5, 65.6, cz + 0.5, -90.0F, -25.0F);
			view(context, world, "colosseum-bridge", cx + 113.5, 89.6, cz + 0.5, 90.0F, 10.0F);
			view(context, world, "colosseum-vomitory", cx + 84.0, 108.6, cz + 9.0, -90.0F, 0.0F);
			view(context, world, "colosseum-concourse", cx + -42.0, 108.6, cz + 72.7, -150.0F, 20.0F);
			// The twenty-four great halls, and the way down to the Brink.
			for (ColosseumSurvey.View v : ColosseumSurvey.halls()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			for (ColosseumSurvey.View v : ColosseumSurvey.descent()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			// A vault: crouch at its cracked wall until it gives way, then look inside.
			int[] door = ColosseumSurvey.vaultEntrance(0);
			world.getServer().runOnServer(server -> {
				var player = human(server);
				player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
				double back = 1.6;
				double a = Math.atan2(door[2], door[0]);
				player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), cx + door[0] + 0.5 - Math.cos(a) * back, door[1],
					cz + door[2] + 0.5 - Math.sin(a) * back, java.util.Set.of(), 0, 0, true);
			});
			context.waitTicks(20);
			context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
			world.getServer().waitFor(server -> human(server).level().getBlockState(new net.minecraft.core.BlockPos(cx + door[0], door[1], cz + door[2])).isAir(),
				400);
			context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
			ColosseumSurvey.View inside = ColosseumSurvey.vault(0);
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(),
				cx + inside.x(), Math.floor(inside.y()), cz + inside.z(), java.util.Set.of(), inside.yaw(), 0, true));
			context.waitTicks(30);
			view(context, world, inside.name(), cx + inside.x(), inside.y(), cz + inside.z(), inside.yaw(), inside.pitch());
			// The Deep: the Brink, the cavern, Vaelor on his throne; the Leap, the fight in three phases, the way back up.
			for (ColosseumSurvey.View v : io.github.flick256.sparbot.practice.colosseum.DeepSurvey.views()) {
				view(context, world, v.name(), cx + v.x(), v.y(), cz + v.z(), v.yaw(), v.pitch());
			}
			deep(context, world, cx, cz);
			ColosseumSurvey.View plaque = ColosseumSurvey.plaque();
			view(context, world, plaque.name(), cx + plaque.x(), plaque.y(), cz + plaque.z(), plaque.yaw(), plaque.pitch());
			view(context, world, "colosseum-crystal", cx + 0.5, 70, cz + 40.5, 180, -40);
			view(context, world, "colosseum-crystal-far", cx + 0.5, 215, cz + 110, 180, 0);
			world.getServer().runCommand("time set midnight");
			view(context, world, "colosseum-night-inside", cx + 0.5, 96, cz + 70, 180, 12);
			view(context, world, "colosseum-night-outside", cx + 0.5, 130, cz + 262, 180, 16);
			view(context, world, "colosseum-night-hall", cx + 45.5, 67.2, cz + 39.9, -48.8F, 8.0F);
			view(context, world, "colosseum-night-crystal", cx + 0.5, 215, cz + 110, 180, 0);
			context.runOnClient(client -> client.options.renderDistance().set(distance));
			world.getServer().runCommand("time set noon");
			// It resets once everyone has left: stand in the field, dig a hole and place a block, leave, and it is back.
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(), cx + 0.5, 64,
				cz + 0.5, java.util.Set.of(), 0, 0, true));
			context.waitTicks(40);
			world.getServer().runOnServer(server -> {
				var level = (net.minecraft.server.level.ServerLevel) human(server).level();
				level.setBlock(new net.minecraft.core.BlockPos(cx + 3, 63, cz + 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
				level.setBlock(new net.minecraft.core.BlockPos(cx + 3, 64, cz + 4), net.minecraft.world.level.block.Blocks.COBBLESTONE.defaultBlockState(), 3);
			});
			world.getServer().runOnServer(server -> human(server).teleportTo((net.minecraft.server.level.ServerLevel) human(server).level(), 0.5, 64, -3.5,
				java.util.Set.of(), 0, 0, true));
			world.getServer().waitFor(server -> {
				var level = human(server).level();
				return !level.getBlockState(new net.minecraft.core.BlockPos(cx + 3, 63, cz + 3)).isAir()
					&& level.getBlockState(new net.minecraft.core.BlockPos(cx + 3, 64, cz + 4)).isAir();
			}, 1200);
			// The training hall, and a drill: full charge against the dummy, real clicks scored by the server.
			view(context, world, "training-hall", io.github.flick256.sparbot.core.practice.PracticeLayout.HALL_X + 0.5, 92,
				io.github.flick256.sparbot.core.practice.PracticeLayout.HALL_Z + 60, 180, 30);
			drills(context, world);
			// A fight in the meadow against a pro, started like the Fight button does.
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight uhc_duel pro"));
			world.getServer().waitFor(server -> SparBot.matches().match("practice_uhc").isPresent(), 600);
			context.waitTicks(80);
			world.getConnection().waitForChunksRender();
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			context.waitTick();
			context.takeScreenshot("sparbot-practice-uhc-fight");
			context.getInput().pressKey(GLFW.GLFW_KEY_F1);
			world.getServer().runOnServer(server -> {
				SparBot.matches().stop(server, "practice_uhc");
				SparBot.bots().get("SparUhc").ifPresent(b -> SparBot.bots().remove(b, "test over"));
			});
			context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice leave"));
			world.getServer().waitFor(server -> human(server) != null && human(server).level() == server.overworld(), 200);
		}
	}

	/** End-to-end personal study flow using the real screen controls and client command hook. */
	private static void studyFlow(ClientGameTestContext context, TestSingleplayerContext world) {
		context.getInput().resizeWindow(640, 480);
		waitForStudyScreen(context, "world-entry");
		StudyQuestion question = context.computeOnClient(client -> StudyClient.question());
		if (question == null || !question.subject().equals("Mathematical Methods")) {
			throw new AssertionError("entry study question did not use the selected subject");
		}
		assertStudyLayoutAcrossSizes(context);
		context.takeScreenshot("study-question");
		var entryBefore = studyProgress(context, question.id());
		answerStudyQuestion(context, question, true);
		var entryAfter = studyProgress(context, question.id());
		assertAssistedSuccess(entryBefore, entryAfter, "entry answer");
		clickMenu(context, "Continue");
		context.waitTicks(4);
		if (!world.getServer().computeOnServer(server -> human(server).level() == server.overworld())) {
			throw new AssertionError("finishing entry study moved the player out of the world");
		}

		// Entry credit permits this first activity command to proceed without another question.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight sword_duel beginner"));
		world.getServer().waitFor(server -> SparBot.matches().match("practice_sword").isPresent(), 1200);
		world.getServer().runOnServer(server -> SparBot.matches().stop(server, "practice_sword"));
		context.waitTicks(4);

		// The next activity is queued behind a real question. Completing the screen replays it once.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight sword_duel beginner"));
		waitForStudyScreen(context, "second-fight-queue");
		world.getServer().waitFor(server -> SparBot.matches().match("practice_sword").isEmpty(), 20);
		question = context.computeOnClient(client -> StudyClient.question());
		assertStudyLayoutAcrossSizes(context);
		answerStudyQuestion(context, question, true);
		clickMenu(context, "Continue");
		world.getServer().waitFor(server -> SparBot.matches().match("practice_sword").isPresent(), 1200);
		world.getServer().runOnServer(server -> SparBot.matches().stop(server, "practice_sword"));

		// Cancelling a queued command discards it and cannot start a match later.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight sword_duel beginner"));
		waitForStudyScreen(context, "third-fight-queue-before-cancel");
		clickMenu(context, "Return");
		context.waitTicks(20);
		if (world.getServer().computeOnServer(server -> SparBot.matches().match("practice_sword").isPresent())) {
			throw new AssertionError("cancelled study question started its queued fight");
		}

		// Open optional revision from the menu and reach an English self-review through the same scheduler
		// and controls used by a player. English starts with written response questions in the bundled bank.
		context.runOnClient(client -> StudyClient.configureSubjects(java.util.Set.of("English")));
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		boolean selfReviewCompleted = false;
		for (int i = 0; i < 14 && !selfReviewCompleted; i++) {
			clickMenu(context, "Open study");
			waitForStudyScreen(context, "optional-english-" + i);
			question = context.computeOnClient(client -> StudyClient.question());
			if (question == null || !question.subject().equals("English")) throw new AssertionError("optional study did not use English");
			assertStudyLayoutAcrossSizes(context);
			if (question.kind() == StudyQuestion.Kind.SELF_REVIEW) {
				var before = studyProgress(context, question.id());
				answerStudyQuestion(context, question, false);
				var after = studyProgress(context, question.id());
				if (after.attempts() != before.attempts() + 1
					|| after.assistedSuccesses() != before.assistedSuccesses()
					|| after.unaidedSuccesses() != before.unaidedSuccesses()
					|| after.dueAtMillis() < System.currentTimeMillis()
					|| after.dueAtMillis() > System.currentTimeMillis() + 11 * 60_000L) {
					throw new AssertionError("Needs work self-review did not record an assisted miss with a short review interval");
				}
				clickMenu(context, "Continue");
				selfReviewCompleted = true;
			} else {
				answerStudyQuestion(context, question, true);
				clickMenu(context, "Continue");
			}
		}
		if (!selfReviewCompleted) throw new AssertionError("English scheduler did not present a self-review question");
		context.runOnClient(client -> StudyClient.setEnabled(false));
	}

	private static void answerStudyQuestion(ClientGameTestContext context, StudyQuestion question, boolean selfReviewMeetsCriteria) {
		if (question == null) throw new AssertionError("study question disappeared");
		if (question.kind() == io.github.flick256.sparbot.core.study.StudyQuestion.Kind.CHOICE) {
			int wrong = question.answerIndex() == 0 ? 1 : 0;
			clickStudyButton(context, "Option " + (char) ('A' + wrong) + ": " + question.options().get(wrong));
			clickMenu(context, "Check answer");
			clickStudyButton(context, "Show hint 1");
			context.takeScreenshot("study-hints");
			clickStudyButton(context, "Option " + (char) ('A' + question.answerIndex()) + ": " + question.options().get(question.answerIndex()));
			clickMenu(context, "Check answer");
		} else if (question.kind() == io.github.flick256.sparbot.core.study.StudyQuestion.Kind.NUMBER) {
			focusField(context, "Answer");
			context.getInput().typeChars("999999999");
			clickMenu(context, "Check answer");
			clickStudyButton(context, "Show hint 1");
			context.takeScreenshot("study-hints");
			focusField(context, "Answer");
			selectAll(context);
			context.getInput().typeChars(Double.toString(question.numericAnswer()));
			clickMenu(context, "Check answer");
		} else {
			focusField(context, "Your written attempt");
			context.getInput().typeChars("I identified the method and explained the main reasoning.");
			clickStudyButton(context, "Reveal rubric");
			context.takeScreenshot("study-writtenreview");
			clickMenu(context, selfReviewMeetsCriteria ? "Met criteria" : "Needs work");
		}
	}

	private static void waitForStudyScreen(ClientGameTestContext context, String phase) {
		try {
			context.waitForScreen(StudyScreen.class);
		} catch (AssertionError timeout) {
			String state = context.computeOnClient(client -> {
				var screen = client.gui.screen();
				StudyQuestion question = StudyClient.question();
				return "activeScreen=" + (screen == null ? "null" : screen.getClass().getName())
					+ ", required=" + StudyClient.required() + ", enabled=" + StudyClient.enabled()
					+ ", questionId=" + (question == null ? "null" : question.id())
					+ ", reason=" + StudyClient.reason() + ", status=" + StudyClient.status();
			});
			try { context.takeScreenshot("study-timeout-" + phase); } catch (Throwable screenshotFailure) { timeout.addSuppressed(screenshotFailure); }
			throw new AssertionError("Timed out waiting for study screen at " + phase + " (" + state + ")", timeout);
		}
	}

	/** Native layout-only sweep of every primary menu tab and each kit editor page. */
	private static void menuLayoutOnly(ClientGameTestContext context) {
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.flick256.sparbot.menu.MenuRequestPayload(false)));
		context.waitTicks(12);
		if (!Boolean.getBoolean("sparbot.editorLayoutOnly")) {
			assertMenuLayoutAcrossSizes(context);
			context.takeScreenshot("layout-home-640x480");
			for (String tab : java.util.List.of("Practice", "Bots", "Spawn", "Kits", "Drills", "You", "Rivals", "Career", "Replay", "Settings")) {
				clickMenu(context, tab);
				context.waitTicks(2);
				assertMenuLayoutAcrossSizes(context);
			}
		}
		clickMenu(context, "Kits");
		clickMenu(context, "Edit");
		context.waitForScreen(KitEditorScreen.class);
		assertEditorLayoutAcrossSizes(context);
		context.takeScreenshot("layout-editor-640x480");
		clickMenu(context, "Items");
		assertEditorLayoutAcrossSizes(context);
		clickMenu(context, "Details");
		assertEditorLayoutAcrossSizes(context);
		context.takeScreenshot("layout-editor-details-640x480");
		clickMenu(context, "Done");
		context.waitFor(client -> client.gui.screen() == null);
	}

	private static void assertMenuLayoutAcrossSizes(ClientGameTestContext context) {
		for (int[] size : new int[][] {{320, 240}, {400, 225}, {480, 270}, {640, 360}, {640, 480}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			assertActualScreenSize(context, size[0], size[1]);
			scrollMenuToEdge(context, 3);
			for (int scrolls = 0; scrolls < 120; scrolls++) {
				var rects = currentMenuLayout(context);
			assertLayoutRects(context, rects, size[0], size[1], "menu");
				if (!scrollMenu(context, -3)) break;
				context.waitTick();
				if (rects.equals(currentMenuLayout(context))) break;
				if (scrolls == 119) throw new AssertionError("menu did not reach bottom of scrollable content");
			}
			scrollMenuToEdge(context, 3);
		}
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(2);
		scrollMenuToEdge(context, 3);
	}

	private static void assertEditorLayoutAcrossSizes(ClientGameTestContext context) {
		for (int[] size : new int[][] {{320, 240}, {400, 225}, {480, 270}, {640, 360}, {640, 480}}) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			assertActualScreenSize(context, size[0], size[1]);
			var rects = context.computeOnClient(client -> {
				if (!(client.gui.screen() instanceof KitEditorScreen editor)) throw new AssertionError("kit editor closed during layout check");
				return editor.layoutSnapshot();
			});
			assertLayoutRects(context, rects, size[0], size[1], "kit editor");
		}
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(2);
	}

	private static void assertActualScreenSize(ClientGameTestContext context, int width, int height) {
		int[] actual = context.computeOnClient(client -> new int[] {client.gui.screen().width, client.gui.screen().height});
		if (actual[0] != width || actual[1] != height) {
			throw layoutFailure(context, "screen", width, height,
				"GUI dimensions " + actual[0] + "x" + actual[1] + " do not match requested size");
		}
	}

	private static java.util.List<StudyScreen.LayoutRect> currentMenuLayout(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof SparBotMenuScreen menu)) throw new AssertionError("main menu closed during layout check");
			return menu.layoutSnapshot();
		});
	}

	private static void scrollMenuToEdge(ClientGameTestContext context, double vertical) {
		for (int i = 0; i < 120; i++) {
			var before = currentMenuLayout(context);
			if (!scrollMenu(context, vertical)) return;
			context.waitTick();
			if (before.equals(currentMenuLayout(context))) return;
		}
	}

	private static boolean scrollMenu(ClientGameTestContext context, double vertical) {
		return context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof SparBotMenuScreen menu)) return false;
			return menu.mouseScrolled(menu.width * 0.75, menu.height * 0.6, 0, vertical);
		});
	}

	private static void assertLayoutRects(ClientGameTestContext context, java.util.List<StudyScreen.LayoutRect> rects, int width, int height, String screen) {
		for (int i = 0; i < rects.size(); i++) {
			var a = rects.get(i);
			if (a.x() < 0 || a.y() < 0 || a.width() < 0 || a.height() < 0
				|| a.x() + a.width() > width || a.y() + a.height() > height) {
				throw layoutFailure(context, screen, width, height, "layout outside bounds: " + a);
			}
			if (a.kind().equals("hitbox")) continue;
			for (int j = i + 1; j < rects.size(); j++) {
				var b = rects.get(j);
				if (b.kind().equals("hitbox")) continue;
				boolean overlaps = a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
					&& a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
				boolean containedText = isText(a.kind()) && contains(b, a) && isContainer(b.kind())
					|| isText(b.kind()) && contains(a, b) && isContainer(a.kind());
				if (overlaps && !containedText) {
					throw layoutFailure(context, screen, width, height, "layout elements overlap: " + a + " / " + b);
				}
			}
		}
	}

	private static boolean isText(String kind) { return kind.endsWith("label") || kind.equals("label"); }
	private static boolean isContainer(String kind) { return kind.equals("widget") || kind.endsWith("-cell"); }
	private static boolean contains(StudyScreen.LayoutRect outer, StudyScreen.LayoutRect inner) {
		return outer.x() <= inner.x() && outer.y() <= inner.y()
			&& outer.x() + outer.width() >= inner.x() + inner.width()
			&& outer.y() + outer.height() >= inner.y() + inner.height();
	}

	private static AssertionError layoutFailure(ClientGameTestContext context, String screen, int width, int height, String message) {
		AssertionError failure = new AssertionError(screen + " at " + width + "x" + height + ": " + message);
		try { context.takeScreenshot("layout-failure-" + screen.replace(' ', '-') + "-" + width + "x" + height); }
		catch (Throwable screenshotFailure) { failure.addSuppressed(screenshotFailure); }
		return failure;
	}

	private record StudyProgressSnapshot(int attempts, int assistedSuccesses, int unaidedSuccesses, long dueAtMillis) { }

	private static StudyProgressSnapshot studyProgress(ClientGameTestContext context, String questionId) {
		return context.computeOnClient(client -> {
			var progress = StudyClient.progress(questionId);
			return new StudyProgressSnapshot(progress.attempts(), progress.assistedSuccesses(), progress.unaidedSuccesses(), progress.dueAtMillis());
		});
	}

	private static void assertAssistedSuccess(StudyProgressSnapshot before, StudyProgressSnapshot after, String label) {
		if (after.attempts() != before.attempts() + 2 || after.assistedSuccesses() != before.assistedSuccesses() + 1
			|| after.unaidedSuccesses() != before.unaidedSuccesses()) {
			throw new AssertionError(label + " did not record the wrong retry and hinted success as assisted progress");
		}
	}

	private static void assertStudyLayoutAcrossSizes(ClientGameTestContext context) {
		int[][] sizes = {{320, 240}, {400, 225}, {640, 360}};
		for (int[] size : sizes) {
			context.getInput().resizeWindow(size[0], size[1]);
			context.waitTicks(2);
			int[] actual = context.computeOnClient(client -> new int[] {client.gui.screen().width, client.gui.screen().height});
			if (actual[0] != size[0] || actual[1] != size[1]) {
				throw new AssertionError("GUI dimensions " + actual[0] + "x" + actual[1] + " do not match requested " + size[0] + "x" + size[1]);
			}
			scrollStudyToEdge(context, 3);
			for (int scrolls = 0; scrolls < 120; scrolls++) {
				java.util.List<StudyScreen.LayoutRect> rects = currentStudyLayout(context);
				assertStudyLayout(rects, size[0], size[1]);
				if (!scrollStudy(context, -3)) break;
				context.waitTick();
				if (rects.equals(currentStudyLayout(context))) break;
				if (scrolls == 119) throw new AssertionError("study screen did not reach the bottom of its scrollable content");
			}
			scrollStudyToEdge(context, 3);
		}
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(2);
	}

	private static java.util.List<StudyScreen.LayoutRect> currentStudyLayout(ClientGameTestContext context) {
		return context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof StudyScreen screen)) throw new AssertionError("study screen closed during layout check");
			return screen.layoutSnapshot();
		});
	}

	private static void assertStudyLayout(java.util.List<StudyScreen.LayoutRect> rects, int width, int height) {
		for (int i = 0; i < rects.size(); i++) {
			var a = rects.get(i);
			if (a.x() < 0 || a.y() < 0 || a.width() < 0 || a.height() < 0
				|| a.x() + a.width() > width || a.y() + a.height() > height) {
				throw new AssertionError("study layout element outside " + width + "x" + height + ": " + a);
			}
			if (a.kind().equals("hitbox")) continue;
			for (int j = i + 1; j < rects.size(); j++) {
				var b = rects.get(j);
				if (b.kind().equals("hitbox")) continue;
				boolean overlaps = a.x() < b.x() + b.width() && b.x() < a.x() + a.width()
					&& a.y() < b.y() + b.height() && b.y() < a.y() + a.height();
				if (overlaps) throw new AssertionError("study layout elements overlap at " + width + "x" + height + ": " + a + " / " + b);
			}
		}
	}

	private static void scrollStudyToEdge(ClientGameTestContext context, double vertical) {
		for (int i = 0; i < 120; i++) {
			var before = currentStudyLayout(context);
			if (!scrollStudy(context, vertical)) return;
			context.waitTick();
			if (before.equals(currentStudyLayout(context))) return;
		}
	}

	private static boolean scrollStudy(ClientGameTestContext context, double vertical) {
		return context.computeOnClient(client -> {
			if (!(client.gui.screen() instanceof StudyScreen screen)) return false;
			return screen.mouseScrolled(screen.width / 2.0, screen.height / 2.0, 0, vertical);
		});
	}

	private static void scrollStudyUntilButton(ClientGameTestContext context, String label) {
		for (int i = 0; i < 120; i++) {
			boolean visible = context.computeOnClient(client -> client.gui.screen() != null && client.gui.screen().children().stream()
				.anyMatch(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible && w.active && w.getMessage().getString().equals(label)));
			if (visible) return;
			var before = currentStudyLayout(context);
			if (!scrollStudy(context, -3)) break;
			context.waitTick();
			if (before.equals(currentStudyLayout(context))) break;
		}
		throw new AssertionError("study action not reachable by scrolling: " + label);
	}

	private static void clickStudyButton(ClientGameTestContext context, String label) {
		scrollStudyUntilButton(context, label);
		context.clickScreenButton(label);
		context.waitTicks(2);
	}

	/** Two world closures in the same JVM, with a live bot left for the shutdown hook each time. */
	private static void shutdown(ClientGameTestContext context) {
		for (int run = 0; run < 2; run++) {
			java.util.concurrent.atomic.AtomicReference<net.minecraft.server.MinecraftServer> stopped = new java.util.concurrent.atomic.AtomicReference<>();
			try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(settings -> settings.setAllowCommands(true)).create()) {
				world.getServer().runCommand("sparbot spawn ExitBot pro sparbot_uhc");
				world.getServer().waitFor(server -> SparBot.bots().get("ExitBot").isPresent());
				world.getServer().runOnServer(server -> stopped.set(server));
				context.waitTicks(10);
			}
			context.waitFor(client -> client.getSingleplayerServer() == null && stopped.get().isStopped(), 200);
			if (!SparBot.bots().all().isEmpty()) {
				throw new AssertionError("Bot manager retained a bot after world shutdown");
			}
		}
	}

	/**
	 * The drills, played by the real client: full charge against the dummy, jump resets against the attacker
	 * (jumping the moment the client sees its hit, as a player would), punishing the whiffer's missed swing,
	 * and the guard that stops a drill killing you.
	 */
	/** While set, the client presses jump on exactly the client ticks a hit's knockback arrives (see drills()). */
	private static volatile boolean JUMP_ON_HIT;
	private static net.minecraft.world.phys.Vec3 jumpLastVelocity;
	/** Real menu navigation, form persistence, search, server actions, keyboard focus and compact layouts. */
	private static void menuSmoke(ClientGameTestContext context, TestSingleplayerContext world) {
		context.getInput().resizeWindow(1280, 800);
		context.runOnClient(client -> client.options.guiScale().set(2));
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		context.waitTicks(4);
		context.takeScreenshot("menu-overview");
		clickMenu(context, "Practice");
		context.takeScreenshot("menu-practice");
		clickMenu(context, "Swight");
		context.takeScreenshot("menu-practice-swight");
		clickMenu(context, "Dn");
		context.takeScreenshot("menu-practice-swight-detail");
		clickMenu(context, "Spawn");
		clickMenu(context, "Swight");
		clickMenu(context, "Choose kit");
		focusField(context, "Search kits");
		context.getInput().typeChars("pvphq_uhc");
		context.waitTicks(4);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.flick256.sparbot.menu.MenuRequestPayload(false)));
		context.waitTicks(8);
		context.runOnClient(client -> {
			if (!(client.gui.screen().getFocused() instanceof net.minecraft.client.gui.components.EditBox box) || !box.getValue().equals("pvphq_uhc")) {
				throw new AssertionError("search value or keyboard focus was lost on refresh");
			}
		});
		context.takeScreenshot("menu-kit-search");
		clickMenu(context, "Use kit");
		context.takeScreenshot("menu-spawn");
		clickMenu(context, "Spawn bot");
		world.getServer().waitFor(server -> SparBot.bots().get("Swight").isPresent());
		context.waitTicks(10);
		context.takeScreenshot("menu-bots");
		clickMenu(context, "Tech");
		clickMenu(context, "strafe: On");
		world.getServer().waitFor(server -> SparBot.bots().get("Swight").map(b -> b.disabledTechniques().contains(io.github.flick256.sparbot.core.brain.Technique.STRAFE)).orElse(false));
		context.waitTicks(8);
		context.takeScreenshot("menu-techniques");
		clickMenu(context, "Kits");
		clickMenu(context, "Equip");
		world.getServer().waitFor(server -> human(server) != null && !human(server).getInventory().isEmpty());
		clickMenu(context, "Save layout");
		world.getServer().waitFor(server -> human(server) != null && SparBot.playerLayouts().kits(human(server).getUUID()).contains("pvphq_uhc"));
		clickMenu(context, "You");
		world.getServer().runOnServer(server -> human(server).setHealth(5));
		clickMenu(context, "Full heal");
		world.getServer().waitFor(server -> human(server).getHealth() >= human(server).getMaxHealth());
		context.takeScreenshot("menu-you");
		clickMenu(context, "Settings");
		focusField(context, "Search settings");
		context.getInput().typeChars("autoTarget");
		context.waitTicks(4);
		context.takeScreenshot("menu-settings");
		clickMenu(context, "Off");
		world.getServer().waitFor(server -> SparBot.config().autoTarget);
		world.getServer().runCommand("sparbot config set autoTarget false");
		focusField(context, "Search settings");
		selectAll(context);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.flick256.sparbot.menu.MenuRequestPayload(false)));
		context.waitTicks(8); // A selected search range must also survive the snapshot.
		context.getInput().typeChars("maxBots");
		context.waitTicks(3);
		context.takeScreenshot("menu-settings-draft-search");
		context.runOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.EditBox box && box.getMessage().getString().equals("Search settings")) {
					if (!box.getValue().equals("maxBots")) { throw new AssertionError("search replacement produced: " + box.getValue()); }
				}
			}
		});
		int maxBotsBefore = world.getServer().computeOnServer(server -> SparBot.config().maxBots);
		focusField(context, "Max Bots");
		selectAll(context);
		context.getInput().typeChars("12");
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new io.github.flick256.sparbot.menu.MenuRequestPayload(false)));
		context.waitTicks(8);
		context.runOnClient(client -> {
			if (!(client.gui.screen().getFocused() instanceof net.minecraft.client.gui.components.EditBox box) || !box.getValue().equals("12")) {
				throw new AssertionError("unsaved setting edit was lost on refresh");
			}
		});
		clickMenu(context, "Set");
		world.getServer().waitFor(server -> SparBot.config().maxBots == 12);
		world.getServer().runCommand("sparbot config set maxBots " + maxBotsBefore);
		clickMenu(context, "Drills");
		context.takeScreenshot("menu-drills");
		clickMenu(context, "Overview");
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(8);
		context.takeScreenshot("menu-compact");
		context.runOnClient(client -> {
			for (var child : client.gui.screen().children()) {
				if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible
					&& (w.getX() < 0 || w.getY() < 0 || w.getRight() > client.gui.screen().width || w.getBottom() > client.gui.screen().height)) {
					throw new AssertionError("widget outside compact viewport: " + w.getMessage().getString());
				}
			}
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.runOnClient(client -> { if (client.gui.screen().getFocused() == null) { throw new AssertionError("keyboard navigation has no focus"); } });
		clickMenu(context, "Practice");
		clickMenu(context, "Swight");
		clickMenu(context, "Enter arena");
		context.runOnClient(client -> { if (client.gui.screen() instanceof SparBotMenuScreen) { throw new AssertionError("fight action must return to the game"); } });
		world.getServer().waitFor(server -> human(server) != null && human(server).level().dimension().equals(io.github.flick256.sparbot.practice.PracticeWorld.DIMENSION), 1200);
		world.getServer().runOnServer(server -> {
			if (SparBot.bots().get("Swight2").isEmpty() || SparBot.bots().get("Swight").isEmpty()) {
				throw new AssertionError("practice Swight must keep its identity without replacing a user-created Swight");
			}
		});
		world.getServer().runOnServer(server -> server.getCommands().performPrefixedCommand(human(server).createCommandSourceStack(), "sparbot practice leave"));
		context.waitFor(client -> client.player != null && client.gui.screen() == null
			&& client.player.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD), 1200);
		context.getInput().resizeWindow(1280, 800);
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Bots");
		clickMenu(context, "Remove");
		world.getServer().waitFor(server -> SparBot.bots().get("Swight").isEmpty());
		clickMenu(context, "Done");
	}

	/** The 4.0 menu through live buttons and commands; only the coach report and replay marker are test fixtures. */
	private static void menuV4Smoke(ClientGameTestContext context, TestSingleplayerContext world) {
		String runId = Long.toString(System.currentTimeMillis());
		String recording = "menu_v4_" + runId;
		String cloneId = "menuv4_" + runId;
		world.getServer().runOnServer(server -> {
			var player = human(server);
			io.github.flick256.sparbot.core.drill.DrillTally tally = new io.github.flick256.sparbot.core.drill.DrillTally();
			for (int i = 0; i < 6; i++) tally.hit(i < 5 ? 0.4 : 1.0, false, true, 2.8);
			SparBot.drills().reports().add(player.getUUID(), io.github.flick256.sparbot.core.drill.FightReport.of(
				"sword_duel", "Game Test Rival", "lost", System.currentTimeMillis(), tally));
		});

		context.runOnClient(client -> client.options.guiScale().set(2));
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		context.waitTicks(4);
		context.takeScreenshot("menu-v4-overview");
		clickMenu(context, "Rivals");
		context.takeScreenshot("menu-v4-rivals");
		clickMenu(context, "Challenge");
		world.getServer().waitFor(server -> SparBot.matches().inMatch(human(server).getUUID()), 300);
		world.getServer().runOnServer(server -> SparBot.matches().stopAll(server));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 200);

		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Career");
		context.takeScreenshot("menu-v4-career");
		clickMenu(context, "Continue");
		String careerArena = io.github.flick256.sparbot.core.practice.PracticeLayout.siteFor("sword_duel").arenaId();
		world.getServer().waitFor(server -> SparBot.matches().match(careerArena).isPresent(), 600);
		java.util.concurrent.atomic.AtomicInteger killedRounds = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicInteger roundsToWin = new java.util.concurrent.atomic.AtomicInteger();
		java.util.concurrent.atomic.AtomicBoolean authoredStyle = new java.util.concurrent.atomic.AtomicBoolean();
		java.util.concurrent.atomic.AtomicBoolean matchRecording = new java.util.concurrent.atomic.AtomicBoolean();
		world.getServer().waitFor(server -> {
			var match = SparBot.matches().match(careerArena).orElse(null);
			if (match == null) return true;
			roundsToWin.set(match.state().mode().roundsToWin());
			authoredStyle.set(match.b().isBot() && match.b().bot().playstyle().id().equals("balanced"));
			matchRecording.set(SparBot.recorder().active().stream().anyMatch(label -> label.startsWith("match-" + careerArena + "-")));
			return match.state().phase() == io.github.flick256.sparbot.core.match.MatchState.Phase.FIGHTING
				&& match.b().isBot() && !match.b().bot().body().isDeadOrDying();
		}, 300);
		// Win each round by killing the actual match bot once after the round enters FIGHTING.
		world.getServer().waitFor(server -> {
			var match = SparBot.matches().match(careerArena).orElse(null);
			if (match == null) return true;
			if (match.state().phase() == io.github.flick256.sparbot.core.match.MatchState.Phase.FIGHTING
				&& match.state().round() > killedRounds.get()) {
				killedRounds.set(match.state().round());
				var body = match.b().bot().body();
				var level = (net.minecraft.server.level.ServerLevel) body.level();
				body.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			}
			return !SparBot.matches().inMatch(human(server).getUUID());
		}, 1200);
		world.getServer().runOnServer(server -> {
			var progress = SparBot.careers().progress(human(server).getUUID(), "sword_duel");
			boolean nextUnlocked = SparBot.careers().menuRivals(human(server).getUUID()).stream()
				.anyMatch(r -> r.id().equals("redline") && r.unlocked());
			if (roundsToWin.get() != 3 || killedRounds.get() < 3 || !authoredStyle.get() || !matchRecording.get()
				|| progress.wins() != 1 || progress.tier() != 1 || progress.losses() != 0 || progress.seriesActive() || !nextUnlocked) {
				throw new AssertionError("actual best-of-five career win did not promote exactly once: rounds=" + roundsToWin.get()
					+ ", killed=" + killedRounds.get() + ", wins=" + progress.wins() + ", tier=" + progress.tier()
					+ ", unlocked=" + nextUnlocked + ", recorded=" + matchRecording.get());
			}
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Career");
		clickMenu(context, "Continue");
		world.getServer().waitFor(server -> SparBot.matches().match(careerArena).isPresent()
			&& SparBot.careers().progress(human(server).getUUID(), "sword_duel").seriesActive(), 600);
		world.getServer().runOnServer(server -> SparBot.matches().stop(server, careerArena));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 300);
		world.getServer().runOnServer(server -> {
			var progress = SparBot.careers().progress(human(server).getUUID(), "sword_duel");
			if (progress.wins() != 1 || progress.losses() != 0 || progress.tier() != 1 || progress.seriesActive())
				throw new AssertionError("stopping a career series awarded or retained progress");
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Career");
		clickMenu(context, "Continue");
		world.getServer().waitFor(server -> SparBot.matches().match(careerArena).isPresent()
			&& SparBot.careers().progress(human(server).getUUID(), "sword_duel").seriesActive(), 600);
		world.getServer().runOnServer(server -> SparBot.matches().stop(server, careerArena));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 300);
		world.getServer().runOnServer(server -> {
			var progress = SparBot.careers().progress(human(server).getUUID(), "sword_duel");
			if (progress.wins() != 1 || progress.losses() != 0 || progress.tier() != 1 || progress.seriesActive())
				throw new AssertionError("career retry after cancellation failed or awarded a result");
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Career");
		clickMenu(context, "Adaptation: Off");
		world.getServer().waitFor(server -> SparBot.careers().adaptivePractice(human(server).getUUID()), 200);
		context.waitTicks(8);
		clickMenu(context, "Adaptation: On");
		world.getServer().waitFor(server -> !SparBot.careers().adaptivePractice(human(server).getUUID()), 200);

		// Save this longer fixture after career matches so it is the newest recording in the native menu.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot record start " + recording + " @s"));
		world.getServer().waitFor(server -> SparBot.recorder().isRecording(recording), 200);
		world.getServer().runOnServer(server -> SparBot.recorder().event("combat", "Recorded game-test exchange", human(server).getUUID()));
		context.waitTicks(100);
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot record stop " + recording));
		world.getServer().waitFor(server -> !SparBot.recorder().isRecording(recording), 200);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
			new io.github.flick256.sparbot.menu.MenuRequestPayload(false)));
		context.waitFor(client -> client.gui.screen() instanceof SparBotMenuScreen menu
			&& !menu.recordingEntries().isEmpty() && menu.recordingEntries().getFirst().name().equals(recording), 200);

		clickMenu(context, "Replay");
		context.runOnClient(client -> {
			if (!(client.gui.screen() instanceof SparBotMenuScreen menu) || menu.recordingEntries().isEmpty()
				|| !menu.recordingEntries().getFirst().name().equals(recording)) {
				throw new AssertionError("the replay menu did not put the refreshed fixture first");
			}
		});
		clickMenu(context, "Choose recording");
		clickMenu(context, "Select recording");
		clickMenu(context, "Play");
		world.getServer().waitFor(server -> SparBot.replays().status().stream().anyMatch(r -> r.name().equals(recording)), 200);
		context.waitTicks(8);
		clickMenu(context, "Pause");
		world.getServer().waitFor(server -> replayStatus(recording).paused(), 200);
		context.takeScreenshot("menu-v4-replay");
		clickMenu(context, "0.5×");
		world.getServer().waitFor(server -> replayStatus(recording).speed() == 0.5, 200);
		int beforeStep = world.getServer().computeOnServer(server -> replayStatus(recording).frame());
		clickMenu(context, "Step +");
		world.getServer().waitFor(server -> replayStatus(recording).frame() > beforeStep, 200);
		int eventTick = world.getServer().computeOnServer(server -> replayStatus(recording).events().stream()
			.filter(e -> e.kind().equals("combat")).findFirst().orElseThrow().tick());
		clickMenu(context, "Go");
		world.getServer().waitFor(server -> replayStatus(recording).frame() == eventTick, 200);
		clickMenu(context, "Stop");
		world.getServer().waitFor(server -> SparBot.replays().status().stream().noneMatch(r -> r.name().equals(recording)), 200);

		clickMenu(context, "You");
		context.takeScreenshot("menu-v4-training");
		clickMenu(context, "Start drill");
		world.getServer().waitFor(server -> SparBot.drills().inDrill(human(server).getUUID()), 200);
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill stop"));
		world.getServer().waitFor(server -> !SparBot.drills().inDrill(human(server).getUUID()), 200);

		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Practice");
		clickMenu(context, "Uneven");
		world.getServer().waitFor(server -> SparBot.practice().terrain(human(server).getUUID()) == io.github.flick256.sparbot.core.practice.PracticeTerrain.UNEVEN,
			200);
		clickMenu(context, "Rematch");
		world.getServer().waitFor(server -> SparBot.matches().inMatch(human(server).getUUID()), 300);
		world.getServer().runOnServer(server -> SparBot.matches().stopAll(server));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 200);

		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		clickMenu(context, "Kits");
		clickMenu(context, "Edit");
		context.waitForScreen(KitEditorScreen.class);
		context.takeScreenshot("menu-v4-editor");
		// A discarded count edit for one slot must not carry into the next equipment selection.
		clickMenu(context, "Details");
		focusField(context, "Count");
		selectAll(context);
		context.getInput().typeChars("64");
		clickMenu(context, "Layout");
		clickMenu(context, "[ Layout");
		clickEditorAt(context, 400, 190); // Select the Head equipment slot in the rendered layout.
		clickMenu(context, "Items");
		focusField(context, "Search registered items");
		context.getInput().typeChars("minecraft:diamond_helmet");
		context.waitTicks(3);
		clickEditorAt(context, 30, 70); // Apply the only matching registered item to the selected equipment slot.
		clickMenu(context, "Details");
		context.takeScreenshot("menu-v4-editor-details");
		focusField(context, "New kit ID");
		selectAll(context);
		context.getInput().typeChars(cloneId);
		focusField(context, "Display name");
		selectAll(context);
		context.getInput().typeChars("Menu 4.0 test clone");
		focusField(context, "Enchantment ID");
		context.getInput().typeChars("minecraft:protection");
		focusField(context, "Level");
		selectAll(context);
		context.getInput().typeChars("4");
		clickMenu(context, "Add / update enchant");
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(4);
		context.runOnClient(client -> {
			var visibleWidgets = client.gui.screen().children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible)
				.map(c -> (net.minecraft.client.gui.components.AbstractWidget) c).toList();
			if (visibleWidgets.stream().anyMatch(w -> w.getX() < 0 || w.getY() < 0 || w.getRight() > client.gui.screen().width
				|| w.getBottom() > client.gui.screen().height))
				throw new AssertionError("compact kit editor widget is outside the viewport");
			for (int i = 0; i < visibleWidgets.size(); i++) for (int j = i + 1; j < visibleWidgets.size(); j++) {
				var a = visibleWidgets.get(i); var b = visibleWidgets.get(j);
				if (a.getX() < b.getRight() && a.getRight() > b.getX() && a.getY() < b.getBottom() && a.getBottom() > b.getY())
					throw new AssertionError("compact kit editor widgets overlap: " + a.getMessage().getString() + " and " + b.getMessage().getString());
			}
			if (client.gui.screen().children().stream().noneMatch(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w
				&& w.visible && w.getMessage().getString().equals("Save new custom kit")))
				throw new AssertionError("compact kit editor hid its Save action");
		});
		context.takeScreenshot("menu-v4-editor-details-compact");
		clickMenu(context, "Save new custom kit");
		world.getServer().waitFor(server -> SparBot.kits().get(cloneId).isPresent(), 300);
		world.getServer().runOnServer(server -> {
			var kit = SparBot.kits().get(cloneId).orElseThrow();
			if (kit.provenance().verified() || !kit.displayName().equals("Menu 4.0 test clone")
				|| kit.armor() == null || kit.armor().head() == null || !kit.armor().head().id().equals("minecraft:diamond_helmet")
				|| kit.armor().head().enchantments() == null
				|| !java.util.Objects.equals(kit.armor().head().enchantments().get("minecraft:protection"), 4)) {
				throw new AssertionError("the editor didn't save an unverified named draft");
			}
		});
		clickMenu(context, "Done");
		context.getInput().pressKey(GLFW.GLFW_KEY_B);
		context.waitForScreen(SparBotMenuScreen.class);
		context.getInput().resizeWindow(640, 480);
		context.waitTicks(8);
		context.runOnClient(client -> {
			java.util.Set<String> expected = java.util.Set.of("Overview", "Practice", "Bots", "Spawn", "Kits", "Drills", "You", "Rivals", "Career", "Replay", "Settings");
			java.util.Set<String> actual = client.gui.screen().children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w
				&& w.visible && expected.contains(w.getMessage().getString())).map(c -> ((net.minecraft.client.gui.components.AbstractWidget) c).getMessage().getString())
				.collect(java.util.stream.Collectors.toSet());
			if (!actual.equals(expected)) throw new AssertionError("compact navigation is missing tabs: " + expected + " vs " + actual);
			if (client.gui.screen().children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible)
				.map(c -> (net.minecraft.client.gui.components.AbstractWidget) c)
				.anyMatch(w -> w.getX() < 0 || w.getY() < 0 || w.getRight() > client.gui.screen().width || w.getBottom() > client.gui.screen().height)) {
				throw new AssertionError("a compact-menu widget is outside the viewport");
			}
		});
		context.getInput().pressKey(GLFW.GLFW_KEY_TAB);
		context.runOnClient(client -> {
			if (client.gui.screen().getFocused() == null) throw new AssertionError("compact menu navigation is not keyboard accessible");
		});
		context.takeScreenshot("menu-v4-compact");
		context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
		world.getServer().runOnServer(server -> SparBot.replays().stop(recording));
	}

	private static io.github.flick256.sparbot.record.ReplayManager.ReplayStatus replayStatus(String name) {
		return SparBot.replays().status().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
	}

	private static void selectAll(ClientGameTestContext context) {
		// This Fabric TestInput version injects KeyEvent modifiers=0, even while holdControl is active.
		// Send the real modified event through Screen so the normal editor shortcut is exercised.
		context.runOnClient(client -> {
			var screen = client.gui.screen();
			if (!(screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox box)
				|| !screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL))
				|| !box.getHighlighted().equals(box.getValue())) {
				throw new AssertionError("Ctrl+A must select the focused field");
			}
		});
	}

	private static void focusField(ClientGameTestContext context, String name) {
		boolean study = context.computeOnClient(client -> client.gui.screen() instanceof StudyScreen);
		if (study) scrollStudyUntilInput(context, name);
		context.runOnClient(client -> {
			var screen = client.gui.screen();
			var edit = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.EditBox e && e.visible && e.getMessage().getString().equals(name))
				.findFirst().orElse(null);
			if (edit == null && name.equals("Your written attempt")) {
				edit = screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.MultiLineEditBox m && m.visible).findFirst().orElse(null);
			}
			if (edit == null) throw new AssertionError("study input field not found: " + name);
			screen.setFocused(edit);
		});
	}

	private static void scrollStudyUntilInput(ClientGameTestContext context, String name) {
		for (int i = 0; i < 120; i++) {
			boolean visible = context.computeOnClient(client -> client.gui.screen() != null && client.gui.screen().children().stream().anyMatch(c -> {
				if (c instanceof net.minecraft.client.gui.components.EditBox box) return box.visible && box.getMessage().getString().equals(name);
				return name.equals("Your written attempt") && c instanceof net.minecraft.client.gui.components.MultiLineEditBox box && box.visible;
			}));
			if (visible) return;
			var before = currentStudyLayout(context);
			if (!scrollStudy(context, -3)) break;
			context.waitTick();
			if (before.equals(currentStudyLayout(context))) break;
		}
		throw new AssertionError("study input not reachable by scrolling: " + name);
	}

	private static void clickEditorAt(ClientGameTestContext context, int x, int y) {
		double scale = context.computeOnClient(client -> (double) client.getWindow().getWidth() / client.gui.screen().width);
		context.getInput().setCursorPos(x * scale, y * scale);
		context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_1);
		context.waitTick();
		context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_1);
		context.waitTick();
	}

	private static void clickMenu(ClientGameTestContext context, String label) {
		// Event review can leave the viewport below the playback controls. Search both ways.
		for (String direction : java.util.List.of("Up", "Dn")) {
			for (int i = 0; i < 50; i++) {
				boolean visible = context.computeOnClient(client -> client.gui.screen().children().stream().anyMatch(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w
					&& w.visible && w.active && w.getMessage().getString().equals(label)));
				if (visible) { context.clickScreenButton(label); context.waitTicks(2); return; }
				if (!context.tryClickScreenButton(direction)) { break; }
				context.waitTick();
			}
		}
		throw new AssertionError("menu action not reachable: " + label);
	}

	private static boolean jumpHookRegistered;

	private static void drills(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> {
			if (!jumpHookRegistered) {
				jumpHookRegistered = true;
				// The start of a client tick comes after the packets that arrived since the last: a hurt animation seen
				// here came with its knockback, so jump is held on this very tick (and let go on the next).
				// The knockback is a new velocity set by a packet between ticks: at the start of the tick it differs from
				// what the last tick left (the player stood or walked, so only knockback pushes it away from the bot).
				net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(c -> {
					if (c.player == null) {
						return;
					}
					net.minecraft.world.phys.Vec3 v = c.player.getDeltaMovement();
					boolean knocked = jumpLastVelocity != null && v.y > 0.3 && jumpLastVelocity.y <= 0 && c.player.onGround();
					if (JUMP_ON_HIT) {
						c.options.keyJump.setDown(knocked);
					}
				});
				net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(c -> {
					if (c.player != null) {
						jumpLastVelocity = c.player.getDeltaMovement();
					}
				});
			}
		});
		// Full charge: nine full-charge hits a little over a second apart pass the first stage (eight needed).
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill charge"));
		world.getServer().waitFor(server -> SparBot.drills().inDrill(human(server).getUUID()), 600);
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		for (int i = 0; i < 9; i++) {
			context.runOnClient(client -> {
				net.minecraft.world.entity.Entity dummy = drillBot(client);
				if (dummy != null) {
					client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, dummy.getEyePosition());
					client.gameMode.attack(client.player, dummy);
					client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
				}
			});
			context.waitTicks(25);
			if (i == 2) {
				context.takeScreenshot("drill-charge");
			}
		}
		world.getServer().waitFor(server -> SparBot.drills().stagesPassed(human(server).getUUID()) >= 1, 300);
		stopDrill(context, world);

		// Jump reset: the attacker swings on a rhythm. Taking its hits without jumping counts for nothing (the
		// knockback lifting you is not a jump); then the client sprints into it and jumps on the very client tick
		// each hit's knockback arrives (the start of the tick its hurt animation came in), as a perfect player would.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill jumpreset"));
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		int[] taken = {0};
		int[] lastHurt = {0};
		for (int i = 0; i < 400 && taken[0] < 2; i++) {
			context.runOnClient(client -> {
				int hurt = client.player.hurtTime;
				if (hurt > lastHurt[0]) {
					taken[0]++;
				}
				lastHurt[0] = hurt;
			});
			context.waitTick();
		}
		context.waitTicks(30);
		int unearned = world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()));
		if (taken[0] < 2 || unearned != 0) {
			throw new AssertionError("taking " + taken[0] + " hits without jumping counted " + unearned + " jump resets");
		}
		JUMP_ON_HIT = true;
		// W and the sprint key held, as a player sprints into the attacker.
		context.runOnClient(client -> {
			client.options.keyUp.setDown(true);
			client.options.keySprint.setDown(true);
		});
		String[] whyNotSprinting = {""};
		for (int i = 0; i < 900 && world.getServer().computeOnServer(server -> SparBot.drills().stagesPassed(human(server).getUUID()) < 1); i++) {
			// A player whose sprint dropped (they bumped into something) presses sprint again.
			context.runOnClient(client -> {
				// Face the attacker, as a player sprinting into it would (holding W blind walks into the bay's wall).
				net.minecraft.world.entity.Entity attacker = drillBot(client);
				if (attacker != null) {
					client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
				}
				if (!client.player.isSprinting() && client.player.onGround()) {
					whyNotSprinting[0] = "collision " + client.player.horizontalCollision + ", food " + client.player.getFoodData().getFoodLevel();
					client.player.setSprinting(true);
				}
			});
			context.waitTick();
			if (i == 120) {
				context.takeScreenshot("drill-jumpreset");
			}
		}
		JUMP_ON_HIT = false;
		context.runOnClient(client -> {
			client.options.keyUp.setDown(false);
			client.options.keySprint.setDown(false);
			client.options.keyJump.setDown(false);
		});
		int passed = world.getServer().computeOnServer(server -> SparBot.drills().stagesPassed(human(server).getUUID()));
		if (passed < 1) {
			throw new AssertionError("jumping as each hit lands didn't pass the jump reset drill's first stage (good reps: "
				+ world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID())) + "; verdicts: "
				+ world.getServer().computeOnServer(server -> SparBot.drills().recentVerdicts(human(server).getUUID())) + "; last not sprinting: " + whyNotSprinting[0] + ")");
		}
		// Nobody dies in a drill.
		boolean alive = world.getServer().computeOnServer(server -> {
			net.minecraft.server.level.ServerPlayer p = human(server);
			p.setHealth(1);
			p.invulnerableTime = 0;
			p.hurtServer((net.minecraft.server.level.ServerLevel) p.level(), p.damageSources().generic(), 100);
			return p.isAlive() && p.getHealth() >= p.getMaxHealth() - 0.01F;
		});
		if (!alive) {
			throw new AssertionError("a lethal hit in a drill killed the player");
		}
		stopDrill(context, world);

		// Whiff punish: step in and hit the moment the whiffer's swing misses.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill punish"));
		world.getServer().waitFor(server -> SparBot.drills().stageRunning(human(server).getUUID()), 600);
		for (int i = 0; i < 900 && world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()) < 2); i++) {
			boolean open = world.getServer().computeOnServer(server -> SparBot.drills().punishWindowOpen(human(server).getUUID()));
			if (open) {
				world.getServer().runOnServer(server -> {
					net.minecraft.server.level.ServerPlayer p = human(server);
					SparBot.bots().get("Drill" + (SparBot.drills().bayOf(p.getUUID()) + 1)).map(io.github.flick256.sparbot.bot.Bot::body).ifPresent(b -> {
						Vec3Holder.step(p, b);
					});
				});
				context.waitTicks(2);
				context.runOnClient(client -> {
					net.minecraft.world.entity.Entity bot = drillBot(client);
					if (bot != null) {
						client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, bot.getEyePosition());
						client.gameMode.attack(client.player, bot);
						client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
					}
				});
				context.waitTicks(30);
			}
			context.waitTick();
		}
		int punished = world.getServer().computeOnServer(server -> SparBot.drills().goodReps(human(server).getUUID()));
		if (punished < 2) {
			throw new AssertionError("hitting the whiffer right after its missed swing didn't count as a punish (good reps: " + punished + ")");
		}
		context.takeScreenshot("drill-punish");
		stopDrill(context, world);

		// A sword duel against an intermediate bot in the practice kit, with the live coach on: the client swings
		// at half charge now and then, which the coach points out.
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot practice fight sword_duel intermediate"));
		world.getServer().waitFor(server -> SparBot.matches().inMatch(human(server).getUUID()), 600);
		for (int i = 0; i < 240; i++) {
			if (i % 7 == 0) {
				context.runOnClient(client -> {
					for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
						if (e instanceof net.minecraft.world.entity.player.Player p && p != client.player && p.distanceTo(client.player) < 4) {
							client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, p.getEyePosition());
							client.gameMode.attack(client.player, p);
							client.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
						}
					}
				});
			}
			context.waitTick();
			if (i == 160) {
				context.takeScreenshot("fight-coach");
			}
		}
		boolean kit = world.getServer().computeOnServer(server -> human(server).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
			.getEnchantments().toString().contains("protection"));
		if (!kit) {
			throw new AssertionError("the sword duel didn't give the practice kit (Protection IV armor)");
		}
		world.getServer().runOnServer(server -> SparBot.matches().stopAll(server));
		world.getServer().waitFor(server -> !SparBot.matches().inMatch(human(server).getUUID()), 600);
	}

	/** Moves a player to just inside reach of the bot, facing it (a player stepping in to punish). */
	private static final class Vec3Holder {
		static void step(net.minecraft.server.level.ServerPlayer p, net.minecraft.world.entity.Entity bot) {
			net.minecraft.world.phys.Vec3 away = p.position().subtract(bot.position()).multiply(1, 0, 1).normalize();
			net.minecraft.world.phys.Vec3 to = bot.position().add(away.scale(2.3));
			float yaw = (float) Math.toDegrees(Math.atan2(-(bot.getX() - to.x), bot.getZ() - to.z));
			p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), to.x, to.y, to.z, java.util.Set.of(), yaw, 0, true);
		}
	}

	private static void stopDrill(ClientGameTestContext context, TestSingleplayerContext world) {
		context.runOnClient(client -> client.player.connection.sendCommand("sparbot drill stop"));
		world.getServer().waitFor(server -> !SparBot.drills().inDrill(human(server).getUUID()), 200);
	}

	private static net.minecraft.world.entity.Entity drillBot(net.minecraft.client.Minecraft client) {
		for (net.minecraft.world.entity.Entity e : client.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.player.Player p && p.getName().getString().startsWith("Drill")) {
				return e;
			}
		}
		return null;
	}

	private static void at(TestSingleplayerContext world, double x, double y, double z, float yaw, float pitch, net.minecraft.world.level.GameType mode) {
		world.getServer().runOnServer(server -> {
			var player = human(server);
			player.setGameMode(mode);
			player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
		});
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name) {
		world.getConnection().waitForChunksRender(false, 600);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(4);
		context.takeScreenshot(name);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
	}

	/** A screenshot of Vaelor: the camera nine blocks from him, toward the middle of the arena, looking at him. */
	private static void shotBoss(ClientGameTestContext context, TestSingleplayerContext world, String name, int cx, int cz) {
		world.getServer().runOnServer(server -> {
			var boss = encounter().boss();
			var p = human(server);
			if (boss == null) {
				return;
			}
			double bx = boss.getX();
			double bz = boss.getZ();
			double ox = cx + 0.5 - bx;
			double oz = cz + 0.5 - bz;
			double len = Math.max(0.1, Math.hypot(ox, oz));
			double px = bx + ox / len * 9;
			double pz = bz + oz / len * 9;
			float yaw = (float) Math.toDegrees(Math.atan2(-(bx - px), bz - pz));
			p.teleportTo((net.minecraft.server.level.ServerLevel) p.level(), px, boss.getY() + 2.5, pz, java.util.Set.of(), yaw, 8, true);
		});
		context.waitTicks(3);
		shot(context, world, name);
	}

	private static io.github.flick256.sparbot.practice.colosseum.DeepEncounter encounter() {
		return SparBot.practice().crown().deep();
	}

	/** Hurts Vaelor as the player would (a player's blow), by {@code amount}. */
	private static void strike(TestSingleplayerContext world, float amount) {
		world.getServer().runOnServer(server -> {
			var p = human(server);
			var boss = encounter().boss();
			if (boss != null) {
				boss.hurtServer((net.minecraft.server.level.ServerLevel) p.level(), p.damageSources().playerAttack(p), amount);
			}
		});
	}

	/**
	 * Down the Well into the arena, Vaelor rising, his three phases (struck down to two thirds, then a third, then out),
	 * his kneeling, the Gate of Triumph, every reward taken in the Hall of Triumph, the climb, the Laurel Door, the Heart
	 * breaking and the rise into the colosseum. The champion must end up with all seven of his things.
	 */
	private static void deep(ClientGameTestContext context, TestSingleplayerContext world, int cx, int cz) {
		double[] pf = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.platform();
		at(world, cx + pf[0], pf[1], cz + pf[2], 0, 30, net.minecraft.world.level.GameType.SURVIVAL);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().waiting(), 400);
		double[] well = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.inTheWell();
		at(world, cx + well[0], well[1], cz + well[2], 0, 70, net.minecraft.world.level.GameType.SURVIVAL);
		world.getServer().waitFor(server -> encounter().fighting(), 200);
		context.waitTicks(70);
		shot(context, world, "deep-leap");
		world.getServer().waitFor(server -> encounter().fighting() && encounter().boss() != null && !encounter().boss().waiting(), 900);
		world.getServer().runOnServer(server -> human(server).setGameMode(net.minecraft.world.level.GameType.CREATIVE));
		double[] bt = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.beforeThrone();
		at(world, cx + bt[0], bt[1], cz + bt[2], (float) bt[3], -8, net.minecraft.world.level.GameType.CREATIVE);
		context.waitTicks(16);
		shotBoss(context, world, "deep-vaelor-rises", cx, cz);
		context.waitTicks(80);
		shotBoss(context, world, "deep-fight", cx, cz);
		strike(world, 260);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().action() == io.github.flick256.sparbot.content.VaelorBoss.Action.ROAR,
			400);
		context.waitTicks(25);
		shotBoss(context, world, "deep-phase-two", cx, cz);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().phase() == 2
			&& encounter().boss().action() != io.github.flick256.sparbot.content.VaelorBoss.Action.ROAR, 400);
		context.waitTicks(70);
		shotBoss(context, world, "deep-phase-two-fight", cx, cz);
		strike(world, 260);
		world.getServer().waitFor(server -> encounter().boss() != null && encounter().boss().phase() == 3, 600);
		shotBoss(context, world, "deep-phase-three", cx, cz);
		context.waitTicks(90);
		shotBoss(context, world, "deep-phase-three-fight", cx, cz);
		strike(world, 3000);
		context.waitTicks(40);
		shotBoss(context, world, "deep-vaelor-kneels", cx, cz);
		world.getServer().waitFor(server -> encounter().victorious(), 400);
		context.waitTicks(70);
		double[] av = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.avenue();
		at(world, cx + av[0], av[1], cz + av[2], (float) av[3], 0, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(40);
		shot(context, world, "deep-gate-of-triumph");
		double[] hall = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.hall();
		at(world, cx + hall[0], hall[1], cz + hall[2], (float) hall[3], 4, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(40);
		shot(context, world, "deep-hall-of-triumph");
		for (int i = 0; i < 7; i++) {
			double[] by = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.byPedestal(i);
			at(world, cx + by[0], by[1], cz + by[2], 0, 0, net.minecraft.world.level.GameType.SURVIVAL);
			context.waitTicks(10);
		}
		world.getServer().runOnServer(server -> {
			var p = human(server);
			for (net.minecraft.world.item.Item item : io.github.flick256.sparbot.content.SparBotContent.REWARDS) {
				if (!p.getInventory().contains(new net.minecraft.world.item.ItemStack(item))) {
					throw new AssertionError("the champion didn't get " + item + " from its pedestal");
				}
			}
		});
		double[] door = io.github.flick256.sparbot.practice.colosseum.DeepSurvey.byLaurelDoor();
		at(world, cx + door[0], door[1], cz + door[2], 0, 0, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(30);
		at(world, cx + pf[0], pf[1], cz + pf[2], 0, -70, net.minecraft.world.level.GameType.SURVIVAL);
		context.waitTicks(50);
		shot(context, world, "deep-ascension");
		world.getServer().waitFor(server -> human(server).getY() > io.github.flick256.sparbot.practice.colosseum.DeepSurvey.fieldTop() + 3, 600);
		world.getServer().runOnServer(server -> {
			var p = human(server);
			p.connection.teleport(p.getX(), p.getY(), p.getZ(), 180, -15);
		});
		context.waitTicks(30);
		shot(context, world, "deep-champion");
		world.getServer().waitFor(server -> !encounter().running(), 800);
		world.getServer().runOnServer(server -> {
			var player = human(server);
			var champion = server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath("sparbot", "hollow_crown/champion"));
			if (champion == null || !player.getAdvancements().getOrStartProgress(champion).isDone()) {
				throw new AssertionError("the champion should have the advancement Unbroken No More");
			}
			var top = player.level().getBlockState(new net.minecraft.core.BlockPos(cx, io.github.flick256.sparbot.practice.colosseum.DeepSurvey.fieldTop(), cz));
			if (top.isAir()) {
				throw new AssertionError("the hatch in the field was left open");
			}
		});
	}

	private static net.minecraft.server.level.ServerPlayer human(net.minecraft.server.MinecraftServer server) {
		return server.getPlayerList().getPlayers().stream().filter(p -> !(p instanceof io.github.flick256.sparbot.bot.BotPlayer)).findFirst().orElse(null);
	}

	/** Puts the player (in spectator, so nothing is in the way) at a viewpoint and takes a screenshot once the chunks show. */
	private static void view(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			var player = human(server);
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			player.teleportTo((net.minecraft.server.level.ServerLevel) player.level(), x, y, z, java.util.Set.of(), yaw, pitch, true);
		});
		context.waitTicks(40);
		// The near chunks for certain; the far ones as far as they get in a few seconds (software rendering is slow).
		world.getConnection().waitForChunksRender(false, 2400);
		context.waitTicks(100);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		context.waitTicks(10);
		context.takeScreenshot(name);
		context.getInput().pressKey(GLFW.GLFW_KEY_F1);
		world.getServer().runOnServer(server -> human(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
	}
}
