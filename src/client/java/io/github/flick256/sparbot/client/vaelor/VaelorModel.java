package io.github.flick256.sparbot.client.vaelor;

import io.github.flick256.sparbot.content.VaelorBoss;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Vaelor: a knight in star-iron plate, broader than a man, with a crowned great helm, spiked pauldrons, a cape and a
 * greatsword as long as he is tall. At model scale he is 47 pixels from crown to heel; the renderer draws him 1.4
 * times that. The texture (128 by 128, made by tools/textures) follows the boxes here exactly, so a box that moves
 * here moves there too.
 *
 * <p>His poses follow what the server says he's doing ({@link VaelorBoss.Action}) and how long he's been at it: every
 * attack has a wind-up you can read before it lands.
 */
public class VaelorModel extends EntityModel<VaelorRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(Identifier.fromNamespaceAndPath("sparbot", "vaelor"), "main");
	private static final float HALF_PI = Mth.HALF_PI;

	private final ModelPart hips;
	private final ModelPart torso;
	private final ModelPart head;
	private final ModelPart cape;
	private final ModelPart rightArm;
	private final ModelPart leftArm;
	private final ModelPart sword;
	private final ModelPart rightLeg;
	private final ModelPart leftLeg;

	public VaelorModel(ModelPart root) {
		super(root);
		this.hips = root.getChild("hips");
		this.torso = hips.getChild("torso");
		this.head = torso.getChild("head");
		this.cape = torso.getChild("cape");
		this.rightArm = torso.getChild("right_arm");
		this.leftArm = torso.getChild("left_arm");
		this.sword = rightArm.getChild("sword");
		this.rightLeg = hips.getChild("right_leg");
		this.leftLeg = hips.getChild("left_leg");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition hips = root.addOrReplaceChild("hips", CubeListBuilder.create()
			.texOffs(46, 20).addBox(-6, -2, -3.5F, 12, 5, 7)
			.texOffs(104, 26).addBox(-4, 3, -4.5F, 8, 6, 1)
			.texOffs(104, 34).addBox(-4, 3, 3.5F, 8, 6, 1), PartPose.offset(0, 8, 0));
		PartDefinition torso = hips.addOrReplaceChild("torso", CubeListBuilder.create()
			.texOffs(0, 20).addBox(-7, -14, -4, 14, 14, 8), PartPose.offset(0, -2, 0));
		PartDefinition head = torso.addOrReplaceChild("head", CubeListBuilder.create()
			.texOffs(0, 0).addBox(-4.5F, -10, -4.5F, 9, 10, 9), PartPose.offset(0, -14, 0));
		head.addOrReplaceChild("crown", CubeListBuilder.create()
			.texOffs(40, 0).addBox(-5, -2, -5, 10, 2, 10)
			.texOffs(84, 0).addBox(-5, -5, -5, 1, 3, 1)
			.texOffs(84, 0).addBox(4, -5, -5, 1, 3, 1)
			.texOffs(84, 0).addBox(-5, -5, 4, 1, 3, 1)
			.texOffs(84, 0).addBox(4, -5, 4, 1, 3, 1)
			.texOffs(84, 0).addBox(-5, -5, -0.5F, 1, 3, 1)
			.texOffs(84, 0).addBox(4, -5, -0.5F, 1, 3, 1)
			.texOffs(84, 0).addBox(-0.5F, -5, 4, 1, 3, 1)
			.texOffs(90, 0).addBox(-0.5F, -7, -5, 1, 5, 1), PartPose.offset(0, -10, 0));
		torso.addOrReplaceChild("cape", CubeListBuilder.create()
			.texOffs(100, 0).addBox(-6.5F, 0, 0, 13, 24, 1), PartPose.offset(0, -13, 4.0F));
		PartDefinition rightArm = torso.addOrReplaceChild("right_arm", CubeListBuilder.create()
			.texOffs(0, 44).addBox(-3, -1, -2.5F, 5, 15, 5)
			.texOffs(44, 44).addBox(-5.5F, -4, -4.5F, 8, 5, 9)
			.texOffs(80, 44).addBox(-3.5F, -7, -1, 2, 3, 2), PartPose.offset(-9, -11, 0));
		torso.addOrReplaceChild("left_arm", CubeListBuilder.create()
			.texOffs(22, 44).addBox(-2, -1, -2.5F, 5, 15, 5)
			.texOffs(44, 44).mirror().addBox(-2.5F, -4, -4.5F, 8, 5, 9)
			.texOffs(80, 44).mirror().addBox(1.5F, -7, -1, 2, 3, 2), PartPose.offset(9, -11, 0));
		rightArm.addOrReplaceChild("sword", CubeListBuilder.create()
			.texOffs(60, 66).addBox(-0.5F, -0.5F, -1, 1, 1, 6)
			.texOffs(76, 66).addBox(-1, -1, 5, 2, 2, 2)
			.texOffs(86, 66).addBox(-4.5F, -1, -3, 9, 2, 2)
			.texOffs(0, 86).addBox(-1.5F, -0.5F, -29, 3, 1, 26), PartPose.offset(-0.5F, 12.5F, 0));
		hips.addOrReplaceChild("right_leg", CubeListBuilder.create()
			.texOffs(0, 66).addBox(-2.5F, 0, -2.5F, 5, 13, 5)
			.texOffs(44, 60).addBox(-3, 4, -3.5F, 6, 3, 2), PartPose.offset(-3, 3, 0));
		hips.addOrReplaceChild("left_leg", CubeListBuilder.create()
			.texOffs(22, 66).addBox(-2.5F, 0, -2.5F, 5, 13, 5)
			.texOffs(44, 60).mirror().addBox(-3, 4, -3.5F, 6, 3, 2), PartPose.offset(3, 3, 0));
		return LayerDefinition.create(mesh, 128, 128);
	}

	/** A pose: the angles (radians) and the drop of the hips (pixels) that each action sets. */
	private static final class Pose {
		float hipsY;
		float torsoX = 0.05F;
		float torsoY;
		float rArmX = -0.55F;
		float rArmY = 0.1F;
		float rArmZ;
		float lArmX = -0.25F;
		float lArmY;
		float lArmZ = -0.12F;
		float swordX = 0.85F;
		float rLegX;
		float lLegX;
		float legYaw;
		float headX;

		static Pose stance() {
			return new Pose();
		}

		static Pose seated() {
			Pose p = new Pose();
			p.hipsY = 6;
			p.torsoX = 0.08F;
			p.rArmX = -0.8F;
			p.rArmY = 0.35F;
			p.lArmX = -0.8F;
			p.lArmY = -0.35F;
			p.lArmZ = 0;
			p.swordX = HALF_PI + 0.8F;
			p.rLegX = -1.45F;
			p.lLegX = -1.45F;
			p.legYaw = 0.12F;
			p.headX = 0.25F;
			return p;
		}

		static Pose kneel() {
			Pose p = new Pose();
			p.hipsY = 5.5F;
			p.torsoX = 0.35F;
			p.rArmX = -0.95F;
			p.rArmY = 0.3F;
			p.lArmX = -0.95F;
			p.lArmY = -0.4F;
			p.lArmZ = 0;
			p.swordX = HALF_PI + 0.95F;
			p.rLegX = -1.5F;
			p.lLegX = 0.9F;
			p.headX = 0.7F;
			return p;
		}

		Pose lerp(Pose o, float t) {
			Pose p = new Pose();
			p.hipsY = Mth.lerp(t, hipsY, o.hipsY);
			p.torsoX = Mth.lerp(t, torsoX, o.torsoX);
			p.torsoY = Mth.lerp(t, torsoY, o.torsoY);
			p.rArmX = Mth.lerp(t, rArmX, o.rArmX);
			p.rArmY = Mth.lerp(t, rArmY, o.rArmY);
			p.rArmZ = Mth.lerp(t, rArmZ, o.rArmZ);
			p.lArmX = Mth.lerp(t, lArmX, o.lArmX);
			p.lArmY = Mth.lerp(t, lArmY, o.lArmY);
			p.lArmZ = Mth.lerp(t, lArmZ, o.lArmZ);
			p.swordX = Mth.lerp(t, swordX, o.swordX);
			p.rLegX = Mth.lerp(t, rLegX, o.rLegX);
			p.lLegX = Mth.lerp(t, lLegX, o.lLegX);
			p.legYaw = Mth.lerp(t, legYaw, o.legYaw);
			p.headX = Mth.lerp(t, headX, o.headX);
			return p;
		}
	}

	private static float ease(float t) {
		t = Mth.clamp(t, 0, 1);
		return t * t * (3 - 2 * t);
	}

	/** Progress through [from, to): 0 before, 1 after, eased. */
	private static float seg(float t, float from, float to) {
		return ease((t - from) / Math.max(0.001F, to - from));
	}

	private static int byPhase(int[] values, int phase) {
		return values[Mth.clamp(phase - 1, 0, 2)];
	}

	@Override
	public void setupAnim(VaelorRenderState state) {
		super.setupAnim(state);
		float t = state.actionTime;
		float age = state.ageInTicks;
		Pose stance = Pose.stance();
		Pose p = switch (state.action) {
			case SEATED -> Pose.seated();
			case RISE -> Pose.seated().lerp(stance, seg(t, 4, 30));
			case CLEAVE, RIPOSTE -> cleave(t, state.action == VaelorBoss.Action.RIPOSTE ? 5 : byPhase(VaelorBoss.CLEAVE_WIND, state.phase));
			case OVERHEAD -> overhead(t, byPhase(VaelorBoss.OVERHEAD_WIND, state.phase));
			case LUNGE -> lunge(t, byPhase(VaelorBoss.LUNGE_WIND, state.phase), age);
			case GUARD -> stance.lerp(guard(), seg(t, 0, 5));
			case STAGGER -> stagger(t, age);
			case STARFALL -> starfall(t, state.phase >= 3 ? 18 : 24);
			case LEAP -> leap(t, state.phase >= 3 ? 20 : 24);
			case LANCE -> stance.lerp(lance(age, t >= 34), seg(t, 0, 8));
			case ROAR -> stance.lerp(roar(age), seg(t, 0, 8)).lerp(stance, seg(t, 60, 70));
			case KNEEL -> stance.lerp(Pose.kneel(), seg(t, 0, 24));
			default -> stance;
		};
		boolean planted = state.action == VaelorBoss.Action.SEATED || state.action == VaelorBoss.Action.KNEEL;
		float breathe = Mth.sin(age * 0.09F) * (planted ? 0.25F : 0.35F);

		hips.y += p.hipsY;
		torso.y += breathe;
		torso.xRot = p.torsoX;
		torso.yRot = p.torsoY;
		rightArm.xRot = p.rArmX;
		rightArm.yRot = p.rArmY;
		rightArm.zRot = p.rArmZ;
		leftArm.xRot = p.lArmX;
		leftArm.yRot = p.lArmY;
		leftArm.zRot = p.lArmZ;
		sword.xRot = p.swordX;
		rightLeg.xRot = p.rLegX;
		leftLeg.xRot = p.lLegX;
		rightLeg.yRot = p.legYaw;
		leftLeg.yRot = -p.legYaw;

		// Walking, when he's on his feet and not mid-blow.
		if (!planted && state.action != VaelorBoss.Action.LEAP && state.action != VaelorBoss.Action.LUNGE) {
			float walk = state.walkAnimationPos * 0.6662F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			rightLeg.xRot += Mth.cos(walk) * 1.1F * speed;
			leftLeg.xRot += Mth.cos(walk + Mth.PI) * 1.1F * speed;
			leftArm.xRot += Mth.cos(walk) * 0.5F * speed;
			torso.yRot += Mth.cos(walk) * 0.06F * speed;
			hips.y += Math.abs(Mth.sin(walk)) * 0.8F * speed;
		}

		// He looks where he looks; bowed when seated or kneeling.
		float yaw = Mth.clamp(state.yRot, -50, 50) * Mth.DEG_TO_RAD;
		head.yRot = planted ? yaw * 0.5F : yaw;
		head.xRot = p.headX + state.xRot * Mth.DEG_TO_RAD * (planted ? 0.3F : 1.0F);

		// The cape trails when he moves and stirs when he doesn't.
		cape.xRot = 0.08F + Math.min(1.0F, state.walkAnimationSpeed) * 0.5F + Mth.sin(age * 0.07F) * 0.04F - p.torsoX * 0.6F;
		if (state.action == VaelorBoss.Action.LEAP || state.action == VaelorBoss.Action.LUNGE) {
			cape.xRot += 0.5F;
		}
		if (state.action == VaelorBoss.Action.SEATED) {
			cape.xRot = -0.05F;
		}

		// Trembling: under strain (the lance, the roar) and in the last of him.
		if (state.action == VaelorBoss.Action.LANCE && t >= 34 || state.action == VaelorBoss.Action.ROAR) {
			float j = Mth.sin(age * 3.1F) * 0.03F;
			rightArm.xRot += j;
			leftArm.xRot -= j;
			torso.zRot += j * 0.5F;
		}
		if (state.action == VaelorBoss.Action.KNEEL && t > 24) {
			torso.zRot += Mth.sin(age * 2.3F) * 0.012F;
		}
	}

	private static Pose cleave(float t, int wind) {
		Pose stance = Pose.stance();
		Pose back = new Pose();
		back.torsoY = 0.7F;
		back.rArmX = -1.5F;
		back.rArmY = 0.95F;
		back.rArmZ = 0.3F;
		back.swordX = 1.5F;
		back.lArmX = -0.5F;
		back.lArmZ = -0.4F;
		Pose across = new Pose();
		across.torsoY = -0.75F;
		across.rArmX = -1.45F;
		across.rArmY = -0.65F;
		across.rArmZ = 0.1F;
		across.swordX = 1.5F;
		across.lArmX = -0.2F;
		across.lArmZ = -0.5F;
		if (t < wind) {
			return stance.lerp(back, seg(t, 0, wind));
		}
		if (t < wind + 4) {
			return back.lerp(across, seg(t, wind, wind + 3));
		}
		return across.lerp(stance, seg(t, wind + 5, wind + 12));
	}

	private static Pose overhead(float t, int wind) {
		Pose stance = Pose.stance();
		Pose up = new Pose();
		up.torsoX = -0.25F;
		up.rArmX = -2.9F;
		up.rArmY = 0.25F;
		up.lArmX = -2.7F;
		up.lArmY = -0.35F;
		up.lArmZ = 0;
		up.swordX = 1.35F;
		up.headX = -0.15F;
		Pose down = new Pose();
		down.torsoX = 0.45F;
		down.hipsY = 1.5F;
		down.rArmX = -0.45F;
		down.rArmY = 0.2F;
		down.lArmX = -0.55F;
		down.lArmY = -0.35F;
		down.lArmZ = 0;
		down.swordX = 1.75F;
		down.headX = 0.2F;
		if (t < wind) {
			return stance.lerp(up, seg(t, 0, wind * 0.8F));
		}
		if (t < wind + 9) {
			return up.lerp(down, seg(t, wind, wind + 3));
		}
		return down.lerp(stance, seg(t, wind + 9, wind + 16));
	}

	private static Pose lunge(float t, int wind, float age) {
		Pose stance = Pose.stance();
		Pose low = new Pose();
		low.hipsY = 3;
		low.torsoX = 0.45F;
		low.rArmX = -1.3F;
		low.rArmY = 0.15F;
		low.swordX = 1.45F;
		low.lArmX = -0.6F;
		low.rLegX = -0.5F;
		low.lLegX = 0.6F;
		if (t < wind) {
			return stance.lerp(low, seg(t, 0, wind * 0.7F));
		}
		if (t < wind + 10) {
			Pose run = low.lerp(low, 0);
			run.hipsY = 1.5F;
			run.rLegX = Mth.sin(age * 1.3F) * 0.9F;
			run.lLegX = -run.rLegX;
			return run;
		}
		return low.lerp(stance, seg(t, wind + 12, wind + 24));
	}

	private static Pose guard() {
		Pose p = new Pose();
		p.torsoX = 0.1F;
		p.rArmX = -1.1F;
		p.rArmY = 0.55F;
		p.lArmX = -1.1F;
		p.lArmY = -0.5F;
		p.lArmZ = 0;
		p.swordX = -0.47F;
		p.hipsY = 1;
		p.rLegX = -0.3F;
		p.lLegX = 0.3F;
		return p;
	}

	private static Pose stagger(float t, float age) {
		Pose p = new Pose();
		float k = 1 - seg(t, 16, 28);
		p.torsoX = -0.35F * k;
		p.rArmX = -0.2F;
		p.rArmZ = 0.6F * k;
		p.lArmZ = -0.6F * k;
		p.headX = -0.4F * k;
		p.swordX = 0.5F;
		p.hipsY = 1 * k;
		p.torsoY = Mth.sin(age * 1.7F) * 0.08F * k;
		return Pose.stance().lerp(p, Math.min(1, t / 3));
	}

	private static Pose starfall(float t, int cast) {
		Pose stance = Pose.stance();
		Pose up = new Pose();
		up.rArmX = -3.0F;
		up.rArmZ = 0.1F;
		up.rArmY = 0;
		up.swordX = HALF_PI;
		up.lArmX = -0.4F;
		up.lArmZ = -0.5F;
		up.headX = -0.5F;
		up.torsoX = -0.12F;
		if (t < cast) {
			return stance.lerp(up, seg(t, 0, 8));
		}
		return up.lerp(stance, seg(t, cast + 2, cast + 10));
	}

	private static Pose leap(float t, int flight) {
		Pose stance = Pose.stance();
		Pose crouch = new Pose();
		crouch.hipsY = 3;
		crouch.torsoX = 0.4F;
		crouch.rArmX = 0.6F;
		crouch.lArmX = 0.6F;
		crouch.swordX = 0.6F;
		Pose air = new Pose();
		air.torsoX = -0.2F;
		air.rArmX = -2.8F;
		air.lArmX = -2.6F;
		air.lArmY = -0.35F;
		air.lArmZ = 0;
		air.swordX = 1.4F;
		air.rLegX = -0.8F;
		air.lLegX = -0.3F;
		Pose slam = new Pose();
		slam.torsoX = 0.5F;
		slam.hipsY = 2.5F;
		slam.rArmX = -0.3F;
		slam.lArmX = -0.4F;
		slam.lArmY = -0.35F;
		slam.lArmZ = 0;
		slam.swordX = 1.75F;
		slam.rLegX = -0.4F;
		slam.lLegX = 0.4F;
		int c = 10;
		if (t < c) {
			return stance.lerp(crouch, seg(t, 0, c));
		}
		if (t < c + flight - 3) {
			return crouch.lerp(air, seg(t, c, c + 5));
		}
		if (t < c + flight + 8) {
			return air.lerp(slam, seg(t, c + flight - 3, c + flight));
		}
		return slam.lerp(stance, seg(t, c + flight + 8, c + flight + 20));
	}

	private static Pose lance(float age, boolean firing) {
		Pose p = new Pose();
		p.rArmX = -1.5F;
		p.rArmY = 0.2F;
		p.lArmX = -1.45F;
		p.lArmY = -0.35F;
		p.lArmZ = 0;
		p.swordX = HALF_PI;
		p.torsoX = firing ? -0.05F : 0.05F;
		p.rLegX = -0.35F;
		p.lLegX = 0.35F;
		p.hipsY = firing ? 1.5F : 0.5F;
		return p;
	}

	private static Pose roar(float age) {
		Pose p = new Pose();
		p.rArmX = -0.5F;
		p.rArmZ = 1.1F;
		p.lArmX = -0.5F;
		p.lArmZ = -1.1F;
		p.headX = -0.6F;
		p.torsoX = -0.25F;
		p.swordX = 1.0F;
		return p;
	}
}
