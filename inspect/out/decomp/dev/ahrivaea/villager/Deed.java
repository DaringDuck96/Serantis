package dev.ahrivaea.villager;

import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.party.Dex;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.StatType;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

public record Deed(String type, String of, int amount, String text) {
   public static Deed parse(JsonObject o) {
      String type = o.get("type").getAsString();
      String of = o.has("of") ? o.get("of").getAsString() : "";
      int amount = o.has("amount") ? o.get("amount").getAsInt() : 1;
      return new Deed(type, of, amount, o.has("text") ? o.get("text").getAsString() : type + " " + of + " x" + amount);
   }

   public boolean absolute() {
      return this.type.equals("advancement") || this.type.startsWith("dex_");
   }

   public int value(ServerPlayer p) {
      ServerStatsCounter stats = p.getStats();
      String var3 = this.type;

      return switch (var3) {
         case "mined" -> {
            int n = 0;

            for (Holder<Block> b : this.blocks()) {
               n += stats.getValue(Stats.BLOCK_MINED.get((Block)b.value()));
            }

            yield n;
         }
         case "crafted" -> this.items(p, Stats.ITEM_CRAFTED);
         case "used" -> this.items(p, Stats.ITEM_USED);
         case "picked_up" -> this.items(p, Stats.ITEM_PICKED_UP);
         case "custom" -> {
            Identifier id = (Identifier)BuiltInRegistries.CUSTOM_STAT.getValue(Identifier.parse(this.of));
            yield id == null ? 0 : stats.getValue(Stats.CUSTOM.get(id));
         }
         case "advancement" -> {
            AdvancementHolder a = p.level().getServer().getAdvancements().get(Identifier.parse(this.of));
            yield a != null && p.getAdvancements().getOrStartProgress(a).isDone() ? 1 : 0;
         }
         case "dex_seen" -> Dex.count(p, 1);
         case "dex_caught" -> Dex.count(p, 2);
         default -> 0;
      };
   }

   public int progress(ServerPlayer p, int baseline) {
      int v = this.value(p) - (this.absolute() ? 0 : baseline);
      return Math.clamp((long)v, 0, this.amount);
   }

   private List<Holder<Block>> blocks() {
      List<Holder<Block>> out = new ArrayList<>();
      if (this.of.startsWith("#")) {
         for (Holder<Block> h : BuiltInRegistries.BLOCK.getTagOrEmpty(TagKey.create(Registries.BLOCK, Identifier.parse(this.of.substring(1))))) {
            out.add(h);
         }
      } else {
         BuiltInRegistries.BLOCK.get(Identifier.parse(this.of)).ifPresent(out::add);
      }

      return out;
   }

   private int items(ServerPlayer p, StatType<Item> statType) {
      int n = 0;
      if (this.of.startsWith("#")) {
         for (Holder<Item> h : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, Identifier.parse(this.of.substring(1))))) {
            n += p.getStats().getValue(statType.get((Item)h.value()));
         }
      } else {
         Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(this.of));
         if (item.isPresent()) {
            n += p.getStats().getValue(statType.get(item.get()));
         } else {
            Ahrivaea.LOGGER.warn("Villager deed: unknown item {}", this.of);
         }
      }

      return n;
   }
}
