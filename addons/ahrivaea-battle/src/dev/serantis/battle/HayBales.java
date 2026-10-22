package dev.serantis.battle;

import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Farm animals for a base. A hay bale anywhere in a base's area brings cows, sheep, goats and chickens to it, apart
 * from the ordinary wild spawns: every minute there's a 50% chance one wanders up beside the bale (Level 1-5, wild,
 * ready to battle for meat or recruit), until CAP of them are there. More hay bales don't bring more. They stay put
 * (they don't despawn when you walk away), and each counts against the cap until it's gone.
 */
public final class HayBales {
    public static final String TAG = "serantis_farm_animal";
    public static final int CAP = 6, EVERY = 20 * 60;
    private static final String[] ANIMALS = {"cow", "sheep", "goat", "chicken"};
    /** Base core (dimension + position) -> the hay bale found there last time. */
    private static final Map<String, BlockPos> HAY = new ConcurrentHashMap<>();

    private HayBales() {}

    /** Every server tick. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 37) return;
        for (ServerLevel level : server.getAllLevels())
            for (BlockPos core : Bases.cores(level)) {
                BaseCoreBlockEntity be = Bases.coreAt(level, core);
                if (be == null) continue;
                String key = level.dimension().identifier() + "@" + core.asLong();
                BlockPos hay = HAY.get(key);
                if (hay == null || !level.isLoaded(hay) || !level.getBlockState(hay).is(Blocks.HAY_BLOCK)) {
                    hay = find(level, be);
                    if (hay == null) { HAY.remove(key); continue; }
                    HAY.put(key, hay);
                }
                if (level.getRandom().nextFloat() >= 0.5f) continue;
                AABB box = new AABB(be.minX(), level.getMinY(), be.minZ(), be.maxX() + 1, level.getMaxY(), be.maxZ() + 1);
                int there = level.getEntitiesOfClass(CreatureEntity.class, box, c -> c.entityTags().contains(TAG) && !c.isTame()).size();
                if (there >= CAP) continue;
                spawn(level, be, hay);
            }
    }

    /** A hay bale in the base's area, or null. Only chunk sections whose palette has hay are looked through. */
    private static BlockPos find(ServerLevel level, BaseCoreBlockEntity be) {
        for (int cx = be.minX() >> 4; cx <= be.maxX() >> 4; cx++)
            for (int cz = be.minZ() >> 4; cz <= be.maxZ() >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection s = sections[i];
                    if (s == null || s.hasOnlyAir() || !s.maybeHas(st -> st.is(Blocks.HAY_BLOCK))) continue;
                    int baseY = level.getSectionYFromSectionIndex(i) << 4;
                    for (int y = 0; y < 16; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                if (!s.getBlockState(x, y, z).is(Blocks.HAY_BLOCK)) continue;
                                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                                if (wx < be.minX() || wx > be.maxX() || wz < be.minZ() || wz > be.maxZ()) continue;
                                return new BlockPos(wx, baseY + y, wz);
                            }
                }
            }
        return null;
    }

    private static void spawn(ServerLevel level, BaseCoreBlockEntity be, BlockPos hay) {
        Species sp = Species.get(ANIMALS[level.getRandom().nextInt(ANIMALS.length)]).orElse(null);
        if (sp == null) return;
        for (int tries = 0; tries < 12; tries++) {
            int x = hay.getX() + level.getRandom().nextInt(13) - 6, z = hay.getZ() + level.getRandom().nextInt(13) - 6;
            if (x < be.minX() || x > be.maxX() || z < be.minZ() || z > be.maxZ() || !level.isLoaded(new BlockPos(x, hay.getY(), z))) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos at = new BlockPos(x, y, z);
            if (Math.abs(y - hay.getY()) > 8) continue;                           // stay near the bale's level (not on a roof far above)
            if (!level.getBlockState(at.below()).isSolid() || !level.getFluidState(at.below()).isEmpty() || !level.getBlockState(at).isAir()) continue;
            Influence inf = sp.formOrDefault(Influence.NONE);
            CreatureEntity e = Ahrivaea.spawn(level, sp, inf, 1 + level.getRandom().nextInt(5), -1f, x + 0.5, y, z + 0.5);
            if (e == null) return;
            e.addTag(TAG);
            e.setPersistenceRequired();
            return;
        }
    }
}
