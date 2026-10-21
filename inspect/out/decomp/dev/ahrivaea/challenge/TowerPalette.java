package dev.ahrivaea.challenge;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public record TowerPalette(int priority, List<String> biomes, Map<Identifier, Identifier> blocks, Optional<Identifier> foundation, String id) {
   public static final Codec<TowerPalette> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.INT.optionalFieldOf("priority", 0).forGetter(TowerPalette::priority),
               Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(TowerPalette::biomes),
               Codec.unboundedMap(Identifier.CODEC, Identifier.CODEC).optionalFieldOf("blocks", Map.of()).forGetter(TowerPalette::blocks),
               Identifier.CODEC.optionalFieldOf("foundation").forGetter(TowerPalette::foundation),
               Codec.STRING.optionalFieldOf("id", "").forGetter(TowerPalette::id)
            )
            .apply(i, TowerPalette::new)
   );
   private static List<TowerPalette> all = List.of();

   public static void reload(ResourceManager manager) {
      List<TowerPalette> loaded = new ArrayList<>();
      manager.listResources("tower_palette", path -> path.getPath().endsWith(".json")).forEach((path, resource) -> {
         String id = path.getPath().substring("tower_palette/".length(), path.getPath().length() - ".json".length());

         try (Reader reader = resource.openAsReader()) {
            TowerPalette p = (TowerPalette)CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader)).getOrThrow();

            for (Entry<Identifier, Identifier> e : p.blocks.entrySet()) {
               if (BuiltInRegistries.BLOCK.getOptional(e.getValue()).isEmpty()) {
                  TowersMod.LOGGER.warn("Tower palette {}: unknown block {}", id, e.getValue());
               }
            }

            loaded.add(new TowerPalette(p.priority, p.biomes, p.blocks, p.foundation, id));
         } catch (Exception var10) {
            TowersMod.LOGGER.error("Could not load tower palette {}: {}", path, var10.getMessage());
         }
      });
      loaded.sort(Comparator.comparingInt(TowerPalette::priority).reversed().thenComparing(TowerPalette::id));
      all = List.copyOf(loaded);
      TowersMod.LOGGER.info("Loaded {} tower palettes", all.size());
   }

   public static TowerPalette get(String id) {
      for (TowerPalette p : all) {
         if (p.id.equals(id)) {
            return p;
         }
      }

      return new TowerPalette(0, List.of(), Map.of(), Optional.empty(), "none");
   }

   public static TowerPalette pick(Holder<Biome> biome) {
      for (TowerPalette p : all) {
         if (!p.biomes.isEmpty() && matchesBiome(p.biomes, biome)) {
            return p;
         }
      }

      for (TowerPalette px : all) {
         if (px.biomes.isEmpty()) {
            return px;
         }
      }

      return get("none");
   }

   static boolean matchesBiome(List<String> entries, Holder<Biome> biome) {
      for (String b : entries) {
         if (b.startsWith("#")) {
            if (biome.is(TagKey.create(Registries.BIOME, Identifier.parse(b.substring(1))))) {
               return true;
            }
         } else if (biome.is(Identifier.parse(b))) {
            return true;
         }
      }

      return false;
   }

   public BlockState foundationState() {
      return this.foundation
         .flatMap(BuiltInRegistries.BLOCK::getOptional)
         .<BlockState>map(Block::defaultBlockState)
         .orElse(Blocks.STONE_BRICKS.defaultBlockState());
   }

   public BlockState swap(BlockState state) {
      Identifier to = this.blocks.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
      if (to == null) {
         return state;
      } else {
         Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(to);
         if (block.isEmpty()) {
            return state;
         } else {
            BlockState out = block.get().defaultBlockState();

            for (Property<?> p : out.getProperties()) {
               for (Property<?> old : state.getProperties()) {
                  if (old.getName().equals(p.getName())) {
                     out = copy(out, p, nameOf(state, old));
                  }
               }
            }

            return out;
         }
      }
   }

   private static <T extends Comparable<T>> String nameOf(BlockState state, Property<T> p) {
      return p.getName(state.getValue(p));
   }

   private static <T extends Comparable<T>> BlockState copy(BlockState state, Property<T> p, String value) {
      return p.getValue(value).map(v -> (BlockState)state.setValue(p, v)).orElse(state);
   }

   public StructurePlaceSettings settings() {
      return new StructurePlaceSettings().addProcessor(new TowerPalette.Processor(this));
   }

   private static record Processor(TowerPalette palette) implements StructureProcessor {
      public StructureBlockInfo processBlock(LevelReader level, BlockPos a, BlockPos b, BlockPos c, StructureBlockInfo current, StructurePlaceSettings settings) {
         if (current == null) {
            return null;
         } else {
            BlockState swapped = this.palette.swap(current.state());
            return swapped == current.state() ? current : new StructureBlockInfo(current.pos(), swapped, current.nbt());
         }
      }

      public MapCodec<? extends StructureProcessor> codec() {
         return MapCodec.unit(this);
      }
   }
}
