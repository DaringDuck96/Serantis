package dev.ahrivaea.guilds;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuildsMod implements ModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_guilds");

   public void onInitialize() {
      Present.register();
      Guilds.init();
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, access, env) -> Guilds.registerCommands(dispatcher));
      LOGGER.info("Ahrivaea Guilds loaded");
   }
}
