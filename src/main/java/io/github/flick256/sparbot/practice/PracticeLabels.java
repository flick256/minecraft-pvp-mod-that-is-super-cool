package io.github.flick256.sparbot.practice;

import com.mojang.math.Transformation;
import io.github.flick256.sparbot.mixin.DisplayAccessor;
import io.github.flick256.sparbot.mixin.TextDisplayAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Floating names in the practice world (text displays that always face the viewer): over every hub pad,
 * over each arena and over the pads back to the hub. They carry a tag, so a rebuild removes the old
 * ones before it puts new ones up.
 */
final class PracticeLabels {
	static final String TAG = "sparbot_practice";

	private PracticeLabels() {
	}

	/** Removes every practice label in the box. */
	static void clear(ServerLevel level, AABB box) {
		for (Entity e : level.getEntities((Entity) null, box, e -> e.entityTags().contains(TAG))) {
			e.discard();
		}
	}

	/**
	 * A label centred at x, y, z.
	 *
	 * @param scale 1 is the size of a name tag
	 * @param background ARGB behind the text (0 for none)
	 */
	static void put(ServerLevel level, double x, double y, double z, Component text, float scale, int background) {
		Display.TextDisplay label = new Display.TextDisplay(EntityTypes.TEXT_DISPLAY, level);
		label.setPos(x, y, z);
		((TextDisplayAccessor) label).sparbot$setText(text);
		((TextDisplayAccessor) label).sparbot$setBackgroundColor(background);
		((TextDisplayAccessor) label).sparbot$setLineWidth(240);
		((DisplayAccessor) label).sparbot$setBillboard(Display.BillboardConstraints.CENTER);
		((DisplayAccessor) label).sparbot$setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale),
			new Quaternionf()));
		((DisplayAccessor) label).sparbot$setViewRange(4.0F);
		label.addTag(TAG);
		level.addFreshEntity(label);
	}
}
