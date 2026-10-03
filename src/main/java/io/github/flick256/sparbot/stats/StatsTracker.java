package io.github.flick256.sparbot.stats;

import io.github.flick256.sparbot.SparBot;
import io.github.flick256.sparbot.bot.BotPlayer;
import java.util.IdentityHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * Feeds vanilla damage and death events into each bot's {@link io.github.flick256.sparbot.core.stats.FightStats}.
 *
 * <p>Fabric's AFTER_DAMAGE reports the amount <i>before</i> armor, enchantments and absorption are
 * applied, so the real damage is measured as the health (plus absorption) the victim actually lost,
 * snapshotted in ALLOW_DAMAGE. This listener only observes: it always allows the damage.
 */
public final class StatsTracker {
	private static final Map<LivingEntity, Float> HEALTH_BEFORE = new IdentityHashMap<>();

	private StatsTracker() {
	}

	public static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof BotPlayer || source.getEntity() instanceof BotPlayer) {
				HEALTH_BEFORE.put(entity, entity.getHealth() + entity.getAbsorptionAmount());
			}
			return true;
		});
		// Damage cancelled after ALLOW_DAMAGE never reaches AFTER_DAMAGE; drop such snapshots each tick.
		ServerTickEvents.END_SERVER_TICK.register(server -> HEALTH_BEFORE.clear());
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StatsTracker::afterDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register(StatsTracker::afterDeath);
	}

	private static void afterDamage(LivingEntity victim, DamageSource source, float baseDamage, float damageBeforeArmor, boolean blocked) {
		Float before = HEALTH_BEFORE.remove(victim);
		if (before == null) {
			return;
		}
		float lost = Math.max(0, before - Math.max(0, victim.getHealth()) - victim.getAbsorptionAmount());
		if (victim instanceof BotPlayer bot) {
			bot.bot().stats().recordDamageTaken(lost);
		}
		// Count only the primary target of the bot's own melee click (not sweeps or other damage it causes).
		if (source.getEntity() instanceof BotPlayer attacker && source.getDirectEntity() == attacker
			&& attacker.bot().isAttacking(victim.getId()) && lost > 0) {
			attacker.bot().stats().recordHit(lost, attacker.bot().attackWouldCrit());
		}
	}

	private static void afterDeath(LivingEntity victim, DamageSource source) {
		HEALTH_BEFORE.remove(victim);
		if (victim instanceof BotPlayer bot) {
			bot.bot().stats().recordDeath();
			SparBot.LOGGER.info("Bot {} died: {}", bot.bot().name(), source.getLocalizedDeathMessage(victim).getString());
		}
		if (source.getEntity() instanceof BotPlayer killer && killer != victim) {
			killer.bot().stats().recordKill();
		}
	}
}
