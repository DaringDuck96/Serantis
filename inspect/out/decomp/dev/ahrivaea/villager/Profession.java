package dev.ahrivaea.villager;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments.Mutable;

public record Profession(String id, String name, String description, String outfit, Item icon, int order, List<Profession.Rank> ranks) {
   private static Map<String, Profession> all = Map.of();

   public static Map<String, Profession> all() {
      return all;
   }

   public static List<Profession> sorted() {
      List<Profession> out = new ArrayList<>(all.values());
      out.sort(Comparator.comparingInt(Profession::order).thenComparing(Profession::name));
      return out;
   }

   public static Profession get(String id) {
      return all.get(id);
   }

   public static void reload(ResourceManager manager) {
      Map<String, Profession> loaded = new LinkedHashMap<>();
      int offers = 0;
      List<String> unknown = new ArrayList<>();

      for (Entry<Identifier, Resource> e : manager.listResources("villager_profession", p -> p.getPath().endsWith(".json")).entrySet()) {
         String id = e.getKey().getPath().substring("villager_profession/".length(), e.getKey().getPath().length() - ".json".length());

         try (Reader reader = e.getValue().openAsReader()) {
            JsonObject o = JsonParser.parseReader(reader).getAsJsonObject();
            List<Profession.Rank> ranks = new ArrayList<>();

            for (JsonElement re : o.getAsJsonArray("ranks")) {
               JsonObject r = re.getAsJsonObject();
               List<Profession.Offer> sells = new ArrayList<>();

               for (JsonElement se : r.getAsJsonArray("sells")) {
                  JsonObject s = se.getAsJsonObject();
                  Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(s.get("item").getAsString()));
                  if (item.isEmpty()) {
                     unknown.add(s.get("item").getAsString());
                  } else {
                     sells.add(
                        new Profession.Offer(
                           item.get(),
                           s.has("count") ? s.get("count").getAsInt() : 1,
                           s.get("cost").getAsInt(),
                           s.has("potion") ? s.get("potion").getAsString() : "",
                           s.has("enchantment") ? s.get("enchantment").getAsString() : "",
                           s.has("level") ? s.get("level").getAsInt() : 1
                        )
                     );
                  }
               }

               offers += sells.size();
               ranks.add(new Profession.Rank(r.get("name").getAsString(), Deed.parse(r.getAsJsonObject("deed")), List.copyOf(sells)));
            }

            Item icon = BuiltInRegistries.ITEM
               .getOptional(Identifier.parse(o.has("icon") ? o.get("icon").getAsString() : "minecraft:emerald"))
               .orElse(Items.EMERALD);
            loaded.put(
               id,
               new Profession(
                  id,
                  o.get("name").getAsString(),
                  o.has("description") ? o.get("description").getAsString() : "",
                  o.has("outfit") ? o.get("outfit").getAsString() : "",
                  icon,
                  o.has("order") ? o.get("order").getAsInt() : 99,
                  List.copyOf(ranks)
               )
            );
         } catch (Exception var20) {
            Ahrivaea.LOGGER.error("Could not load villager profession {}: {}", id, var20.toString());
         }
      }

      all = Map.copyOf(loaded);
      Ahrivaea.LOGGER
         .info(
            "Loaded {} villager professions selling {} items{}",
            new Object[]{all.size(), offers, unknown.isEmpty() ? "" : "; unknown items skipped: " + String.join(", ", unknown)}
         );
   }

   public static record Offer(Item item, int count, int cost, String potion, String enchantment, int level) {
      public ItemStack stack(Provider registries) {
         ItemStack stack = new ItemStack(this.item, this.count);
         if (!this.potion.isEmpty()) {
            BuiltInRegistries.POTION.get(Identifier.parse(this.potion)).ifPresent(p -> stack.set(DataComponents.POTION_CONTENTS, new PotionContents(p)));
         }

         if (!this.enchantment.isEmpty()) {
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(this.enchantment));
            registries.lookup(Registries.ENCHANTMENT).flatMap(l -> l.get(key)).ifPresent(e -> {
               Mutable book = new Mutable(ItemEnchantments.EMPTY);
               book.set(e, this.level);
               stack.set(DataComponents.STORED_ENCHANTMENTS, book.toImmutable());
            });
         }

         return stack;
      }
   }

   public static record Rank(String name, Deed deed, List<Profession.Offer> sells) {
   }
}
