package dev.ahrivaea.stellar;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StellarMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_stellar");

   public static Identifier id(String path) {
      return Identifier.fromNamespaceAndPath("ahrivaea", path);
   }

   public void onInitialize() {
      Present.register();
      Stellar.init();
      LOGGER.info("Ahrivaea Stellar Gear loaded");
   }
}
