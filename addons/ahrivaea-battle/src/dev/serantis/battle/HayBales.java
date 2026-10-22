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
 *
 * Right-click a hay bale with an empty hand to switch it off or on again (each bale on its own; a new one starts on).
 * A base gets animals while at least one of its bales is on. Switched-off bales are kept in serantis_hay_off.txt in
 * the world folder.
 */
public final class HayBales {
    public static final String TAG = "serantis_farm_animal";
    public static final int CAP = 6, EVERY = 20 * 60;
    private static final String[] ANIMALS = {"cow", "sheep", "goat", "chicken"};
    /** Base core (dimension + position) -> the hay bale found there last time. */
    private static final Map<String, BlockPos> HAY = new ConcurrentHashMap<>();

    /** Switched-off bales, "dimension@pos", and the server they were loaded for. */
    private static final java.util.Set<String> OFF = ConcurrentHashMap.newKeySet();
    private static MinecraftServer offFor;

    private HayBales() {}

    private static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serantis_hay_off.txt");
    }

    private static synchronized void load(MinecraftServer server) {
        if (offFor == server) return;
        offFor = server;
        OFF.clear();
        try {
            java.nio.file.Path f = file(server);
            if (java.nio.file.Files.exists(f))
                for (String line : java.nio.file.Files.readAllLines(f)) if (!line.isBlank()) OFF.add(line.trim());
        } catch (Exception e) {
            // nothing saved: every bale is on
        }
    }

    private static synchronized void save(MinecraftServer server) {
        try {
            java.nio.file.Files.write(file(server), new java.util.ArrayList<>(OFF));
        } catch (Exception e) {
            // couldn't save: it still holds until a restart
        }
    }

    private static String id(ServerLevel level, BlockPos pos) {
        return level.dimension().identifier() + "@" + pos.asLong();
    }

    public static boolean isOn(ServerLevel level, BlockPos pos) {
        load(level.getServer());
        return !OFF.contains(id(level, pos));
    }

    /** Right-clicked a hay bale with an empty hand. False if it isn't in a base (nothing to switch). */
    public static boolean toggle(ServerLevel level, BlockPos pos, net.minecraft.server.level.ServerPlayer p) {
        if (Bases.baseAt(level, pos) == null) return false;
        load(level.getServer());
        String id = id(level, pos);
        boolean on = OFF.remove(id);
        if (!on) OFF.add(id);
        save(level.getServer());
        HAY.values().remove(pos);                                       // look again next time
        p.sendOverlayMessage(net.minecraft.network.chat.Component.literal(on ? "Hay bale on: farm animals will come to your base."
                : "Hay bale off: it won't bring farm animals.").withStyle(on ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.GRAY));
        level.playSound(null, pos, on ? net.minecraft.sounds.SoundEvents.GRASS_PLACE : net.minecraft.sounds.SoundEvents.GRASS_BREAK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, on ? 1.2f : 0.8f);
        return true;
    }

    /** Every server tick. */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % EVERY != 37) return;
        for (ServerLevel level : server.getAllLevels())
            for (BlockPos core : Bases.cores(level)) {
                BaseCoreBlockEntity be = Bases.coreAt(level, core);
                if (be == null) continue;
                String key = level.dimension().identifier() + "@" + core.asLong();
                BlockPos hay = HAY.get(key);
                if (hay == null || !level.isLoaded(hay) || !level.getBlockState(hay).is(Blocks.HAY_BLOCK) || !isOn(level, hay)) {
                    hay = find(level, be);
                    if (hay == null) { HAY.remove(key); continue; }
                    HAY.put(key, hay);
                }
                if (level.getRandom().nextFloat() >= 0.5f) continue;
                AABB box = new AABB(be.minX(), level.getMinY(), be.minZ(), be.maxX() + 1, level.getMaxY(), be.maxZ() + 1);
                int there = level.getEntitiesOfClass(CreatureEntity.class, box, c -> c.entityTags().contains(TAG) && !c.isTame()
                        && BaseShape.inside(be, c.getBlockX(), c.getBlockZ())).size();
                if (there >= CAP) continue;
                spawn(level, be, hay);
            }
    }

    /** A switched-on hay bale in the base's area, or null. Only chunk sections whose palette has hay are looked through. */
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
                                if (!BaseShape.inside(be, wx, wz)) continue;
                                BlockPos found = new BlockPos(wx, baseY + y, wz);
                                if (isOn(level, found)) return found;
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
            if (!BaseShape.inside(be, x, z) || !level.isLoaded(new BlockPos(x, hay.getY(), z))) continue;
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
