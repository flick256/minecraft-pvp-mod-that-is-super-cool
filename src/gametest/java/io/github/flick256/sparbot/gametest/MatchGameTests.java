package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.core.match.GameMode;
import io.github.flick256.sparbot.core.match.MatchState;
import io.github.flick256.sparbot.core.stats.EloLadder;
import io.github.flick256.sparbot.match.Arena;
import io.github.flick256.sparbot.match.Match;
import io.github.flick256.sparbot.match.MatchManager;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Milestone 5: matches, arenas, rounds, cleanup, forfeits, Elo and player inventory safety. */
public class MatchGameTests {
	@GameTest(maxTicks = 2, padding = 24)
	public void arenaResetRemovesCrystalsAndTntCartsWithoutDetonatingThem(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "hazardreset");
		var level = helper.getLevel();
		var cart = new net.minecraft.world.entity.vehicle.minecart.MinecartTNT(net.minecraft.world.entity.EntityTypes.TNT_MINECART, level);
		var crystal = new net.minecraft.world.entity.boss.enderdragon.EndCrystal(net.minecraft.world.entity.EntityTypes.END_CRYSTAL, level);
		var outside = new net.minecraft.world.entity.vehicle.minecart.MinecartTNT(net.minecraft.world.entity.EntityTypes.TNT_MINECART, level);
		cart.setPos(helper.absoluteVec(new Vec3(2.5, 1, 2.5)));
		crystal.setPos(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
		outside.setPos(helper.absoluteVec(new Vec3(20.5, 1, 3.5)));
		level.addFreshEntity(cart); level.addFreshEntity(crystal); level.addFreshEntity(outside);
		try {
			SparBot.matches().arenas().reset(level.getServer(), arena);
			helper.assertTrue(cart.isRemoved() && crystal.isRemoved(), "old explosive utility is discarded before the next round");
			helper.assertFalse(outside.isRemoved(), "reset does not remove carts outside the arena boundary");
			helper.assertTrue(helper.getBlockState(new BlockPos(2, 0, 2)).is(Blocks.STONE), "cleanup does not detonate the old utility");
		} finally { cart.discard(); crystal.discard(); outside.discard(); }
		helper.succeed();
	}
	private static final GameMode QUICK = new GameMode("test_quick", "Quick", "", "basic_sword", 2, 60, 1, "health");
	private static final GameMode ONE_ROUND = new GameMode("test_one", "One round", "", "basic_sword", 1, 60, 1, "health");

