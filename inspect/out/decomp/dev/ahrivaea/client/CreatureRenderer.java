package dev.ahrivaea.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.ahrivaea.client.battle.BattleClient;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.villager.VillagerJob;
import dev.ahrivaea.villager.Villagers;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class CreatureRenderer extends MobRenderer<CreatureEntity, CreatureRenderState, EntityModel<CreatureRenderState>> {
   private final CreatureModel placeholder;

   public CreatureRenderer(Context context) {
      this(context, new CreatureModel(context.bakeLayer(AhrivaeaClient.CREATURE_LAYER)));
   }

   private CreatureRenderer(Context context, CreatureModel placeholder) {
      super(context, placeholder, 0.4F);
      this.placeholder = placeholder;
      this.addLayer(new VillagerOutfitLayer(this));
   }

   public CreatureRenderState createRenderState() {
      return new CreatureRenderState();
   }

   public void extractRenderState(CreatureEntity entity, CreatureRenderState state, float partialTick) {
      super.extractRenderState(entity, state, partialTick);
      String species = entity.getSpeciesId();
      String influence = entity.getInfluence().getSerializedName();
      state.geo = species.isEmpty() ? null : GeoModels.model(species, influence);
      state.realSize = state.geo != null && GeoModels.realSize(species, influence);
      state.modelScale = state.geo != null ? GeoModels.scale(species, influence) : 1.0F;
      state.extent = state.realSize
         ? GeoModels.extent(species, influence) * state.modelScale
         : (state.geo != null ? GeoModels.extent(species, influence) : 0.7F) * state.modelScale * entity.getScale();
      state.flying = !entity.isPreview()
         && !entity.isInWater()
         && !entity.isPassenger()
         && entity.level().noCollision(entity, entity.getBoundingBox().move(0.0, -0.3, 0.0));
      if (state.realSize && state.nameTagAttachment != null) {
         float h = GeoModels.height(species, influence) * state.modelScale * battleShrink(state.extent);
         state.nameTagAttachment = new Vec3(0.0, (double)h + 0.3, 0.0);
      }

      state.texture = GeoModels.texture(species, influence, entity.getVariant().getSerializedName());
      state.sitting = entity.isInSittingPose();
      state.villager = species.equals("villager") && state.geo != null;
      VillagerJob job = (VillagerJob)entity.getAttached(Villagers.JOB);
      state.outfit = job == null ? "" : Villagers.outfit(job.profession());
      state.tint = state.geo == null ? 0xFF000000 | entity.getInfluence().color() : -1;
   }

   public void submit(CreatureRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
      this.model = (EntityModel)(state.geo != null ? state.geo : this.placeholder);
      super.submit(state, poseStack, collector, camera);
   }

   private static float battleShrink(float extent) {
      return BattleClient.active() && extent > 1.3F ? 1.3F / extent : 1.0F;
   }

   protected void scale(CreatureRenderState state, PoseStack poseStack) {
      float s = state.modelScale * (state.realSize && state.scale > 0.0F ? 1.0F / state.scale : 1.0F) * battleShrink(state.extent);
      poseStack.scale(s, s, s);
   }

   protected float getShadowRadius(CreatureRenderState state) {
      return state.realSize
         ? Math.min(state.extent * battleShrink(state.extent) * 0.45F, 6.0F)
         : super.getShadowRadius(state) * (state.geo != null ? 2.5F : 1.0F);
   }

   protected AABB getBoundingBoxForCulling(CreatureEntity entity, float partialTick) {
      AABB box = super.getBoundingBoxForCulling(entity, partialTick);
      String species = entity.getSpeciesId();
      String influence = entity.getInfluence().getSerializedName();
      if (!species.isEmpty() && GeoModels.realSize(species, influence)) {
         float half = GeoModels.extent(species, influence) * GeoModels.scale(species, influence) / 2.0F;
         float h = GeoModels.height(species, influence) * GeoModels.scale(species, influence);
         return box.minmax(
            new AABB(
               entity.getX() - (double)half,
               entity.getY(),
               entity.getZ() - (double)half,
               entity.getX() + (double)half,
               entity.getY() + (double)h,
               entity.getZ() + (double)half
            )
         );
      } else {
         return box;
      }
   }

   protected int getModelTint(CreatureRenderState state) {
      return state.tint;
   }

   public Identifier getTextureLocation(CreatureRenderState state) {
      return state.texture;
   }
}
