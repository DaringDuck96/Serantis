package dev.ahrivaea.stellar;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PlacedLogs {
   public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(
      StellarMod.id("placed_logs"), b -> b.persistent(Codec.LONG.listOf()).initializer(List::of)
   );

   private PlacedLogs() {
   }

   static void init() {
   }

   private static LevelChunk chunk(Level level, BlockPos pos) {
      return level.getChunkAt(pos);
   }

   public static boolean isPlaced(Level level, BlockPos pos) {
      return ((List)chunk(level, pos).getAttachedOrCreate(PLACED)).contains(pos.asLong());
   }

   public static void mark(Level level, BlockPos pos) {
      LevelChunk c = chunk(level, pos);
      List<Long> list = (List<Long>)c.getAttachedOrCreate(PLACED);
      if (!list.contains(pos.asLong())) {
         List<Long> copy = new ArrayList<>(list);
         copy.add(pos.asLong());
         c.setAttached(PLACED, copy);
         c.markUnsaved();
      }
   }

   public static void unmark(Level level, BlockPos pos) {
      LevelChunk c = chunk(level, pos);
      List<Long> list = (List<Long>)c.getAttachedOrCreate(PLACED);
      if (list.contains(pos.asLong())) {
         List<Long> copy = new ArrayList<>(list);
         copy.remove(pos.asLong());
         c.setAttached(PLACED, copy);
         c.markUnsaved();
      }
   }
}
