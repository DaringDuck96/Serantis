package dev.ahrivaea.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public class GeoCreatureModel extends EntityModel<CreatureRenderState> {
   private final List<ModelPart> legsA = new ArrayList<>();
   private final List<ModelPart> legsB = new ArrayList<>();
   private final List<ModelPart> necks = new ArrayList<>();
   private final List<ModelPart> tail = new ArrayList<>();
   private final ModelPart head;
   private final ModelPart wingLeft;
   private final ModelPart wingRight;
   private final Function<String, ModelPart> lookup;
   private Map<String, GeoAnimation> animations = Map.of();

   public GeoCreatureModel(ModelPart root) {
      super(root);
      Function<String, ModelPart> find = root.createPartLookup();
      this.lookup = find;
      this.head = find.apply("head");
      this.wingLeft = find.apply("wing_left");
      this.wingRight = find.apply("wing_right");
      add(this.legsA, find, "front_left_leg", "back_right_leg", "leg_left", "arm_right");
      add(this.legsB, find, "front_right_leg", "back_left_leg", "leg_right", "arm_left");
      add(this.necks, find, "neck", "neck_2", "neck_3");
      add(this.tail, find, "tail", "tail_2", "tail_3", "tail_4", "tail_5", "tail_6", "tail_7", "tail_8");
   }

   void setAnimations(Map<String, GeoAnimation> animations) {
      this.animations = Map.copyOf(animations);
   }

   private static void add(List<ModelPart> list, Function<String, ModelPart> find, String... names) {
      for (String n : names) {
         ModelPart p = find.apply(n);
         if (p != null) {
            list.add(p);
         }
      }
   }

   public void setupAnim(CreatureRenderState state) {
      super.setupAnim(state);
      GeoAnimation anim = state.flying && this.animations.containsKey("fly") ? this.animations.get("fly") : this.animations.get("idle");
      if (anim != null) {
         anim.apply(this.lookup, (double)state.ageInTicks / 20.0);
      }

      float swing = Mth.cos((double)(state.walkAnimationPos * 0.6F)) * 0.8F * state.walkAnimationSpeed;

      for (ModelPart p : this.legsA) {
         p.xRot += swing;
      }

      for (ModelPart p : this.legsB) {
         p.xRot -= swing;
      }

      float yaw = state.yRot * (float) (Math.PI / 180.0);
      float pitch = state.xRot * (float) (Math.PI / 180.0);
      int joints = this.necks.size() + (this.head != null ? 1 : 0);
      if (joints > 0) {
         for (ModelPart p : this.necks) {
            p.yRot += yaw / (float)joints;
         }

         if (this.head != null) {
            this.head.yRot += yaw / (float)joints;
            this.head.xRot += pitch * 0.5F;
         }
      }

      if (this.animations.isEmpty()) {
         float flap = Mth.sin((double)(state.ageInTicks * 0.08F)) * 0.12F;
         if (this.wingLeft != null) {
            this.wingLeft.zRot -= flap;
         }

         if (this.wingRight != null) {
            this.wingRight.zRot += flap;
         }

         for (int i = 0; i < this.tail.size(); i++) {
            ModelPart var10000 = this.tail.get(i);
            var10000.yRot = var10000.yRot + Mth.cos((double)(state.ageInTicks * 0.07F - (float)i * 0.6F)) * 0.06F;
         }
      }

      if (state.sitting) {
         for (ModelPart p : this.legsA) {
            p.xRot = -0.9F;
         }

         for (ModelPart p : this.legsB) {
            p.xRot = -0.9F;
         }
      }
   }
}
