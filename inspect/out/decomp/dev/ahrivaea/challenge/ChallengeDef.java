package dev.ahrivaea.challenge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

public record ChallengeDef(
   String name,
   String unit,
   Map<String, Integer> items,
   int goal,
   int stages,
   Identifier ruined,
   Identifier finished,
   BlockPos chest,
   List<ChallengeDef.Reward> rewards,
   String id
) {
   public static final Codec<ChallengeDef> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.STRING.fieldOf("name").forGetter(ChallengeDef::name),
               Codec.STRING.optionalFieldOf("unit", "items").forGetter(ChallengeDef::unit),
               Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("items").forGetter(ChallengeDef::items),
               Codec.INT.fieldOf("goal").forGetter(ChallengeDef::goal),
               Codec.intRange(1, 64).optionalFieldOf("stages", 4).forGetter(ChallengeDef::stages),
               Identifier.CODEC.fieldOf("ruined").forGetter(ChallengeDef::ruined),
               Identifier.CODEC.fieldOf("finished").forGetter(ChallengeDef::finished),
               BlockPos.CODEC.fieldOf("chest").forGetter(ChallengeDef::chest),
               ChallengeDef.Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(ChallengeDef::rewards),
               Codec.STRING.optionalFieldOf("id", "").forGetter(ChallengeDef::id)
            )
            .apply(i, ChallengeDef::new)
   );
   private static Map<String, ChallengeDef> all = Map.of();
   private static List<String> order = List.of();

   public static void reload(ResourceManager manager) {
      Map<String, ChallengeDef> loaded = new LinkedHashMap<>();
      manager.listResources("challenge", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
         String idx = path.getPath().substring("challenge/".length(), path.getPath().length() - ".json".length());

         try (Reader reader = resource.openAsReader()) {
            ChallengeDef d = (ChallengeDef)CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();

            for (String item : d.items.keySet()) {
               if (!item.startsWith("#") && !BuiltInRegistries.ITEM.containsKey(Identifier.parse(item))) {
                  TowersMod.LOGGER.warn("Challenge {}: unknown item {}", idx, item);
               }
            }

            for (ChallengeDef.Reward r : d.rewards) {
               for (ChallengeDef.RewardItem it : r.items()) {
                  if (!BuiltInRegistries.ITEM.containsKey(it.item())) {
                     TowersMod.LOGGER.warn("Challenge {}: unknown reward item {}", idx, it.item());
                  }
               }
            }

            loaded.put(idx, new ChallengeDef(d.name, d.unit, d.items, d.goal, d.stages, d.ruined, d.finished, d.chest, d.rewards, idx));
         } catch (Exception var12) {
            TowersMod.LOGGER.error("Could not load challenge {}: {}", path, var12.getMessage());
         }
      });
      all = Map.copyOf(loaded);
      List<String> newOrder = new ArrayList<>();
      manager.getResourceStack(TowersMod.id("challenge_order.json")).forEach(resource -> {
         try (Reader reader = resource.openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            newOrder.clear();
            json.getAsJsonArray("towers").forEach(ex -> newOrder.add(ex.getAsString()));
         } catch (Exception var7) {
            TowersMod.LOGGER.error("Could not load challenge_order.json: {}", var7.getMessage());
         }
      });

      for (String id : newOrder) {
         if (!all.containsKey(id)) {
            TowersMod.LOGGER.warn("challenge_order.json lists {}, which has no challenge file", id);
         }
      }

      order = List.copyOf(newOrder);
      TowersMod.LOGGER.info("Loaded {} challenges: {}; tower order: {}", new Object[]{all.size(), String.join(", ", all.keySet()), String.join(", ", order)});
   }

   public static Optional<ChallengeDef> get(String id) {
      return Optional.ofNullable(all.get(id));
   }

   public static Map<String, ChallengeDef> all() {
      return all;
   }

   public int worth(ItemStack stack) {
      Integer direct = this.items.get(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
      if (direct != null) {
         return direct;
      } else {
         for (Entry<String, Integer> e : this.items.entrySet()) {
            if (e.getKey().startsWith("#") && stack.is(TagKey.create(Registries.ITEM, Identifier.parse(e.getKey().substring(1))))) {
               return e.getValue();
            }
         }

         return 0;
      }
   }

   public static Optional<ChallengeDef> forTower(int index) {
      if (!order.isEmpty()) {
         Optional<ChallengeDef> d = get(order.get(index % order.size()));
         if (d.isPresent()) {
            return d;
         }
      }

      if (all.isEmpty()) {
         return Optional.empty();
      } else {
         List<ChallengeDef> list = List.copyOf(all.values());
         return Optional.of(list.get(index % list.size()));
      }
   }

   public static List<String> order() {
      return order;
   }

   public int threshold(int k) {
      return (int)Math.ceil((double)this.goal * (double)k / (double)this.stages);
   }

   public static record Reward(int rank, List<ChallengeDef.RewardItem> items) {
      public static final Codec<ChallengeDef.Reward> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Codec.INT.fieldOf("rank").forGetter(ChallengeDef.Reward::rank),
                  ChallengeDef.RewardItem.CODEC.listOf().fieldOf("items").forGetter(ChallengeDef.Reward::items)
               )
               .apply(i, ChallengeDef.Reward::new)
      );
   }

   public static record RewardItem(Identifier item, int count) {
      public static final Codec<ChallengeDef.RewardItem> CODEC = RecordCodecBuilder.create(
         i -> i.group(
                  Identifier.CODEC.fieldOf("item").forGetter(ChallengeDef.RewardItem::item),
                  Codec.INT.optionalFieldOf("count", 1).forGetter(ChallengeDef.RewardItem::count)
               )
               .apply(i, ChallengeDef.RewardItem::new)
      );
   }
}
