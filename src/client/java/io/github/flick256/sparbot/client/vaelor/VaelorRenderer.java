package io.github.flick256.sparbot.client.vaelor;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.flick256.sparbot.content.VaelorBoss;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/** Vaelor in the world: his model at 1.4 times its size (about four blocks tall), and what glows on him. */
public class VaelorRenderer extends MobRenderer<VaelorBoss, VaelorRenderState, VaelorModel> {
	static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("sparbot", "textures/entity/vaelor/vaelor.png");
	static final float SCALE = 1.4F;

	public VaelorRenderer(EntityRendererProvider.Context context) {
		super(context, new VaelorModel(context.bakeLayer(VaelorModel.LAYER)), 1.2F);
		addLayer(new VaelorGlowLayer(this));
	}

	@Override
	public Identifier getTextureLocation(VaelorRenderState state) {
		return TEXTURE;
	}

	@Override
	public VaelorRenderState createRenderState() {
		return new VaelorRenderState();
	}

	@Override
	public void extractRenderState(VaelorBoss entity, VaelorRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.action = entity.action();
		state.actionTime = entity.actionTime(partialTicks);
		state.phase = entity.phase();
	}

	@Override
	protected void scale(VaelorRenderState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	protected boolean shouldShowName(VaelorBoss entity, double distanceToCameraSq) {
		return false;
	}
}
