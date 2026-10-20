package io.github.flick256.sparbot.record;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Routes observed combat facts to only the recordings containing their participants. */
public final class CombatReviewEvents {
	private CombatReviewEvents() { }

	public static void incoming(LivingEntity victim, DamageSource source, float amount) {
		List<UUID> participants = participants(victim, source);
		if (participants.isEmpty()) return;
		ItemStack using = victim.getUseItem();
		boolean raised = victim.isBlocking();
		boolean cooling = victim instanceof Player player && shieldCooling(player);
		RecorderHolder.event("incoming", name(victim) + " incoming=" + String.format(java.util.Locale.ROOT, "%.1f", amount)
			+ "; shield raised=" + raised + ", using=" + using.is(Items.SHIELD)
			+ ", cooldown=" + cooling + ", angle=" + angle(victim, source), participants);
	}

	public static void blocked(LivingEntity victim, DamageSource source, float incoming, float reduction, boolean raised, boolean usingShield,
		boolean cooling, String angle) {
		List<UUID> participants = participants(victim, source);
		if (!participants.isEmpty()) RecorderHolder.event("shield_check", "Shield " + name(victim) + " vs " + cause(source)
			+ ": incoming=" + String.format(java.util.Locale.ROOT, "%.1f", incoming)
			+ ", reduced=" + String.format(java.util.Locale.ROOT, "%.1f", reduction)
			+ "; raised=" + raised + ", using=" + usingShield + ", cooldown=" + cooling + ", angle=" + angle, participants);
	}

	public static void damage(LivingEntity victim, DamageSource source, float amount) {
		List<UUID> participants = participants(victim, source);
		if (!participants.isEmpty() && amount > 0) RecorderHolder.event("damage", name(victim) + " took " + String.format(java.util.Locale.ROOT, "%.1f", amount)
			+ " damage from " + cause(source), participants);
	}

	public static void death(LivingEntity victim, DamageSource source) {
		List<UUID> participants = participants(victim, source);
		if (!participants.isEmpty()) RecorderHolder.event("death", name(victim) + " was defeated by " + cause(source), participants);
	}

	private static List<UUID> participants(LivingEntity victim, DamageSource source) {
		List<UUID> ids = new ArrayList<>(2);
		if (victim instanceof Player) ids.add(victim.getUUID());
		Entity attacker = source.getEntity();
		if (attacker instanceof Player && !attacker.getUUID().equals(victim.getUUID())) ids.add(attacker.getUUID());
		return ids;
	}
	private static String name(Entity entity) { return entity.getName().getString(); }
	private static String cause(DamageSource source) { return source.getEntity() == null ? source.type().msgId() : name(source.getEntity()); }
	public static String angle(LivingEntity victim, DamageSource source) {
		Entity attacker = source.getEntity();
		if (attacker == null || attacker == victim) return "unknown";
		Vec3 toAttacker = attacker.position().subtract(victim.position()).normalize();
		double facing = victim.getViewVector(1.0F).dot(toAttacker);
		return facing > 0.5 ? "front" : facing < -0.5 ? "rear" : "side";
	}
	public static boolean shieldCooling(Player player) {
		return (player.getMainHandItem().is(Items.SHIELD) && player.getCooldowns().isOnCooldown(player.getMainHandItem()))
			|| (player.getOffhandItem().is(Items.SHIELD) && player.getCooldowns().isOnCooldown(player.getOffhandItem()));
	}

	private static final class RecorderHolder {
		static void event(String kind, String text, List<UUID> participants) {
			io.github.flick256.sparbot.SparBot.recorder().event(kind, text, participants.toArray(UUID[]::new));
		}
	}
}
