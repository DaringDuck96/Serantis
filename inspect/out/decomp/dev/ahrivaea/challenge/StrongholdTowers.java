package dev.ahrivaea.challenge;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents.Load;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.EndTick;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces.PortalRoom;

public final class StrongholdTowers {
   private static final Set<ChunkPos> PENDING = new LinkedHashSet<>();
   private static boolean generating;

   private StrongholdTowers() {
   }

   public static void init() {
      ServerChunkEvents.CHUNK_LOAD.register((Load)(level, chunk, generated) -> {
         if (level.dimension() == Level.OVERWORLD) {
            Structure stronghold = stronghold(level);
            if (stronghold != null) {
               StructureStart start = chunk.getStartForStructure(stronghold);
               if (start != null && start.isValid()) {
                  PENDING.add(chunk.getPos());
               }
            }
         }
      });
      ServerTickEvents.END_SERVER_TICK.register((EndTick)server -> {
         if (!PENDING.isEmpty()) {
            ServerLevel level = server.overworld();
            List<ChunkPos> todo = List.copyOf(PENDING);
            PENDING.clear();

            for (ChunkPos pos : todo) {
               int index = indexOf(level, pos);
               if (index >= 0) {
                  if (Challenges.state(server, towerId(index)).isEmpty()) {
                     generate(level, index);
                  }

                  Optional<ChallengeState> st = Challenges.state(server, towerId(index));
                  if (st.isPresent() && !st.get().complete && st.get().portalRoom.isEmpty()) {
                     Challenges.sealPortalRoom(level, towerId(index), portalRoom(level, pos));
                  }
               }
            }
         }
      });
   }

   public static String towerId(int index) {
      return "tower_" + (index + 1);
   }

   private static Structure stronghold(ServerLevel level) {
      return (Structure)level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(BuiltinStructures.STRONGHOLD);
   }

   private static List<ChunkPos> rings(ServerLevel level) {
      StructureSet set = (StructureSet)level.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET).getValue(BuiltinStructureSets.STRONGHOLDS);
      if (set != null && set.placement() instanceof ConcentricRingsStructurePlacement rings) {
         List<ChunkPos> list = level.getChunkSource().getGeneratorState().getRingPositionsFor(rings);
         return list == null ? List.of() : list;
      } else {
         return List.of();
      }
   }

   public static int indexOf(ServerLevel level, ChunkPos pos) {
      return rings(level).indexOf(pos);
   }

   public static BoundingBox portalRoom(ServerLevel level, ChunkPos cp) {
      Structure stronghold = stronghold(level);
      if (stronghold == null) {
         return null;
      } else {
         StructureStart start = level.getChunk(cp.x(), cp.z()).getStartForStructure(stronghold);
         if (start != null && start.isValid()) {
            for (StructurePiece piece : start.getPieces()) {
               if (piece instanceof PortalRoom) {
                  return piece.getBoundingBox();
               }
            }

            return null;
         } else {
            return null;
         }
      }
   }

   public static BlockPos generate(ServerLevel level, int index) {
      List<ChunkPos> rings = rings(level);
      if (index >= 0 && index < rings.size()) {
         String id = towerId(index);
         Optional<ChallengeState> existing = Challenges.state(level.getServer(), id);
         if (existing.isPresent()) {
            return existing.get().origin;
         } else {
            Optional<ChallengeDef> def = ChallengeDef.forTower(index);
            if (!def.isEmpty() && !generating) {
               generating = true;

               BlockPos var6;
               try {
                  var6 = build(level, rings, index, id, def.get());
               } finally {
                  generating = false;
               }

               return var6;
            } else {
               return null;
            }
         }
      } else {
         return null;
      }
   }

   private static BlockPos build(ServerLevel level, List<ChunkPos> rings, int index, String id, ChallengeDef def) {
      ChunkPos cp = rings.get(index);
      LevelChunk chunk = level.getChunk(cp.x(), cp.z());
      Structure stronghold = stronghold(level);
      StructureStart start = stronghold == null ? null : chunk.getStartForStructure(stronghold);
      int x = cp.getMiddleBlockX();
      int z = cp.getMiddleBlockZ();
      if (start != null && start.isValid()) {
         for (StructurePiece piece : start.getPieces()) {
            if (piece instanceof PortalRoom) {
               BlockPos c = piece.getBoundingBox().getCenter();
               x = c.getX();
               z = c.getZ();
            }
         }
      }

      Vec3i size = level.getServer().getStructureTemplateManager().get(def.ruined()).map(t -> t.getSize()).orElse(new Vec3i(1, 1, 1));
      level.getChunk(x >> 4, z >> 4);
      int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      BlockPos origin = new BlockPos(x - size.getX() / 2, y - 1, z - size.getZ() / 2);
      if (!Challenges.place(level, def, origin, id)) {
         return null;
      } else {
         Challenges.sealPortalRoom(level, id, portalRoom(level, cp));
         return origin;
      }
   }
}
