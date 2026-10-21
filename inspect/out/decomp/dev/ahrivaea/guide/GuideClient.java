package dev.ahrivaea.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Join;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuideClient implements ClientModInitializer {
   public static final Logger LOGGER = LoggerFactory.getLogger("ahrivaea_guide");
   public static KeyMapping OPEN;
   private static final Set<String> SEEN = new LinkedHashSet<>();
   private static String pendingOpen;
   private static int announceIn = -1;

   public void onInitializeClient() {
      loadSeen();
      Category category = Category.register(Identifier.fromNamespaceAndPath("ahrivaea_guide", "guide"));
      OPEN = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.ahrivaea_guide.open", Type.KEYBOARD, 10, category));
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         while (OPEN.consumeClick()) {
            if (client.gui.screen() == null) {
               open(null);
            }
         }

         if (pendingOpen != null && client.gui.screen() == null) {
            String mod = pendingOpen;
            pendingOpen = null;
            open(mod.isEmpty() ? null : mod);
         }

         if (announceIn > 0 && --announceIn == 0) {
            announce(client);
         }
      });
      ClientCommandRegistrationCallback.EVENT
         .register(
            (ClientCommandRegistrationCallback)(dispatcher, registries) -> dispatcher.register(
                  (LiteralArgumentBuilder)((LiteralArgumentBuilder)ClientCommands.literal("guide").executes(ctx -> {
                     pendingOpen = "";
                     return 1;
                  })).then(ClientCommands.argument("mod", StringArgumentType.greedyString()).executes(ctx -> {
                     pendingOpen = StringArgumentType.getString(ctx, "mod");
                     return 1;
                  }))
               )
         );
      ClientPlayConnectionEvents.JOIN.register((Join)(handler, sender, client) -> announceIn = 60);
      LOGGER.info("Ahrivaea Guide loaded: press G");
   }

   public static void open(String mod) {
      Minecraft mc = Minecraft.getInstance();
      GuideData data = GuideData.load().forThisServer();
      if (data.mods.isEmpty()) {
         if (mc.player != null) {
            mc.player.sendOverlayMessage(Component.literal("This server doesn't use any Ahrivaea mods."));
         }
      } else {
         mc.gui.setScreen(new GuideScreen(data, mod));
      }
   }

   private static void announce(Minecraft client) {
      if (client.player != null) {
         GuideData data = GuideData.load().forThisServer();
         List<String> fresh = data.mods.stream().filter(m -> !seen(m.key())).map(GuideData.Mod::name).toList();
         if (!fresh.isEmpty()) {
            String key = OPEN.getTranslatedKeyMessage().getString();
            client.player
               .sendSystemMessage(
                  Component.literal("[Ahrivaea Guide] ")
                     .withStyle(ChatFormatting.GOLD)
                     .append(
                        Component.literal(
                              (fresh.size() == data.mods.size() ? "Mods on this server: " : "New: ")
                                 + String.join(", ", fresh)
                                 + ". Press "
                                 + key
                                 + " (or type /guide) to see what they do and how to craft their items."
                           )
                           .withStyle(ChatFormatting.WHITE)
                     )
               );
         }
      }
   }

   public static boolean seen(String modKey) {
      return SEEN.contains(modKey);
   }

   public static void markSeen(String modKey) {
      if (SEEN.add(modKey)) {
         saveSeen();
      }
   }

   private static Path file() {
      return FabricLoader.getInstance().getConfigDir().resolve("ahrivaea_guide.json");
   }

   private static void loadSeen() {
      try {
         if (!Files.exists(file())) {
            return;
         }

         JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
         if (o.has("seen")) {
            o.getAsJsonArray("seen").forEach(ex -> SEEN.add(ex.getAsString()));
         }
      } catch (Exception var1) {
         LOGGER.warn("Guide: couldn't read {}: {}", file(), var1.toString());
      }
   }

   private static void saveSeen() {
      try {
         JsonObject o = new JsonObject();
         JsonArray a = new JsonArray();
         SEEN.forEach(a::add);
         o.add("seen", a);
         Files.createDirectories(file().getParent());
         Files.writeString(file(), o.toString());
      } catch (Exception var2) {
         LOGGER.warn("Guide: couldn't save {}: {}", file(), var2.toString());
      }
   }
}
