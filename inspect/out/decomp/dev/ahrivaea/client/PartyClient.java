package dev.ahrivaea.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants.Type;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.Dex;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.party.Starters;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

public final class PartyClient {
   public static KeyMapping OPEN;
   public static KeyMapping DEX;
   static JsonObject state;
   static boolean showSummary = true;
   private static JsonObject pendingStarter;
   static final int ROW = 20;
   static final int WIDTH = 112;

   private PartyClient() {
   }

   public static void register() {
      loadConfig();
      Category category = Category.register(Ahrivaea.id("party"));
      OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea.party", Type.KEYBOARD, 14, category));
      DEX = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea.dex", Type.KEYBOARD, 13, category));
      ClientPlayNetworking.registerGlobalReceiver(Dex.DataPayload.TYPE, (payload, ctx) -> {
         JsonObject data = JsonParser.parseString(payload.json()).getAsJsonObject();
         if (ctx.client().gui.screen() instanceof DexScreen s) {
            s.setData(data);
         }
      });
      ClientPlayNetworking.registerGlobalReceiver(PartySync.StatePayload.TYPE, (payload, ctx) -> {
         state = JsonParser.parseString(payload.json()).getAsJsonObject();
         if (ctx.client().gui.screen() instanceof PartyScreen s) {
            s.refresh();
         }
      });
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client) -> state = null);
      ClientPlayNetworking.registerGlobalReceiver(
         Starters.OfferPayload.TYPE, (payload, ctx) -> pendingStarter = JsonParser.parseString(payload.json()).getAsJsonObject()
      );
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (pendingStarter != null && mc.gui.screen() == null && mc.player != null) {
            mc.gui.setScreen(new StarterScreen(pendingStarter));
            pendingStarter = null;
         }

         while (DEX.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) {
               if (state == null) {
                  mc.player.sendOverlayMessage(Component.literal("This server doesn't use Ahrivaea Creatures."));
               } else {
                  DexScreen.open();
               }
            }
         }

         while (OPEN.consumeClick()) {
            if (mc.gui.screen() == null && mc.player != null) {
               if (state == null) {
                  mc.player.sendOverlayMessage(Component.literal("This server doesn't use Ahrivaea Creatures."));
               } else {
                  mc.gui.setScreen(new PartyScreen());
               }
            }
         }
      });
      HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Ahrivaea.id("party_summary"), (g, delta) -> {
         Minecraft mc = Minecraft.getInstance();
         if (state != null && showSummary && mc.player != null && !mc.gui.hud.isHidden()) {
            drawSummary(g, mc.font);
         }
      });
   }

   public static int outCount() {
      return state == null ? -1 : state.getAsJsonArray("out").size();
   }

   static void drawSummary(GuiGraphicsExtractor g, Font font) {
      JsonArray party = state.getAsJsonArray("party");
      JsonArray out = state.getAsJsonArray("out");
      int rows = party.size() + out.size();
      if (rows != 0) {
         int h = 12 + rows * 20 + (!out.isEmpty() && !party.isEmpty() ? 4 : 0);
         LocalPlayer player = Minecraft.getInstance().player;
         int top = MountHud.hudTop(Minecraft.getInstance(), g.guiHeight()) - 2;
         int x = 4;
         int y = player != null && player.getVehicle() instanceof CreatureEntity ? top + 76 : top;
         g.fill(x - 2, y - 2, x + 112 + 2, y + h, -1879048192);
         g.text(font, "Party  [" + OPEN.getTranslatedKeyMessage().getString() + "]", x + 1, y, -5592406);
         y += 12;

         for (JsonElement e : party) {
            row(g, font, e.getAsJsonObject(), x, y, null);
            y += 20;
         }

         if (!out.isEmpty() && !party.isEmpty()) {
            y += 4;
         }

         for (JsonElement e : out) {
            JsonObject c = e.getAsJsonObject();
            row(g, font, c, x, y, c.get("riding").getAsBoolean() ? "riding" : "out");
            y += 20;
         }
      }
   }

   private static void row(GuiGraphicsExtractor g, Font font, JsonObject c, int x, int y, String tag) {
      String lv = "Lv." + c.get("level").getAsInt();
      String name = c.get("name").getAsString();
      int room = 108 - font.width(lv);
      if (font.width(name) > room) {
         name = font.plainSubstrByWidth(name, room - 6) + "..";
      }

      g.text(font, name, x + 1, y, 0xFF000000 | c.get("color").getAsInt());
      g.text(font, lv, x + 112 - font.width(lv), y, -1);
      int hp = c.get("hp").getAsInt();
      int max = c.has("maxHp") ? c.get("maxHp").getAsInt() : hp;
      bar(g, x + 1, y + 11, 56, 4, max <= 0 ? 0.0F : (float)hp / (float)max, hpColor(hp, max));
      String hpText = tag != null ? tag : hp + "/" + max;
      g.text(font, hpText, x + 61, y + 9, tag != null ? -6303489 : -2236963);
   }

   static int hpColor(int hp, int max) {
      float f = max <= 0 ? 0.0F : (float)hp / (float)max;
      return f > 0.5F ? -11149995 : (f > 0.2F ? -2047936 : -2076592);
   }

   static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
      g.fill(x, y, x + w, y + h, -13619152);
      g.fill(x, y, x + Math.round((float)w * Math.clamp(fraction, 0.0F, 1.0F)), y + h, color);
   }

   private static Path configFile() {
      return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_party.json");
   }

   private static void loadConfig() {
      try {
         if (!Files.exists(configFile())) {
            return;
         }

         JsonObject o = JsonParser.parseString(Files.readString(configFile())).getAsJsonObject();
         if (o.has("summary")) {
            showSummary = o.get("summary").getAsBoolean();
         }
      } catch (Exception var1) {
         Ahrivaea.LOGGER.warn("Couldn't read {}: {}", configFile(), var1.toString());
      }
   }

   static void setShowSummary(boolean on) {
      showSummary = on;

      try {
         JsonObject o = new JsonObject();
         o.addProperty("summary", on);
         Files.createDirectories(configFile().getParent());
         Files.writeString(configFile(), o.toString());
      } catch (Exception var2) {
         Ahrivaea.LOGGER.warn("Couldn't save {}: {}", configFile(), var2.toString());
      }
   }
}