	/** Builds the walled test arena and registers it (with spawns) under a unique id. */
	static Arena arena(GameTestHelper helper, String prefix) throws Exception {
		TestSupport.arena(helper);
		ServerLevel level = helper.getLevel();
		String id = TestSupport.uniqueName(prefix).toLowerCase();
		SparBot.matches().arenas().create(id, level, helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(7, 4, 7)));
		Vec3 a = helper.absoluteVec(new Vec3(2.5, 1, 3.5));
		Vec3 b = helper.absoluteVec(new Vec3(5.5, 1, 4.5));
		SparBot.matches().arenas().setSpawn(id, true, new Arena.Spawn(a.x, a.y, a.z, -90));
		return SparBot.matches().arenas().setSpawn(id, false, new Arena.Spawn(b.x, b.y, b.z, 90));
	}

	@GameTest(maxTicks = 3000)
	public void botVsBotMatchPlaysToAWinnerAndResetsTheArena(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "duelarena");
		Bot pro = TestSupport.spawnBot(helper, "MPro", 2.5, 1, 3.5, -90, "pro", "basic_sword");
		Bot noob = TestSupport.spawnBot(helper, "MNoob", 5.5, 1, 4.5, 90, "beginner", "basic_sword");
		AtomicReference<Match> done = new AtomicReference<>();
		SparBot.matches().start(helper.getLevel().getServer(), QUICK, arena, MatchManager.fighterFor(pro), MatchManager.fighterFor(noob), done::set);
		// Mid-round, someone "places a web": the arena reset between rounds must remove it.
		helper.runAfterDelay(40, () -> helper.setBlock(new BlockPos(4, 1, 1), Blocks.COBWEB));
		helper.succeedWhen(() -> {
			Match match = done.get();
			helper.assertTrue(match != null, "match still running");
			helper.assertTrue(match.state().winner() != null, "the match has a winner");
			int winnerRounds = Math.max(match.state().winsA(), match.state().winsB());
			helper.assertValueEqual(winnerRounds, QUICK.roundsToWin(), "winner's rounds");
			helper.assertTrue(helper.getBlockState(new BlockPos(4, 1, 1)).isAir(), "the web was cleared by the arena reset");
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, arena.box()).isEmpty(), "no drops left in the arena");
			EloLadder ladder = SparBot.matches().elo().ladder();
			helper.assertTrue(ladder.get(EloLadder.botKey("pro", "balanced")).games() > 0, "result recorded on the Elo ladder");
			helper.assertFalse(pro.inMatch() || noob.inMatch(), "bots released after the match");
			SparBot.LOGGER.info("Test match result: {} {} {}", pro.name(), match.state().score(), noob.name());
			TestSupport.remove(pro, noob);
		});
	}

	@GameTest(maxTicks = 2000)
	public void humanGetsTheirOwnInventoryBackAfterLosing(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "humanarena");
		ServerLevel level = helper.getLevel();
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 2.5, 1, 3.5);
		human.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
		human.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 3));
		Bot bot = TestSupport.spawnBot(helper, "Rival", 5.5, 1, 4.5, 90, "pro", "basic_sword");
		AtomicReference<Match> done = new AtomicReference<>();
		SparBot.matches().start(level.getServer(), ONE_ROUND, arena, MatchManager.fighterFor(human), MatchManager.fighterFor(bot), done::set);
		helper.startSequence()
			.thenWaitUntil(() -> helper.assertTrue(SparBot.matches().match(arena.id()).map(m -> m.state().phase() == MatchState.Phase.FIGHTING).orElse(false),
				"round started"))
			.thenExecute(() -> {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(human.getUUID());
				helper.assertTrue(p.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "the human fights in the mode's kit");
				p.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			})
			.thenIdle(5)
			.thenExecute(() -> {
				helper.assertTrue(done.get() != null && done.get().state().winner() == MatchState.Side.B, "the bot won");
				// The human clicks "Respawn".
				ServerPlayer dead = level.getServer().getPlayerList().getPlayer(human.getUUID());
				dead.connection.handleClientCommand(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
			})
			.thenWaitUntil(() -> {
				ServerPlayer p = level.getServer().getPlayerList().getPlayer(human.getUUID());
				helper.assertTrue(p.getInventory().getItem(0).is(Items.DIRT) && p.getInventory().getItem(0).getCount() == 5, "dirt is back");
				helper.assertTrue(p.getInventory().getItem(20).is(Items.DIAMOND), "diamonds are back");
				helper.assertFalse(p.getInventory().contains(new ItemStack(Items.DIAMOND_SWORD)), "no kit items left over");
			})
			.thenExecute(() -> {
				helper.assertFalse(SparBot.matches().backups().has(human.getUUID()), "backup cleaned up");
				TestSupport.remove(bot);
				TestSupport.removeRealPlayer(level.getServer().getPlayerList().getPlayer(human.getUUID()));
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 400)
	public void aFighterLeavingForfeitsTheMatch(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "forfeitarena");
		Bot stayer = TestSupport.spawnBot(helper, "Stayer", 2.5, 1, 3.5, -90, "intermediate", "basic_sword");
		Bot quitter = TestSupport.spawnBot(helper, "Quitter", 5.5, 1, 4.5, 90, "intermediate", "basic_sword");
		AtomicReference<Match> done = new AtomicReference<>();
		SparBot.matches().start(helper.getLevel().getServer(), QUICK, arena, MatchManager.fighterFor(stayer), MatchManager.fighterFor(quitter), done::set);
		helper.runAfterDelay(30, () -> TestSupport.remove(quitter));
		helper.succeedWhen(() -> {
			helper.assertTrue(done.get() != null, "match ended");
			helper.assertTrue(done.get().state().winner() == MatchState.Side.A, "the one who stayed wins");
			helper.assertFalse(stayer.inMatch(), "released");
			TestSupport.remove(stayer);
		});
	}

	@GameTest(maxTicks = 6000)
	public void benchmarkPlaysASeriesAndRatesTheLevels(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "bencharena");
		EloLadder ladder = SparBot.matches().elo().ladder();
		String pro = EloLadder.botKey("pro", "wtap_combo");
		String noob = EloLadder.botKey("beginner", "balanced");
		int gamesBefore = ladder.get(pro).games();
		io.github.flick256.sparbot.match.Benchmark bench = new io.github.flick256.sparbot.match.Benchmark(arena,
			SparBot.matches().modes().get("benchmark").orElseThrow(), SparBot.profiles().get("pro").orElseThrow(),
			SparBot.playstyles().resolve("wtap_combo"), SparBot.profiles().get("beginner").orElseThrow(), SparBot.playstyles().resolve("balanced"), 3, null);
		SparBot.matches().addBenchmark(bench);
		helper.succeedWhen(() -> {
			helper.assertFalse(SparBot.matches().benchmarks().contains(bench), "benchmark still running");
			helper.assertValueEqual(bench.played(), 3, "matches played");
			helper.assertValueEqual(ladder.get(pro).games() - gamesBefore, 3, "every match rated");
			helper.assertTrue(ladder.get(pro).rating > ladder.get(noob).rating, "the Pro rates above the Beginner");
			helper.assertTrue(SparBot.bots().all().stream().noneMatch(b -> b.name().startsWith("Bench")), "benchmark bots removed");
			SparBot.LOGGER.info("Benchmark test: pro/wtap {}-{} beginner", bench.winsA(), bench.winsB());
		});
	}
	@GameTest(maxTicks = 100)
	public void stoppingUhcRestoresRulesInventoryRecordingAndRunsCleanupOnce(GameTestHelper helper) throws Exception {
		Arena arena = arena(helper, "stopcleanup");
		ServerLevel level = helper.getLevel();
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 2.5, 1, 3.5);
		human.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
		boolean regenerationBefore = io.github.flick256.sparbot.match.MatchRules.regenerates(human);
		Bot bot = TestSupport.dummy(helper, "StopRival", 5.5, 1, 4.5, 90, "basic_sword");
		var mode = new GameMode("test_stop_uhc", "Stop UHC", "", "basic_sword", 1, 60, 2, "health", false);
		var callbacks = new java.util.concurrent.atomic.AtomicInteger();
		Match match = SparBot.matches().start(level.getServer(), mode, arena, MatchManager.fighterFor(human), MatchManager.fighterFor(bot),
			finished -> callbacks.incrementAndGet());
		match.enableReviewRecording();
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(human.getMainHandItem().is(Items.DIAMOND_SWORD), "match kit was actually applied");
			helper.assertFalse(io.github.flick256.sparbot.match.MatchRules.regenerates(human), "UHC disabled normal regeneration");
			helper.assertTrue(SparBot.recorder().active().stream().anyMatch(n -> n.startsWith("match-" + arena.id())), "review recording started");
			helper.assertTrue(SparBot.matches().stop(level.getServer(), arena.id()), "first stop ends the match");
			helper.assertFalse(SparBot.matches().stop(level.getServer(), arena.id()), "second stop is a no-op");
			helper.assertTrue(match.forfeit(), "stopped match cannot award career progress");
			helper.assertValueEqual(callbacks.get(), 1, "temporary opponent cleanup callback ran exactly once");
			helper.assertTrue(human.getMainHandItem().is(Items.EMERALD) && human.getMainHandItem().getCount() == 7, "original inventory restored");
			helper.assertTrue(io.github.flick256.sparbot.match.MatchRules.regenerates(human) == regenerationBefore, "original regeneration rule restored");
			helper.assertFalse(SparBot.matches().backups().has(human.getUUID()), "restored backup removed");
			helper.assertTrue(SparBot.recorder().active().stream().noneMatch(n -> n.startsWith("match-" + arena.id())), "stopped recording was closed");
			TestSupport.remove(bot); TestSupport.removeRealPlayer(human); helper.succeed();
		});
	}

	@GameTest(maxTicks = 100)
	public void corruptBackupLeavesInventoryUnchangedAndIsPreserved(GameTestHelper helper) throws Exception {
		ServerLevel level = helper.getLevel();
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 2.5, 1, 3.5);
		java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("sparbot-corrupt-backup-");
		java.nio.file.Path file = dir.resolve(human.getUUID() + ".nbt");
		io.github.flick256.sparbot.match.PlayerBackup backup = new io.github.flick256.sparbot.match.PlayerBackup(dir);
		try {
			human.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
			human.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 3));
			backup.save(human);

			// Corrupt the final slot: restoring slot 0 before decoding this entry would partially
			// overwrite the emeralds below before noticing the invalid item id.
			var saved = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
			var items = saved.getListOrEmpty("items");
			var invalid = new net.minecraft.nbt.CompoundTag();
			invalid.putString("id", "sparbot:not_a_real_item");
			invalid.putInt("count", 1);
			items.set(items.size() - 1, invalid);
			net.minecraft.nbt.NbtIo.writeCompressed(saved, file);

			human.getInventory().setItem(0, new ItemStack(Items.EMERALD, 7));
			helper.assertFalse(backup.restore(human), "malformed slot fails the restore");
			helper.assertTrue(human.getInventory().getItem(0).is(Items.EMERALD)
				&& human.getInventory().getItem(0).getCount() == 7, "inventory remains unchanged when any slot fails decoding");
			helper.assertTrue(backup.has(human.getUUID()), "corrupt backup remains for recovery instead of being silently deleted");
		} finally {
			java.nio.file.Files.deleteIfExists(file);
			java.nio.file.Files.deleteIfExists(dir);
			TestSupport.removeRealPlayer(human);
		}
		helper.succeed();
	}

}
