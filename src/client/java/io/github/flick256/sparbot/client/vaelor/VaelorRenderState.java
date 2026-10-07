package io.github.flick256.sparbot.client.vaelor;

import io.github.flick256.sparbot.content.VaelorBoss;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** What Vaelor's model needs each frame: what he's doing, how far into it, and which phase he's in. */
public class VaelorRenderState extends LivingEntityRenderState {
	public VaelorBoss.Action action = VaelorBoss.Action.SEATED;
	public float actionTime;
	public int phase = 1;
}
