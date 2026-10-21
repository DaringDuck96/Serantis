package dev.ahrivaea.map;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MapMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_map");

   public void onInitialize() {
      Present.register();
      Markers.init();
      LOGGER.info("Ahrivaea Map loaded");
   }
}
