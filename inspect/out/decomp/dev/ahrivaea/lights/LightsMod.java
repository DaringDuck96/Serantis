package dev.ahrivaea.lights;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LightsMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_lights");

   public void onInitialize() {
      Present.register();
      HeldLights.init();
      LOGGER.info("Ahrivaea Handheld Lights loaded");
   }
}
