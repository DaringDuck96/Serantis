package dev.ahrivaea.map.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.ahrivaea.map.MapMod;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.MapColor.Brightness;
import net.minecraft.world.level.storage.LevelResource;

public final class MapTiles {
   public static final int SIZE = 256;
   private static final int PER_TICK = 24;
   private static String folder;
   private static final Map<Long, MapTiles.Tile> TILES = new HashMap<>();
   private static final Set<Long> NONE = new HashSet<>();
   private static final LinkedHashSet<Long> QUEUE = new LinkedHashSet<>();
   private static int nextTexture;

   private MapTiles() {
   }

   static String folderNow(Minecraft mc) {
      if (mc.level == null) {
         return null;
      } else {
         String world;
         if (mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            world = "singleplayer_" + clean(String.valueOf(root.getFileName()));
         } else {
            world = "server_" + clean(mc.getCurrentServer() == null ? "unknown" : mc.getCurrentServer().ip);
         }

         Identifier dim = mc.level.dimension().identifier();
         return world + "/" + clean(dim.getNamespace()) + "_" + clean(dim.getPath());
      }
   }

   private static String clean(String s) {
      return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
   }

   public static Path dir() {
      return Minecraft.getInstance().gameDirectory.toPath().resolve("ahrivaea_map").resolve(folder);
   }

   static void tick(Minecraft mc, boolean on) {
      String now = on ? folderNow(mc) : null;
      if (!Objects.equals(now, folder)) {
         closeAll();
         folder = now;
         if (now != null) {
            queueAround(mc, mc.options.getEffectiveRenderDistance() + 1);
         }
      }

      if (folder != null) {
         ClientLevel level = mc.level;
         int n = 0;
         Iterator<Long> it = QUEUE.iterator();

         while (it.hasNext() && n < 24) {
            long c = it.next();
            it.remove();
            int cx = ChunkPos.getX(c);
            int cz = ChunkPos.getZ(c);
            if (level.hasChunk(cx, cz)) {
               scan(level, level.getChunk(cx, cz));
               n++;
            }
         }
      }
   }

   static boolean queueEmpty() {
      return QUEUE.isEmpty();
   }

   static void queue(int cx, int cz) {
      QUEUE.add(Long.valueOf(ChunkPos.pack(cx, cz)));
   }

