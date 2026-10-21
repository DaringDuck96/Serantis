package dev.ahrivaea.battle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.ahrivaea.Ahrivaea;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record ArenaReturn(String dimension, double x, double y, double z, float yaw, float pitch) {
   public static final Codec<ArenaReturn> CODEC = RecordCodecBuilder.create(
      i -> i.group(
               Codec.STRING.fieldOf("dimension").forGetter(ArenaReturn::dimension),
               Codec.DOUBLE.fieldOf("x").forGetter(ArenaReturn::x),
               Codec.DOUBLE.fieldOf("y").forGetter(ArenaReturn::y),
               Codec.DOUBLE.fieldOf("z").forGetter(ArenaReturn::z),
               Codec.FLOAT.fieldOf("yaw").forGetter(ArenaReturn::yaw),
               Codec.FLOAT.fieldOf("pitch").forGetter(ArenaReturn::pitch)
            )
            .apply(i, ArenaReturn::new)
   );
   public static final AttachmentType<ArenaReturn> ATTACHMENT = AttachmentRegistry.create(Ahrivaea.id("arena_return"), b -> b.persistent(CODEC));

   public static ArenaReturn of(ServerPlayer p) {
      return new ArenaReturn(p.level().dimension().identifier().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot());
   }

   public static void sendBack(ServerPlayer p) {
      ArenaReturn r = (ArenaReturn)p.getAttached(ATTACHMENT);
      p.removeAttached(ATTACHMENT);
      MinecraftServer server = p.level().getServer();
      ServerLevel to = null;
      if (r != null) {
         to = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(r.dimension())));
      }

      if (r != null && to != null) {
         Vec3 at = safeSpot(to, new Vec3(r.x(), r.y(), r.z()));
         if (at == null) {
            ServerLevel overworld = server.overworld();
            to = overworld;
            BlockPos spawn = overworld.getRespawnData().pos();
            at = safeSpot(overworld, Vec3.atBottomCenterOf(spawn));
            if (at == null) {
               at = Vec3.atBottomCenterOf(overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, spawn));
            }
         }

         p.teleportTo(to, at.x, at.y, at.z, Set.of(), r.yaw(), r.pitch(), true);
         p.resetFallDistance();
      } else if (p.level().dimension().equals(Arena.DIMENSION)) {
         ServerLevel overworld = server.overworld();
         BlockPos spawn = overworld.getRespawnData().pos();
         Vec3 at = safeSpot(overworld, Vec3.atBottomCenterOf(spawn));
         if (at == null) {
            at = Vec3.atBottomCenterOf(overworld.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, spawn));
         }

         p.teleportTo(overworld, at.x, at.y, at.z, Set.of(), p.getYRot(), p.getXRot(), true);
         p.resetFallDistance();
      }
   }

   public static Vec3 safeSpot(ServerLevel level, Vec3 want) {
      BlockPos start = BlockPos.containing(want);
      level.getChunk(start.getX() >> 4, start.getZ() >> 4);
      if (safeExact(level, want)) {
         return want;
      } else {
         for (int r = 0; r <= 8; r++) {
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3, 4, -4, 6, -6, 8, -8, 12, -12}) {
               for (int dx = -r; dx <= r; dx++) {
                  for (int dz = -r; dz <= r; dz++) {
                     if (Math.max(Math.abs(dx), Math.abs(dz)) == r) {
                        BlockPos q = start.offset(dx, dy, dz);
                        if (r > 0 && (dx != 0 || dz != 0)) {
                           level.getChunk(q.getX() >> 4, q.getZ() >> 4);
                        }

                        if (safe(level, q)) {
                           return Vec3.atBottomCenterOf(q);
                        }
                     }
                  }
               }
            }
         }

         BlockPos top = level.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, start);
         return safe(level, top) ? Vec3.atBottomCenterOf(top) : null;
      }
   }

   private static boolean safeExact(ServerLevel level, Vec3 at) {
      AABB body = new AABB(at.x - 0.3, at.y, at.z - 0.3, at.x + 0.3, at.y + 1.8, at.z + 0.3);
      if (!level.noCollision(body)) {
         return false;
      } else {
         BlockPos ground = BlockPos.containing(at.x, at.y - 0.05, at.z);
         BlockState g = level.getBlockState(ground);
         if (!g.getCollisionShape(level, ground).isEmpty() && !harmfulGround(g)) {
            BlockPos feet = BlockPos.containing(at);
            return harmless(level.getBlockState(feet)) && harmless(level.getBlockState(feet.above())) && harmless(level.getBlockState(feet.above(2)));
         } else {
            return false;
         }
      }
   }

   private static boolean harmfulGround(BlockState s) {
      return s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CACTUS) || s.is(BlockTags.CAMPFIRES) || s.is(Blocks.POINTED_DRIPSTONE) || !s.getFluidState().isEmpty();
   }

   private static boolean harmless(BlockState s) {
      return !s.getFluidState().is(FluidTags.LAVA)
         && !s.is(BlockTags.FIRE)
         && !s.is(Blocks.POWDER_SNOW)
         && !s.is(Blocks.SWEET_BERRY_BUSH)
         && !s.is(Blocks.COBWEB)
         && !s.is(Blocks.WITHER_ROSE)
         && !s.is(Blocks.NETHER_PORTAL)
         && !s.is(Blocks.END_PORTAL);
   }

   public static boolean safe(ServerLevel level, BlockPos feet) {
      if (feet.getY() > level.getMinY() && feet.getY() + 1 < level.getMaxY()) {
         BlockState below = level.getBlockState(feet.below());
         return below.isFaceSturdy(level, feet.below(), Direction.UP) && !harmfulGround(below) ? open(level, feet) && open(level, feet.above()) : false;
      } else {
         return false;
      }
   }

   private static boolean open(ServerLevel level, BlockPos p) {
      BlockState s = level.getBlockState(p);
      return s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty() && harmless(s);
   }
}
