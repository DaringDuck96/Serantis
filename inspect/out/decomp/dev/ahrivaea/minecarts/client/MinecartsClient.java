package dev.ahrivaea.minecarts.client;

import dev.ahrivaea.minecarts.MinecartsMod;
import dev.ahrivaea.minecarts.Present;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.StartTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MinecartsClient implements ClientModInitializer {
   public void onInitializeClient() {
      ClientTickEvents.START_CLIENT_TICK.register((StartTick)mc -> {
         boolean has;
         try {
            has = mc.getConnection() != null && ClientPlayNetworking.canSend(Present.TYPE);
         } catch (IllegalStateException var3) {
            has = false;
         }

         MinecartsMod.serverHasMod = has;
      });
   }
}
