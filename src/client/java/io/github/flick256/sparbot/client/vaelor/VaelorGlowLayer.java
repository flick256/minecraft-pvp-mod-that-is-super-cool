package io.github.flick256.sparbot.client.vaelor;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.flick256.sparbot.content.VaelorBoss;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * What glows on him, drawn full-bright over the armour: the eyes in his visor, the star in his chest, the gems in his
 * crown and the runes down his blade. Violet in the first phase, gold in the second, red in the last; it goes out as
 * he kneels.
 */
public class VaelorGlowLayer extends RenderLayer<VaelorRenderState, VaelorModel> {
	private static final RenderType GLOW = RenderTypes.eyes(Identifier.fromNamespaceAndPath("sparbot", "textures/entity/vaelor/vaelor_glow.png"));
	private static final int[] COLOURS = {0xB27CFF, 0xFFC95A, 0xFF4636};

	public VaelorGlowLayer(RenderLayerParent<VaelorRenderState, VaelorModel> renderer) {
		super(renderer);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, VaelorRenderState state, float yRot, float xRot) {
		int rgb = COLOURS[Mth.clamp(state.phase - 1, 0, 2)];
		float bright = 1.0F;
		if (state.action == VaelorBoss.Action.KNEEL) {
			bright = Mth.clamp(1.0F - state.actionTime / VaelorBoss.DEATH_TICKS_CLIENT, 0.05F, 1.0F);
		} else if (state.action == VaelorBoss.Action.SEATED) {
			bright = 0.55F + 0.25F * Mth.sin(state.ageInTicks * 0.05F);
		} else if (state.action == VaelorBoss.Action.ROAR || state.action == VaelorBoss.Action.LANCE) {
			bright = 0.8F + 0.2F * Mth.sin(state.ageInTicks * 0.6F);
		}
		int r = (int) ((rgb >> 16 & 255) * bright);
		int g = (int) ((rgb >> 8 & 255) * bright);
		int b = (int) ((rgb & 255) * bright);
		int argb = 0xFF000000 | r << 16 | g << 8 | b;
		collector.order(1).submitModel(getParentModel(), state, poseStack, GLOW, lightCoords, OverlayTexture.NO_OVERLAY, argb, null,
			state.outlineColor, null);
	}
}
