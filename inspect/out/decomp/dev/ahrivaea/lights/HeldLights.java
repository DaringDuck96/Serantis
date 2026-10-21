package dev.ahrivaea.lights;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;
import net.minecraft.world.level.material.Fluids;

public final class HeldLights {
   public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true)
      .category(GameRuleCategory.PLAYER)
      .buildAndRegister(Identifier.fromNamespaceAndPath("ahrivaea", "held_lights"));
   public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(
      Identifier.fromNamespaceAndPath("ahrivaea", "held_lights"), b -> b.persistent(Codec.LONG.listOf()).initializer(List::of)
   );
   public static final Map<Item, Integer> GLOWING = Map.ofEntries(
      Map.entry(Items.LAVA_BUCKET, 15),
      Map.entry(Items.GLOW_BERRIES, 14),
      Map.entry(Items.NETHER_STAR, 12),
      Map.entry(Items.END_CRYSTAL, 12),
      Map.entry(Items.BLAZE_ROD, 10),
      Map.entry(Items.FIRE_CHARGE, 10),
      Map.entry(Items.BLAZE_POWDER, 8),
      Map.entry(Items.GLOWSTONE_DUST, 8),
      Map.entry(Items.EXPERIENCE_BOTTLE, 6),
      Map.entry(Items.GLOW_INK_SAC, 6),
      Map.entry(Items.MAGMA_CREAM, 6),
      Map.entry(Items.SPECTRAL_ARROW, 6)
   );
   private static final Map<UUID, HeldLights.Lit> LIT = new HashMap<>();
   private static final Map<ResourceKey<Level>, List<BlockPos>> LEFTOVER = new HashMap<>();
   private static final List<HeldLights.Lit> CLEAR = new ArrayList<>();

   private HeldLights() {
   }

   public static void init() {
      ServerTickEvents.END_SERVER_TICK.register(HeldLights::tick);
      ServerLifecycleEvents.SERVER_STARTED.register((ServerStarted)server -> {
         LIT.clear();
         LEFTOVER.clear();

         for (ServerLevel level : server.getAllLevels()) {
            List<Long> saved = (List<Long>)level.getAttachedOrCreate(PLACED);
            if (!saved.isEmpty()) {
               List<BlockPos> list = new ArrayList<>();

               for (long l : saved) {
                  list.add(BlockPos.of(l));
               }

               LEFTOVER.put(level.dimension(), list);
            }
         }
      });
      ServerChunkEvents.CHUNK_LOAD.register((Load)(level, chunk, generated) -> {
         List<BlockPos> left = LEFTOVER.get(level.dimension());
         if (left != null && !left.isEmpty()) {
            Iterator<BlockPos> it = left.iterator();

            while (it.hasNext()) {
               BlockPos p = it.next();
               if (p.getX() >> 4 == chunk.getPos().x() && p.getZ() >> 4 == chunk.getPos().z()) {
                  synchronized (CLEAR) {
                     CLEAR.add(new HeldLights.Lit(level.dimension(), p));
                  }

                  it.remove();
               }
            }
         }
      });
      ServerLifecycleEvents.SERVER_STOPPING.register((ServerStopping)server -> {
         for (Entry<UUID, HeldLights.Lit> e : new ArrayList<>(LIT.entrySet())) {
            remove(server, e.getValue());
         }

         LIT.clear();
      });
   }

   public static int lightOf(ItemStack s) {
      if (s.isEmpty()) {
         return 0;
      } else {
         Integer glow = GLOWING.get(s.getItem());
         if (glow != null) {
            return glow;
         } else {
            return s.getItem() instanceof BlockItem bi ? bi.getBlock().defaultBlockState().getLightEmission() : 0;
         }
      }
   }

   public static int lightOf(ServerPlayer p) {
      return Math.max(lightOf(p.getMainHandItem()), lightOf(p.getOffhandItem()));
   }

   private static void tick(MinecraftServer server) {
      List<HeldLights.Lit> clear;
      synchronized (CLEAR) {
         clear = new ArrayList<>(CLEAR);
         CLEAR.clear();
      }

      for (HeldLights.Lit l : clear) {
         remove(server, l);
      }

      Map<UUID, Boolean> seen = new HashMap<>();

      for (ServerPlayer p : server.getPlayerList().getPlayers()) {
         seen.put(p.getUUID(), true);
         ServerLevel level = p.level();
         int light = level.getGameRules().get(ENABLED) && p.isAlive() && !p.isSpectator() ? lightOf(p) : 0;
         HeldLights.Lit now = LIT.get(p.getUUID());
         BlockPos spot = light > 0 ? spot(level, p, now) : null;
         if (now == null
            || spot == null
            || now.dimension() != level.dimension()
            || !now.pos().equals(spot)
            || !(level.getBlockState(spot).getBlock() instanceof LightBlock)
            || (Integer)level.getBlockState(spot).getValue(LightBlock.LEVEL) != light) {
            if (now != null) {
               remove(server, now);
               LIT.remove(p.getUUID());
            }

            if (spot != null && place(level, spot, light)) {
               LIT.put(p.getUUID(), new HeldLights.Lit(level.dimension(), spot));
            }
         }
      }

      Iterator<Entry<UUID, HeldLights.Lit>> it = LIT.entrySet().iterator();

      while (it.hasNext()) {
         Entry<UUID, HeldLights.Lit> e = it.next();
         if (!seen.containsKey(e.getKey())) {
            remove(server, e.getValue());
            it.remove();
         }
      }
   }

   private static BlockPos spot(ServerLevel level, ServerPlayer p, HeldLights.Lit now) {
      BlockPos eye = BlockPos.containing(p.getEyePosition());

      for (BlockPos c : new BlockPos[]{eye, p.blockPosition(), eye.above()}) {
         if (now != null && now.dimension() == level.dimension() && now.pos().equals(c)) {
            return c;
         }

         if (free(level, c)) {
            return c;
         }
      }

      return null;
   }

   private static boolean free(ServerLevel level, BlockPos p) {
      if (!level.isInWorldBounds(p)) {
         return false;
      } else {
         BlockState s = level.getBlockState(p);
         return s.isAir() || s.is(Blocks.WATER) && s.getFluidState().isSource();
      }
   }

   private static boolean place(ServerLevel level, BlockPos p, int light) {
      BlockState s = level.getBlockState(p);
      boolean water = s.is(Blocks.WATER);
      if (!s.isAir() && !water && !(s.getBlock() instanceof LightBlock)) {
         return false;
      } else {
         if (s.getBlock() instanceof LightBlock) {
            water = (Boolean)s.getValue(LightBlock.WATERLOGGED);
         }

         level.setBlock(
            p, (BlockState)((BlockState)Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, light)).setValue(LightBlock.WATERLOGGED, water), 2
         );
         remember(level, p, true);
         return true;
      }
   }

   private static void remove(MinecraftServer server, HeldLights.Lit l) {
      ServerLevel level = server.getLevel(l.dimension());
      if (level != null) {
         remember(level, l.pos(), false);
         if (level.isLoaded(l.pos())) {
            BlockState s = level.getBlockState(l.pos());
            if (s.getBlock() instanceof LightBlock) {
               level.setBlock(
                  l.pos(), s.getValue(LightBlock.WATERLOGGED) ? Fluids.WATER.defaultFluidState().createLegacyBlock() : Blocks.AIR.defaultBlockState(), 2
               );
            }
         }
      }
   }

   private static void remember(ServerLevel level, BlockPos p, boolean placed) {
      List<Long> list = (List<Long>)level.getAttachedOrCreate(PLACED);
      boolean has = list.contains(p.asLong());
      if (has != placed) {
         List<Long> copy = new ArrayList<>(list);
         if (placed) {
            copy.add(p.asLong());
         } else {
            copy.remove(p.asLong());
         }

         level.setAttached(PLACED, copy);
      }
   }

   public static BlockPos litAt(ServerPlayer p) {
      HeldLights.Lit l = LIT.get(p.getUUID());
      return l == null ? null : l.pos();
   }

   private static record Lit(ResourceKey<Level> dimension, BlockPos pos) {
   }
}
