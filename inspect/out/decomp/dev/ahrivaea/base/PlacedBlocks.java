package dev.ahrivaea.base;

import com.mojang.serialization.Codec;
import dev.ahrivaea.Ahrivaea;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

public final class PlacedBlocks {
   public static final AttachmentType<List<Long>> PLACED = AttachmentRegistry.create(
      Ahrivaea.id("placed_blocks"), b -> b.persistent(Codec.LONG.listOf()).initializer(List::of)
   );

   private PlacedBlocks() {
   }

   public static boolean isPlaced(ServerLevel level, BlockPos pos) {
      LevelChunk c = level.getChunkAt(pos);
      List<Long> list = (List<Long>)c.getAttached(PLACED);
      return list != null && list.contains(pos.asLong());
   }

   public static void placed(ServerLevel level, BlockPos pos) {
      if (Bases.inAnyBaseColumn(level, pos)) {
         LevelChunk c = level.getChunkAt(pos);
         List<Long> list = (List<Long>)c.getAttachedOrCreate(PLACED);
         if (!list.contains(pos.asLong())) {
            List<Long> copy = new ArrayList<>(list);
            copy.add(pos.asLong());
            c.setAttached(PLACED, copy);
            c.markUnsaved();
         }
      }
   }

   public static void removed(ServerLevel level, BlockPos pos) {
      LevelChunk c = level.getChunkAt(pos);
      List<Long> list = (List<Long>)c.getAttached(PLACED);
      if (list != null && list.contains(pos.asLong())) {
         List<Long> copy = new ArrayList<>(list);
         copy.remove(pos.asLong());
         c.setAttached(PLACED, copy);
         c.markUnsaved();
      }
   }
}
