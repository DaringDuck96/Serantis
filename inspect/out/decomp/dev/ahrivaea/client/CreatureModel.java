package dev.ahrivaea.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

public class CreatureModel extends EntityModel<CreatureRenderState> {
   private final ModelPart head;
   private final ModelPart body;
   private final ModelPart tail;
   private final ModelPart legFL;
   private final ModelPart legFR;
   private final ModelPart legBL;
   private final ModelPart legBR;

   public CreatureModel(ModelPart root) {
      super(root);
      this.head = root.getChild("head");
      this.body = root.getChild("body");
      this.tail = root.getChild("tail");
      this.legFL = root.getChild("leg_fl");
      this.legFR = root.getChild("leg_fr");
      this.legBL = root.getChild("leg_bl");
      this.legBR = root.getChild("leg_br");
   }

   public static LayerDefinition createBodyLayer() {
      MeshDefinition mesh = new MeshDefinition();
      PartDefinition root = mesh.getRoot();
      PartDefinition head = root.addOrReplaceChild(
         "head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -3.0F, -6.0F, 6.0F, 6.0F, 6.0F), PartPose.offset(0.0F, 13.0F, -5.0F)
      );
      head.addOrReplaceChild(
         "ears",
         CubeListBuilder.create().texOffs(24, 0).addBox(-3.0F, -5.0F, -3.0F, 2.0F, 2.0F, 1.0F).texOffs(24, 0).addBox(1.0F, -5.0F, -3.0F, 2.0F, 2.0F, 1.0F),
         PartPose.ZERO
      );
      root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 12).addBox(-4.0F, -3.0F, -5.0F, 8.0F, 6.0F, 10.0F), PartPose.offset(0.0F, 16.0F, 0.0F));
      root.addOrReplaceChild(
         "tail",
         CubeListBuilder.create().texOffs(44, 12).addBox(-1.0F, -1.0F, 0.0F, 2.0F, 2.0F, 6.0F),
         PartPose.offsetAndRotation(0.0F, 14.0F, 5.0F, 0.5F, 0.0F, 0.0F)
      );
      CubeListBuilder leg = CubeListBuilder.create().texOffs(36, 12).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 5.0F, 2.0F);
      root.addOrReplaceChild("leg_fl", leg, PartPose.offset(-2.5F, 19.0F, -3.5F));
      root.addOrReplaceChild("leg_fr", leg, PartPose.offset(2.5F, 19.0F, -3.5F));
      root.addOrReplaceChild("leg_bl", leg, PartPose.offset(-2.5F, 19.0F, 3.5F));
      root.addOrReplaceChild("leg_br", leg, PartPose.offset(2.5F, 19.0F, 3.5F));
      return LayerDefinition.create(mesh, 64, 32);
   }

   public void setupAnim(CreatureRenderState state) {
      super.setupAnim(state);
      this.head.yRot = state.yRot * (float) (Math.PI / 180.0);
      this.head.xRot = state.xRot * (float) (Math.PI / 180.0);
      float swing = Mth.cos((double)(state.walkAnimationPos * 0.9F)) * 1.2F * state.walkAnimationSpeed;
      this.legFL.xRot = swing;
      this.legBR.xRot = swing;
      this.legFR.xRot = -swing;
      this.legBL.xRot = -swing;
      this.tail.yRot = Mth.cos((double)(state.ageInTicks * 0.15F)) * 0.3F;
      if (state.sitting) {
         this.body.y += 2.0F;
         this.head.y += 2.0F;
         this.legBL.xRot = this.legBR.xRot = -1.4F;
      }
   }
}
