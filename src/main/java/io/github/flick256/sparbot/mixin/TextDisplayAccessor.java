package io.github.flick256.sparbot.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Text displays' setters are private in 26.2; the practice world's floating labels need them. */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
	@Invoker("setText")
	void sparbot$setText(Component text);

	@Invoker("setBackgroundColor")
	void sparbot$setBackgroundColor(int argb);

	@Invoker("setLineWidth")
	void sparbot$setLineWidth(int width);
}
