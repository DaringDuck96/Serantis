package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.CreatureRenderState;
import dev.ahrivaea.client.CreatureRenderer;
import dev.ahrivaea.client.GeoModels;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.villager.VillagerJob;
import dev.serantis.battle.client.VillagerLooks;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Villagers wear the model drawn for their profession (see VillagerLooks), instead of the vanilla outfit. */
@Mixin(CreatureRenderer.class)
public abstract class CreatureRendererVillagerMixin {
    @Inject(method = "extractRenderState(Ldev/ahrivaea/creature/CreatureEntity;Ldev/ahrivaea/client/CreatureRenderState;F)V", at = @At("RETURN"))
    private void serantis$profession(CreatureEntity entity, CreatureRenderState state, float partial, CallbackInfo ci) {
        if (!state.villager) return;
        VillagerJob job = dev.serantis.battle.Villages.jobOf(entity);
        String look = VillagerLooks.look(job == null ? null : job.profession());
        if (look == null) return;
        var geo = GeoModels.model("villager", look);
        if (geo == null) return;
        state.geo = geo;
        state.texture = Identifier.parse("ahrivaea:textures/entity/creature/villager/" + look + ".png");   // never Enraged or Mutated
        state.villager = false;                              // no vanilla skin and outfit on top
        state.outfit = "";
    }
}
