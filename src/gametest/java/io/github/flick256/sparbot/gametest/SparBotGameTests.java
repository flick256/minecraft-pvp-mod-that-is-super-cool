package io.github.flick256.sparbot.gametest;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.Bot;
import io.github.flick256.sparbot.bot.BotPlayer;
import io.github.flick256.sparbot.bot.MortalityGuard;
import io.github.flick256.sparbot.kit.KitApplier;
import io.github.flick256.sparbot.core.kit.Kit;
import io.github.flick256.sparbot.core.kit.Kits;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * In-game tests for the hard rules: a bot is a fully mortal survival player, takes damage exactly like a
 * real player, and can fight and die for real.
 */
public class SparBotGameTests {
	@GameTest(maxTicks = 40)
	public void spawnedBotIsAMortalSurvivalPlayer(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Mortal", 4, 1, 4, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			helper.assertTrue(body.tickCount > 5, "wait a few ticks");
			assertMortal(helper, body);
			helper.assertTrue(body.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET), "kit armor equipped");
			helper.assertTrue(body.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "kit sword in hotbar slot 0");
			helper.assertFalse(body.isInvulnerableTo(level, level.damageSources().generic()), "bot must be damageable");
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 80)
	public void invulnerableBotIsRemovedAndComesBackMortal(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Cheat", 4, 1, 4, 0, "intermediate", "basic_sword");
		TestSupport.body(bot).getAbilities().invulnerable = true;
		TestSupport.body(bot).setInvulnerable(true);
		helper.startSequence()
			.thenWaitUntil(() -> assertGone(helper, bot, "a bot with the invulnerable ability must be removed by the mortality guard"))
			.thenExecute(() -> {
				// The removed bot's saved player data says "invulnerable". Bots never load saved data (26.2
				// loads it in the login configuration phase, PrepareSpawnTask, which bots skip), so a new
				// bot with the same name must start clean and mortal.
				Bot again = TestSupport.spawnBotNamed(helper, bot.name(), 4, 1, 4, 0, "intermediate", "basic_sword");
				assertMortal(helper, TestSupport.body(again));
				TestSupport.remove(again);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void creativeBotIsRemovedImmediately(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Creative", 4, 1, 4, 0, "intermediate", "basic_sword");
		TestSupport.body(bot).setGameMode(GameType.CREATIVE);
		helper.succeedWhen(() -> assertGone(helper, bot, "a creative-mode bot must be removed by the mortality guard"));
	}

	@GameTest(maxTicks = 60)
	public void botTakesExactlyTheDamageARealPlayerTakes(GameTestHelper helper) {
		// Enchanted armor (Protection) for health; health must match for every source and amount.
		compareDamage(helper, "mctiers_sword_recreation", false);
		helper.succeed();
	}

	@GameTest(maxTicks = 60)
	public void botArmorWearsExactlyLikeARealPlayers(GameTestHelper helper) {
		// Unenchanted armor so durability loss is deterministic (Unbreaking skips loss at random).
		compareDamage(helper, "basic_sword", true);
		helper.succeed();
	}

	private static void compareDamage(GameTestHelper helper, String kitId, boolean compareDurability) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Parity", 2, 1, 2, 0, "intermediate", kitId);
		BotPlayer botBody = TestSupport.body(bot);
		ServerPlayer human = TestSupport.spawnRealPlayer(helper, 5, 1, 5);
		KitApplier.apply(human, TestSupport.kit(kitId));
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 5));
		zombie.setNoAi(true);
		ServerLevel level = helper.getLevel();
		try {
			List<Function<ServerLevel, DamageSource>> sources = List.of(
				l -> l.damageSources().mobAttack(zombie),
				l -> l.damageSources().generic(),
				l -> l.damageSources().fall(),
				l -> l.damageSources().inFire(),
				l -> l.damageSources().explosion(null, null),
				l -> l.damageSources().playerAttack(human));
			float[] amounts = {3, 6.5F, 9, 14};
			for (Function<ServerLevel, DamageSource> sourceFactory : sources) {
				for (float amount : amounts) {
					for (ServerPlayer p : new ServerPlayer[] {botBody, human}) {
						p.setHealth(p.getMaxHealth());
						p.setAbsorptionAmount(0);
						p.invulnerableTime = 0;
					}
					DamageSource source = sourceFactory.apply(level);
					botBody.hurtServer(level, source, amount);
					human.hurtServer(level, sourceFactory.apply(level), amount);
					helper.assertValueEqual(botBody.getHealth(), human.getHealth(),
						"health after " + amount + " " + source.getMsgId() + " damage (bot vs real player, kit " + kitId + ")");
				}
			}
			if (compareDurability) {
				for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					helper.assertTrue(human.getItemBySlot(slot).getDamageValue() > 0, slot.getName() + " took durability damage");
					helper.assertValueEqual(botBody.getItemBySlot(slot).getDamageValue(), human.getItemBySlot(slot).getDamageValue(),
						slot.getName() + " durability loss (bot vs real player)");
				}
			}
		} finally {
			TestSupport.remove(bot);
			TestSupport.removeRealPlayer(human);
		}
	}

	@GameTest(maxTicks = 100)
	public void botTakesFallDamage(GameTestHelper helper) {
		helper.setBlock(4, 0, 4, net.minecraft.world.level.block.Blocks.STONE);
		Bot bot = TestSupport.spawnBot(helper, "Faller", 4.5, 7, 4.5, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		helper.succeedWhen(() -> {
			helper.assertTrue(body.onGround() && body.tickCount > 5, "wait for landing");
			// 6 block drop: vanilla fall damage = fall distance - 3 safe blocks = 3 (armor does not reduce falls).
			helper.assertTrue(body.getHealth() <= 17.5F && body.getHealth() >= 16.5F, "expected ~3 fall damage, health " + body.getHealth());
			TestSupport.remove(bot);
		});
	}

	@GameTest(maxTicks = 60)
	public void botReceivesKnockbackWhenHit(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot attacker = TestSupport.spawnBot(helper, "Hitter", 2.5, 1, 3.5, -90, "intermediate", "basic_sword");
		Bot victim = TestSupport.spawnBot(helper, "Victim", 4.5, 1, 3.5, 90, "intermediate", "basic_sword");
		BotPlayer a = TestSupport.body(attacker);
		BotPlayer v = TestSupport.body(victim);
		double startX = v.getX();
		helper.runAfterDelay(5, () -> a.attack(v));
		helper.succeedWhen(() -> {
			helper.assertTrue(v.getHealth() < v.getMaxHealth(), "victim was hurt");
			helper.assertTrue(v.getX() - startX > 0.3, "victim was knocked back, moved " + (v.getX() - startX));
			TestSupport.remove(attacker, victim);
		});
	}

	/**
	 * One sequential test because keepInventory is a level-wide gamerule and GameTests run in parallel:
	 * die without keepInventory (kit drops), respawn manually (new body, fresh kit), then die with
	 * keepInventory (nothing drops).
	 */
	@GameTest(maxTicks = 200)
	public void deathFollowsKeepInventoryAndRespawnStartsANewRound(GameTestHelper helper) {
		TestSupport.arena(helper);
		ServerLevel level = helper.getLevel();
		boolean originalKeepInventory = level.getGameRules().get(GameRules.KEEP_INVENTORY);
		Bot bot = TestSupport.spawnBot(helper, "Dier", 3.5, 1, 3.5, 0, "intermediate", "basic_sword");
		BotPlayer first = TestSupport.body(bot);
		helper.startSequence()
			.thenIdle(5)
			.thenExecute(() -> {
				level.getGameRules().set(GameRules.KEEP_INVENTORY, false, level.getServer());
				first.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				helper.assertTrue(first.isDeadOrDying(), "bot is dead");
				helper.assertValueEqual(bot.stats().deaths(), 1, "deaths");
				boolean droppedSword = level.getEntitiesOfClass(ItemEntity.class, TestSupport.area(helper)).stream()
					.anyMatch(e -> e.getItem().is(Items.DIAMOND_SWORD));
				helper.assertTrue(droppedSword, "dead bot dropped its sword (keepInventory=false)");
				level.getEntitiesOfClass(ItemEntity.class, TestSupport.area(helper)).forEach(e -> e.discard());
				helper.assertTrue(SparBot.bots().respawn(level.getServer(), bot), "manual respawn works");
			})
			.thenIdle(3)
			.thenExecute(() -> {
				BotPlayer second = TestSupport.body(bot);
				helper.assertTrue(second != first, "respawn creates a new body");
				helper.assertTrue(second.isAlive() && second.getHealth() == second.getMaxHealth(), "respawned at full health");
				helper.assertTrue(second.getInventory().getItem(0).is(Items.DIAMOND_SWORD), "kit re-equipped for the next round");
				assertMortal(helper, second);
				level.getGameRules().set(GameRules.KEEP_INVENTORY, true, level.getServer());
				second.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
			})
			.thenIdle(3)
			.thenExecute(() -> {
				level.getGameRules().set(GameRules.KEEP_INVENTORY, originalKeepInventory, level.getServer());
				helper.assertTrue(TestSupport.body(bot).isDeadOrDying(), "bot died again");
				helper.assertValueEqual(bot.stats().deaths(), 2, "deaths");
				helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, TestSupport.area(helper)).isEmpty(), "no drops with keepInventory=true");
				TestSupport.remove(bot);
			})
			.thenSucceed();
	}

	@GameTest(maxTicks = 40)
	public void totemIsReallyConsumed(GameTestHelper helper) {
		TestSupport.arena(helper);
		ServerLevel level = helper.getLevel();
		Bot bot = TestSupport.spawnBot(helper, "Totem", 3.5, 1, 3.5, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		body.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
		helper.runAfterDelay(5, () -> {
			body.hurtServer(level, level.damageSources().generic(), 1000);
			helper.assertTrue(body.isAlive(), "totem saved the bot");
			helper.assertTrue(body.getOffhandItem().isEmpty(), "totem was consumed");
			body.invulnerableTime = 0;
			body.hurtServer(level, level.damageSources().generic(), 1000);
			helper.assertTrue(body.isDeadOrDying(), "without a second totem the bot dies");
			TestSupport.remove(bot);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 2400)
	public void twoBotsFightADuelToTheDeath(GameTestHelper helper) {
		TestSupport.arena(helper);
		Bot pro = TestSupport.spawnBot(helper, "Pro", 2.5, 1, 3.5, -90, "pro", "basic_sword");
		Bot beginner = TestSupport.spawnBot(helper, "Noob", 5.5, 1, 4.5, 90, "beginner", "basic_sword");
		pro.setAssignedTarget(TestSupport.body(beginner).getUUID());
		beginner.setAssignedTarget(TestSupport.body(pro).getUUID());
		helper.succeedWhen(() -> {
			boolean someoneDied = TestSupport.body(pro).isDeadOrDying() || TestSupport.body(beginner).isDeadOrDying();
			helper.assertTrue(someoneDied, "duel still running");
			int hits = pro.stats().hits() + beginner.stats().hits();
			helper.assertTrue(hits > 0, "the duel was decided by landed melee hits");
			SparBot.LOGGER.info("Duel finished: {} [{}] vs {} [{}]", pro.name(), pro.stats().summary(), beginner.name(), beginner.stats().summary());
			TestSupport.remove(pro, beginner);
		});
	}

	@GameTest(maxTicks = 40)
	public void removedBotLeavesTheServerLikeAPlayer(GameTestHelper helper) {
		Bot bot = TestSupport.spawnBot(helper, "Leaver", 4, 1, 4, 0, "intermediate", "basic_sword");
		BotPlayer body = TestSupport.body(bot);
		helper.runAfterDelay(3, () -> TestSupport.remove(bot));
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(bot.uuid()) == null, "bot still in the player list");
			helper.assertTrue(body.isRemoved(), "bot entity still in the world");
		});
	}

	@GameTest
	public void bundledKitsResolveAgainstTheLive262Registries(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (Kit kit : Kits.loadBundled().values()) {
			List<String> errors = KitApplier.resolveErrors(kit, level.registryAccess());
			helper.assertTrue(errors.isEmpty(), "kit " + kit.id() + " does not resolve: " + errors);
		}
		Kit broken = new Kit("broken", "Broken", "sword", TestSupport.kit("basic_sword").provenance(),
			new Kit.Armor(new Kit.KitItem("minecraft:diamond_boots", null, null, null), null, null, null), null,
			Map.of("0", new Kit.KitItem("minecraft:not_an_item", null, null, null),
				"1", new Kit.KitItem("minecraft:diamond_sword", null, Map.of("minecraft:not_an_enchantment", 1), null),
				"2", new Kit.KitItem("minecraft:diamond_sword", 64, null, null)),
			List.of());
		List<String> errors = KitApplier.resolveErrors(broken, level.registryAccess());
		helper.assertTrue(errors.stream().anyMatch(e -> e.contains("cannot be worn in the head slot")), "wrong armor slot detected: " + errors);
		helper.assertTrue(errors.stream().anyMatch(e -> e.contains("unknown item")), "unknown item detected: " + errors);
		helper.assertTrue(errors.stream().anyMatch(e -> e.contains("unknown enchantment")), "unknown enchantment detected: " + errors);
		helper.assertTrue(errors.stream().anyMatch(e -> e.contains("stacks to 1")), "over-stacked item detected: " + errors);
		helper.succeed();
	}

	private static void assertGone(GameTestHelper helper, Bot bot, String message) {
		helper.assertTrue(SparBot.bots().get(bot.name()).isEmpty(), message + " (still managed)");
		helper.assertTrue(helper.getLevel().getServer().getPlayerList().getPlayer(bot.uuid()) == null, message + " (still online)");
	}

	private static void assertMortal(GameTestHelper helper, BotPlayer body) {
		helper.assertTrue(MortalityGuard.violations(body).isEmpty(), "mortality violations: " + MortalityGuard.violations(body));
		helper.assertFalse(body.getAbilities().invulnerable, "invulnerable ability");
		helper.assertFalse(body.getAbilities().mayfly, "flight ability");
		helper.assertFalse(body.getAbilities().instabuild, "creative ability");
		helper.assertFalse(body.isInvulnerable(), "invulnerable entity flag");
		helper.assertTrue(body.gameMode() == GameType.SURVIVAL, "survival mode");
		helper.assertTrue(body.connection.hasClientLoaded(), "loaded, so no join protection");
		helper.assertFalse(body.isClientAuthoritative(), "server-authoritative so fall damage applies");
	}

	/**
	 * A bot whose brain is paused, made to look at the chest of a target {@code gap} blocks from its eyes
	 * (to the hitbox) and click every tick through the normal client emulation.
	 */
	private static Bot[] reachSetup(GameTestHelper helper, double gap) {
		TestSupport.arena(helper);
		Bot bot = TestSupport.spawnBot(helper, "Reacher", 1.5, 1, 3.5, -90, "pro", "basic_sword");
		io.github.flick256.sparbot.bot.BotTestAccess.pauseBrain(bot, true);
		Bot target = TestSupport.dummy(helper, "Edge", 1.5 + gap + 0.3, 1, 3.5, 90, "basic_sword");
		helper.onEachTick(() -> {
			BotPlayer body = bot.body();
			BotPlayer t = target.body();
			if (body == null || t == null) {
				return;
			}
			net.minecraft.world.phys.Vec3 eye = body.getEyePosition();
			net.minecraft.world.phys.Vec3 chest = t.position().add(0, 1.2, 0);
			double dx = chest.x - eye.x;
			double dz = chest.z - eye.z;
			body.setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
			body.setXRot((float) -Math.toDegrees(Math.atan2(chest.y - eye.y, Math.sqrt(dx * dx + dz * dz))));
			io.github.flick256.sparbot.bot.BotTestAccess.press(bot, new io.github.flick256.sparbot.core.act.Inputs(0, 0, 0, 0, false, false, false, true,
				false, -1));
		});
		return new Bot[] {bot, target};
	}

	@GameTest(maxTicks = 120)
	public void botsHaveNoExtraReach(GameTestHelper helper) {
		Bot[] b = reachSetup(helper, 3.15);
		helper.runAfterDelay(100, () -> {
			BotPlayer t = TestSupport.body(b[1]);
			helper.assertTrue(b[0].stats().swings() > 0, "the bot clicked at it");
			helper.assertValueEqual(t.getHealth(), t.getMaxHealth(), "health of a target 3.15 blocks away (vanilla reach is 3)");
			TestSupport.remove(b);
			helper.succeed();
		});
	}

	@GameTest(maxTicks = 120)
	public void botsHitWithinVanillaReach(GameTestHelper helper) {
		Bot[] b = reachSetup(helper, 2.8);
		helper.succeedWhen(() -> {
			BotPlayer t = TestSupport.body(b[1]);
			helper.assertTrue(t.getHealth() < t.getMaxHealth(), "a target 2.8 blocks away gets hit");
			TestSupport.remove(b);
		});
	}
}
