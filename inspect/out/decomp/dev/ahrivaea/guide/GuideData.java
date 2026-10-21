package dev.ahrivaea.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public final class GuideData {
   public final List<GuideData.Mod> mods;
   public final Map<Item, String> addedBy;

   private GuideData(List<GuideData.Mod> mods, Map<Item, String> addedBy) {
      this.mods = mods;
      this.addedBy = addedBy;
   }

   public GuideData forThisServer() {
      Set<Identifier> sendable;
      try {
         sendable = Minecraft.getInstance().getConnection() == null ? Set.of() : ClientPlayNetworking.getSendable();
      } catch (IllegalStateException var6) {
         sendable = Set.of();
      }

      List<GuideData.Mod> kept = new ArrayList<>();

      for (GuideData.Mod m : this.mods) {
         if (sendable.contains(Identifier.fromNamespaceAndPath(m.id(), "present"))) {
            kept.add(m);
         }
      }

      if (!kept.isEmpty()) {
         for (GuideData.Mod mx : this.mods) {
            if (mx.id().equals("ahrivaea_guide")) {
               kept.add(mx);
            }
         }
      }

      Map<Item, String> by = new HashMap<>();

      for (GuideData.Mod mxx : kept) {
         this.addedBy.forEach((item, name) -> {
            if (name.equals(m.name())) {
               by.put(item, name);
            }
         });
      }

      return new GuideData(kept, by);
   }

   public List<GuideData.Recipe> allRecipes() {
      List<GuideData.Recipe> out = new ArrayList<>();

      for (GuideData.Mod m : this.mods) {
         out.addAll(m.recipes());
      }

      return out;
   }

   public static GuideData load() {
      List<GuideData.Mod> mods = new ArrayList<>();
      Map<Item, String> addedBy = new HashMap<>();
      Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.fromNamespaceAndPath("ahrivaea_guide", "guide.json"));
      if (resource.isEmpty()) {
         return new GuideData(mods, addedBy);
      } else {
         try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            for (JsonElement e : root.getAsJsonArray("mods")) {
               JsonObject m = e.getAsJsonObject();
               List<ItemStack> items = new ArrayList<>();

               for (JsonElement i : m.getAsJsonArray("items")) {
                  ItemStack st = stack(i.getAsString(), 1);
                  items.add(st);
                  if (!st.is(Items.BARRIER)) {
                     addedBy.put(st.getItem(), m.get("name").getAsString());
                  }
               }

               List<GuideData.Recipe> recipes = new ArrayList<>();
               GuideData.Mod mod = new GuideData.Mod(
                  m.get("key").getAsString(),
                  m.get("id").getAsString(),
                  m.get("name").getAsString(),
                  m.get("summary").getAsString(),
                  strings(m.getAsJsonArray("guide")),
                  strings(m.getAsJsonArray("features")),
                  m.get("commands").getAsString(),
                  m.get("added").getAsString(),
                  m.get("install").getAsString(),
                  items,
                  recipes
               );

               for (JsonElement r : m.getAsJsonArray("recipes")) {
                  try {
                     GuideData.Recipe recipe = recipe(mod, r.getAsJsonObject().get("id").getAsString(), r.getAsJsonObject().getAsJsonObject("recipe"));
                     if (recipe != null) {
                        recipes.add(recipe);
                     }
                  } catch (RuntimeException var15) {
                     GuideClient.LOGGER.warn("Guide: couldn't read recipe {}: {}", r, var15.toString());
                  }
               }

               for (GuideData.Recipe r : recipes) {
                  Identifier rid = BuiltInRegistries.ITEM.getKey(r.result().getItem());
                  if (!rid.getNamespace().equals("minecraft")) {
                     addedBy.putIfAbsent(r.result().getItem(), mod.name());
                  }
               }

               mods.add(mod);
            }
         } catch (Exception var17) {
            GuideClient.LOGGER.error("Guide: couldn't read guide.json: {}", var17.toString());
         }

         return new GuideData(mods, addedBy);
      }
   }

   private static List<String> strings(JsonArray a) {
      List<String> out = new ArrayList<>();
      if (a != null) {
         for (JsonElement e : a) {
            out.add(e.getAsString());
         }
      }

      return out;
   }

   private static GuideData.Recipe recipe(GuideData.Mod mod, String id, JsonObject r) {
      String type = r.get("type").getAsString();
      List<List<ItemStack>> slots = new ArrayList<>();
      GuideData.Station station;
      switch (type) {
         case "minecraft:crafting_shaped":
            station = GuideData.Station.CRAFTING;

            for (int i = 0; i < 9; i++) {
               slots.add(List.of());
            }

            JsonArray pattern = r.getAsJsonArray("pattern");
            JsonObject key = r.getAsJsonObject("key");

            for (int row = 0; row < pattern.size() && row < 3; row++) {
               String line = pattern.get(row).getAsString();

               for (int col = 0; col < line.length() && col < 3; col++) {
                  String c = String.valueOf(line.charAt(col));
                  if (key.has(c)) {
                     slots.set(row * 3 + col, ingredient(key.get(c)));
                  }
               }
            }
            break;
         case "minecraft:crafting_shapeless":
            station = GuideData.Station.CRAFTING;

            for (JsonElement e : r.getAsJsonArray("ingredients")) {
               slots.add(ingredient(e));
            }

            while (slots.size() < 9) {
               slots.add(List.of());
            }
            break;
         case "minecraft:stonecutting":
            station = GuideData.Station.STONECUTTER;
            slots.add(ingredient(r.get("ingredient")));
            break;
         case "minecraft:smithing_transform":
            station = GuideData.Station.SMITHING;
            slots.add(r.has("template") ? ingredient(r.get("template")) : List.of());
            slots.add(r.has("base") ? ingredient(r.get("base")) : List.of());
            slots.add(r.has("addition") ? ingredient(r.get("addition")) : List.of());
            break;
         default:
            return null;
      }

      ItemStack result = result(r.get("result"));
      StringBuilder text = new StringBuilder(result.getHoverName().getString()).append(' ').append(station.title).append(' ').append(mod.name());

      for (List<ItemStack> s : slots) {
         if (!s.isEmpty()) {
            text.append(' ').append(s.get(0).getHoverName().getString());
         }
      }

      return new GuideData.Recipe(id, mod, station, slots, result, text.toString().toLowerCase(Locale.ROOT));
   }

   private static ItemStack result(JsonElement e) {
      if (e.isJsonPrimitive()) {
         return stack(e.getAsString(), 1);
      } else {
         JsonObject o = e.getAsJsonObject();
         String id = o.has("id") ? o.get("id").getAsString() : o.get("item").getAsString();
         return stack(id, o.has("count") ? o.get("count").getAsInt() : 1);
      }
   }

   private static List<ItemStack> ingredient(JsonElement e) {
      List<ItemStack> out = new ArrayList<>();
      if (e == null || e.isJsonNull()) {
         return out;
      } else if (e.isJsonArray()) {
         for (JsonElement x : e.getAsJsonArray()) {
            out.addAll(ingredient(x));
         }

         return dedupe(out);
      } else {
         String s;
         if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            s = o.has("tag") ? "#" + o.get("tag").getAsString() : (o.has("item") ? o.get("item").getAsString() : null);
            if (s == null) {
               return out;
            }
         } else {
            s = e.getAsString();
         }

         if (s.startsWith("#")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(s.substring(1)));

            for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
               out.add(new ItemStack(h));
            }

            if (out.isEmpty()) {
               out.add(missing(s));
            }
         } else {
            out.add(stack(s, 1));
         }

         return out;
      }
   }

   private static List<ItemStack> dedupe(List<ItemStack> list) {
      Map<Item, ItemStack> seen = new LinkedHashMap<>();

      for (ItemStack s : list) {
         seen.putIfAbsent(s.getItem(), s);
      }

      return new ArrayList<>(seen.values());
   }

   private static ItemStack stack(String id, int count) {
      Identifier rid = Identifier.tryParse(id);
      Optional<Item> item = rid == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(rid);
      return !item.isEmpty() && item.get() != Items.AIR ? new ItemStack((ItemLike)item.get(), count) : missing(id);
   }

   private static ItemStack missing(String id) {
      ItemStack st = new ItemStack(Items.BARRIER);
      st.set(DataComponents.CUSTOM_NAME, Component.literal(id + " (not installed here)"));
      return st;
   }

   public static record Mod(
      String key,
      String id,
      String name,
      String summary,
      List<String> guide,
      List<String> features,
      String commands,
      String added,
      String install,
      List<ItemStack> items,
      List<GuideData.Recipe> recipes
   ) {
   }

   public static record Recipe(String id, GuideData.Mod mod, GuideData.Station station, List<List<ItemStack>> slots, ItemStack result, String searchText) {
      public boolean makes(Item item) {
         return this.result.is(item);
      }

      public boolean uses(Item item) {
         for (List<ItemStack> s : this.slots) {
            for (ItemStack st : s) {
               if (st.is(item)) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static enum Station {
      CRAFTING("Crafting Table", Items.CRAFTING_TABLE),
      STONECUTTER("Stonecutter", Items.STONECUTTER),
      SMITHING("Smithing Table", Items.SMITHING_TABLE),
      OTHER("Other", Items.BARRIER);

      public final String title;
      public final Item icon;

      private Station(String title, Item icon) {
         this.title = title;
         this.icon = icon;
      }
   }
}
