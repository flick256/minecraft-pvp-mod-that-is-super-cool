package io.github.flick256.sparbot.content;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
import org.jspecify.annotations.Nullable;

/**
 * Starfall, the bow that hung behind Vaelor's throne. Drawn like a bow, but its arrows leave the string a fifth
 * faster, straighter, and with 3.5 base damage instead of 2: a full draw does about 13 where a bow does 6, before
 * enchantments, which it takes as well as a bow does.
 */
public class StarfallItem extends BowItem {
	public static final double BASE_DAMAGE = 3.5;
	public static final float SPEED = 1.2F;

	public StarfallItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float power, float uncertainty, float angle,
		@Nullable LivingEntity target) {
		if (projectile instanceof AbstractArrow arrow) {
			arrow.setBaseDamage(BASE_DAMAGE);
		}
		projectile.shootFromRotation(shooter, shooter.getXRot(), shooter.getYRot() + angle, 0.0F, power * SPEED, uncertainty * 0.4F);
	}
}
