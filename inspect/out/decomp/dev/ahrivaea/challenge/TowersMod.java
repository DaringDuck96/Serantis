package dev.ahrivaea.challenge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TowersMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_towers");

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("ahrivaea", path);
   }

   public void onInitialize() {
      Present.register();
      ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("challenges"), ChallengeDef::reload);
      ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(id("tower_palettes"), TowerPalette::reload);
      Challenges.init();
      StrongholdTowers.init();
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, access, env) -> Challenges.registerCommands(dispatcher));
      LOGGER.info("Ahrivaea Tower Challenges loaded");
   }
}
