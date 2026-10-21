package dev.ahrivaea.client;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.client.base.BaseScreen;
import dev.ahrivaea.client.battle.BattleClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.server.packs.PackType;

public class AhrivaeaClient implements ClientModInitializer {
   public static final ModelLayerLocation CREATURE_LAYER = new ModelLayerLocation(Ahrivaea.id("creature"), "main");

   public void onInitializeClient() {
      ModelLayerRegistry.registerModelLayer(CREATURE_LAYER, CreatureModel::createBodyLayer);
      EntityRendererRegistry.register(Ahrivaea.CREATURE, CreatureRenderer::new);
      BattleClient.init();
      BaseScreen.register();
      VillagerScreen.register();
      MountHud.register();
      PartyClient.register();
      ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(Ahrivaea.id("creature_models"), GeoModels::reload);
   }
}