   static void queueAround(Minecraft mc, int radius) {
      if (mc.player != null) {
         int px = mc.player.getBlockX() >> 4;
         int pz = mc.player.getBlockZ() >> 4;

         for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
               for (int dz = -r; dz <= r; dz++) {
                  if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                     queue(px + dx, pz + dz);
                  }
               }
            }
         }
      }
   }

   public static void scan(ClientLevel level, LevelChunk chunk) {
      if (folder != null) {
         int x0 = chunk.getPos().getMinBlockX();
         int z0 = chunk.getPos().getMinBlockZ();
         MapTiles.Tile t = tile(Math.floorDiv(x0, 256), Math.floorDiv(z0, 256), true);
         boolean ceiling = level.dimensionType().hasCeiling();
         int[] heights = new int[17];
         MutableBlockPos pos = new MutableBlockPos();
         boolean north = level.hasChunk(chunk.getPos().x(), chunk.getPos().z() - 1);
         LevelChunk northChunk = north ? level.getChunk(chunk.getPos().x(), chunk.getPos().z() - 1) : null;

         for (int lx = 0; lx < 16; lx++) {
            heights[lx] = northChunk == null ? Integer.MIN_VALUE : top(level, northChunk, lx, 15, ceiling, pos);
         }

         for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
               int y = top(level, chunk, lx, lz, ceiling, pos);
               int above = heights[lx];
               heights[lx] = y;
               int color;
               if (y == Integer.MIN_VALUE) {
                  color = -15724528;
               } else {
                  pos.set(x0 + lx, y, z0 + lz);
                  BlockState s = chunk.getBlockState(pos);
                  FluidState f = s.getFluidState();
                  if (f.is(FluidTags.WATER)) {
                     int depth = 0;

                     while (depth < 12 && chunk.getBlockState(pos.move(0, -1, 0)).getFluidState().is(FluidTags.WATER)) {
                        depth++;
                     }

                     double d = (double)depth * 0.1 + (double)(lx + lz & 1) * 0.2;
                     color = MapColor.WATER.calculateARGBColor(d < 0.5 ? Brightness.HIGH : (d > 0.9 ? Brightness.LOW : Brightness.NORMAL));
                  } else {
                     MapColor mc = s.getMapColor(level, pos);
                     Brightness b = above != Integer.MIN_VALUE && above != y ? (y > above ? Brightness.HIGH : Brightness.LOW) : Brightness.NORMAL;
                     color = mc.calculateARGBColor(b);
                  }
               }

               int px = Math.floorMod(x0 + lx, 256);
               int pz = Math.floorMod(z0 + lz, 256);
               if (t.image.getPixel(px, pz) != color) {
                  t.image.setPixel(px, pz, color);
                  t.upload = true;
                  t.save = true;
               }
            }
         }
      }
   }

   private static int top(ClientLevel level, LevelChunk chunk, int lx, int lz, boolean ceiling, MutableBlockPos pos) {
      int x = chunk.getPos().getMinBlockX() + lx;
      int z = chunk.getPos().getMinBlockZ() + lz;
      int min = level.getMinY();
      int y;
      if (!ceiling) {
         y = chunk.getHeight(Types.WORLD_SURFACE, lx, lz);
      } else {
         y = min + level.dimensionType().logicalHeight() - 1;

         while (y > min && !chunk.getBlockState(pos.set(x, y, z)).isAir()) {
            y--;
         }
      }

      while (y >= min) {
         BlockState s = chunk.getBlockState(pos.set(x, y, z));
         if (!s.getFluidState().isEmpty() || s.getMapColor(level, pos) != MapColor.NONE) {
            return y;
         }

         y--;
      }

      return Integer.MIN_VALUE;
   }

   public static MapTiles.Tile tile(int tx, int tz, boolean create) {
      if (folder == null) {
         return null;
      } else {
         long key = ChunkPos.pack(tx, tz);
         MapTiles.Tile t = TILES.get(key);
         if (t != null) {
            return t;
         } else {
            NativeImage img = null;
            if (!NONE.contains(key)) {
               Path f = dir().resolve("tile." + tx + "." + tz + ".png");
               if (Files.exists(f)) {
                  try (InputStream in = Files.newInputStream(f)) {
                     img = NativeImage.read(in);
                     if (img.getWidth() != 256 || img.getHeight() != 256) {
                        img.close();
                        img = null;
                     }
                  } catch (IOException var13) {
                     MapMod.LOGGER.warn("Couldn't read map tile {}: {}", f, var13.toString());
                  }
               }

               if (img == null) {
                  NONE.add(key);
               }
            }

            if (img == null) {
               if (!create) {
                  return null;
               }

               img = new NativeImage(256, 256, true);
            }

            t = new MapTiles.Tile(tx, tz, img, Identifier.fromNamespaceAndPath("ahrivaea_map", "tile/" + nextTexture++));
            TILES.put(key, t);
            NONE.remove(key);
            return t;
         }
      }
   }

   static void ready(MapTiles.Tile t) {
      if (t.upload) {
         t.texture.upload();
         t.upload = false;
      }
   }

   public static void saveAll() {
      if (folder != null) {
         try {
            Files.createDirectories(dir());
         } catch (IOException var4) {
            MapMod.LOGGER.warn("Couldn't make the map folder: {}", var4.toString());
            return;
         }

         for (MapTiles.Tile t : TILES.values()) {
            if (t.save) {
               try {
                  t.image.writeToFile(dir().resolve("tile." + t.tx + "." + t.tz + ".png"));
                  t.save = false;
               } catch (IOException var3) {
                  MapMod.LOGGER.warn("Couldn't save map tile: {}", var3.toString());
               }
            }
         }
      }
   }

   static void closeAll() {
      saveAll();

      for (MapTiles.Tile t : new ArrayList<>(TILES.values())) {
         Minecraft.getInstance().getTextureManager().release(t.id);
      }

      TILES.clear();
      NONE.clear();
   }

   public static int explored() {
      int n = 0;

      for (MapTiles.Tile t : TILES.values()) {
         for (int x = 0; x < 256; x++) {
            for (int z = 0; z < 256; z++) {
               if (t.image.getPixel(x, z) >>> 24 != 0) {
                  n++;
               }
            }
         }
      }

      return n;
   }

   public static final class Tile {
      public final int tx;
      public final int tz;
      final NativeImage image;
      final DynamicTexture texture;
      public final Identifier id;
      boolean upload = true;
      boolean save;

      Tile(int tx, int tz, NativeImage image, Identifier id) {
         this.tx = tx;
         this.tz = tz;
         this.image = image;
         this.id = id;
         this.texture = new DynamicTexture(() -> "ahrivaea_map " + id, image);
         Minecraft.getInstance().getTextureManager().register(id, this.texture);
      }
   }
}
