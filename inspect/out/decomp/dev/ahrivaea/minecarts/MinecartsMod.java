package dev.ahrivaea.minecarts;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinecartsMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_minecarts");
   public static final double SPEED = 30.0;
   public static volatile boolean serverHasMod = true;

   public void onInitialize() {
      Present.register();
      LOGGER.info("Ahrivaea Fast Minecarts loaded: top speed {} blocks/sec", 30.0);
   }
}
