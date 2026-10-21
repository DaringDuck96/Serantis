package dev.ahrivaea.map.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.ahrivaea.map.Markers;
import dev.ahrivaea.map.Present;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.Load;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public class MapClient implements ClientModInitializer {
   public static KeyMapping OPEN;
   public static KeyMapping MINIMAP;
   public static boolean minimap = true;
   public static volatile Markers.Payload markers = new Markers.Payload(List.of(), List.of());
   public static volatile long markersAt;
   private static int ticks;

   public void onInitializeClient() {
      loadSettings();
      Category category = Category.register(Identifier.fromNamespaceAndPath("ahrivaea_map", "map"));
      OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_map.open", Type.KEYBOARD, 16, category));
      MINIMAP = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_map.minimap", Type.KEYBOARD, 17, category));
      ClientPlayNetworking.registerGlobalReceiver(Markers.Payload.TYPE, (payload, ctx) -> {
         markers = payload;
         markersAt = System.currentTimeMillis();
      });
      ClientChunkEvents.CHUNK_LOAD.register((Load)(level, chunk) -> {
         if (serverHasMod()) {
            MapTiles.queue(chunk.getPos().x(), chunk.getPos().z());
         }
      });
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         boolean on = serverHasMod();

         while (OPEN.consumeClick()) {
            if (on && mc.gui.screen() == null) {
               mc.gui.setScreen(new MapScreen());
            }
         }

         while (MINIMAP.consumeClick()) {
            if (on) {
               setMinimap(!minimap);
            }
         }

         ticks++;
         if (on && ticks % 100 == 0) {
            MapTiles.queueAround(mc, mc.options.getEffectiveRenderDistance());
         } else if (on && ticks % 20 == 0) {
            MapTiles.queueAround(mc, 1);
         }

         MapTiles.tick(mc, on && mc.level != null);
         if (ticks % 600 == 0) {
            MapTiles.saveAll();
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, mc) -> {
         markers = new Markers.Payload(List.of(), List.of());
         mc.execute(MapTiles::closeAll);
      });
      HudElementRegistry.attachElementAfter(VanillaHudElements.MOB_EFFECTS, Identifier.fromNamespaceAndPath("ahrivaea_map", "minimap"), (g, delta) -> {
         Minecraft mc = Minecraft.getInstance();
         if (minimap && mc.player != null && mc.level != null && !mc.gui.hud.isHidden() && serverHasMod()) {
            MapView.minimap(g, mc);
         }
      });
   }

   public static boolean serverHasMod() {
      try {
         return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(Present.TYPE);
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   static boolean markersFresh() {
      return System.currentTimeMillis() - markersAt < 3000L;
   }

   private static Path settingsFile() {
      return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_map.json");
   }

   private static void loadSettings() {
      try {
         if (Files.exists(settingsFile())) {
            JsonObject o = JsonParser.parseString(Files.readString(settingsFile())).getAsJsonObject();
            if (o.has("minimap")) {
               minimap = o.get("minimap").getAsBoolean();
            }
         }
      } catch (Exception var1) {
      }
   }

   public static void setMinimap(boolean on) {
      minimap = on;

      try {
         JsonObject o = new JsonObject();
         o.addProperty("minimap", on);
         Files.writeString(settingsFile(), o.toString());
      } catch (Exception var2) {
      }
   }
}
