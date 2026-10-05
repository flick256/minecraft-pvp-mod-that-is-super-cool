package io.github.flick256.sparbot.mixin;

import com.mojang.math.Transformation;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Display entities' setters are private in 26.2; the practice world's floating labels need them. */
@Mixin(Display.class)
public interface DisplayAccessor {
	@Invoker("setBillboardConstraints")
	void sparbot$setBillboard(Display.BillboardConstraints constraints);

	@Invoker("setTransformation")
	void sparbot$setTransformation(Transformation transformation);

	@Invoker("setViewRange")
	void sparbot$setViewRange(float range);
}
