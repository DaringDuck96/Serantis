package dev.serantis.battle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.villager.VillagerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Villages always have villagers.
 *
 * The first time a player walks into a village, it's given 4-7 villagers, each with a different profession,
 * and 3-5 Town Guards, spread around its streets and houses (they never despawn). Every three days (72,000
 * ticks of play), the village fills any empty spots (villagers recruited or beaten in battle) with new
 * villagers of random professions it doesn't have yet, and replaces any missing guards. The villagers
 * Minecraft itself would put in a village aren't used. Each village's makeup is kept in serantis_villages.json
 * in the world folder; each villager remembers its village.
 */
public final class Towns {
    public static final long REFILL_TICKS = 3 * 24000L;

    /** Added to creatures: the village a villager belongs to ("dimension|x|z" of the village's start chunk), or null. */
    public interface Home {
        String serantis$home();
        void serantis$setHome(String key);
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static MinecraftServer loadedFor;
    private static final Map<String, JsonObject> VILLAGES = new LinkedHashMap<>();

    private Towns() {}

    // ------------------------------------------------------------------ the saved file

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("serantis_villages.json");
    }

    private static void load(MinecraftServer server) {
        if (loadedFor == server) return;
        loadedFor = server;
        VILLAGES.clear();
        try {
            Path f = file(server);
            if (Files.exists(f)) {
                JsonObject all = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : all.entrySet()) VILLAGES.put(e.getKey(), e.getValue().getAsJsonObject());
            }
        } catch (Exception e) {
            Ahrivaea.LOGGER.warn("Serantis: couldn't read the village file: {}", e.toString());
        }
    }

    private static void save(MinecraftServer server) {
        JsonObject all = new JsonObject();
        VILLAGES.forEach(all::add);
        try {
            Files.writeString(file(server), GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Ahrivaea.LOGGER.warn("Serantis: couldn't save the village file: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------ finding villages

    private static StructureStart villageAt(ServerLevel level, BlockPos pos) {
        StructureStart s = level.structureManager().getStructureWithPieceAt(pos, StructureTags.VILLAGE);
        return s != null && s.isValid() ? s : null;
    }

    private static String key(ServerLevel level, StructureStart s) {
        ChunkPos c = s.getChunkPos();
        return level.dimension().identifier() + "|" + c.x() + "|" + c.z();
    }

    /** Is this spot in a village? (Minecraft's own village villagers aren't used there.) */
    public static boolean inVillage(ServerLevel level, BlockPos pos) {
        try { return villageAt(level, pos) != null; } catch (RuntimeException e) { return false; }
    }

    private static boolean entitiesLoaded(ServerLevel level, BoundingBox box) {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                if (!level.areEntitiesLoaded(ChunkPos.pack(cx, cz))) return false;
        return true;
    }

    // ------------------------------------------------------------------ keeping villages full

    /** Every few seconds for each player: the village they're in gets its villagers, or its refill when due. */
    public static void check(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level) || p.isSpectator()) return;
        StructureStart s;
        try { s = villageAt(level, p.blockPosition()); } catch (RuntimeException e) { return; }
        if (s == null) return;
        BoundingBox box = s.getBoundingBox();
        if (!entitiesLoaded(level, box)) return;                 // count only once the whole village is loaded
        MinecraftServer server = level.getServer();
        load(server);
        String key = key(level, s);
        long now = level.getGameTime();
        JsonObject v = VILLAGES.get(key);
        RandomSource random = level.getRandom();

        // who lives here now
        AABB area = new AABB(box.minX() - 24, level.getMinY(), box.minZ() - 24, box.maxX() + 25, level.getMaxY(), box.maxZ() + 25);
        List<CreatureEntity> living = level.getEntitiesOfClass(CreatureEntity.class, area,
                e -> e.isAlive() && !e.isTame() && key.equals(((Home) e).serantis$home()));
        Set<String> roles = new HashSet<>();
        int guards = 0;
        for (CreatureEntity e : living) {
            VillagerJob j = Villages.jobOf(e);
            if (j == null) continue;
            if (Villages.GUARD.equals(j.profession())) guards++;
            else roles.add(j.profession());
        }

        if (v == null) {                                         // a village seen for the first time
            v = new JsonObject();
            v.addProperty("villagers", 4 + random.nextInt(4));
            v.addProperty("guards", 3 + random.nextInt(3));
            v.addProperty("filled", now);
        } else if (now - v.get("filled").getAsLong() >= REFILL_TICKS) {
            v.addProperty("filled", now);                        // three days on: fill the empty spots
        } else return;

        int wantVillagers = v.get("villagers").getAsInt(), wantGuards = v.get("guards").getAsInt();
        List<String> add = new ArrayList<>();
        while (roles.size() + add.size() < wantVillagers) {
            String role = pickRole(random, roles, add);
            if (role == null) break;
            add.add(role);
        }
        for (String role : add) spawn(level, s, key, role, random);
        for (int i = guards; i < wantGuards; i++) spawn(level, s, key, null, random);
        JsonArray now_ = new JsonArray();
        roles.forEach(now_::add);
        add.forEach(now_::add);
        v.add("professions", now_);
        VILLAGES.put(key, v);
        save(server);
    }

    /** A profession the village doesn't have yet (weighted the usual way). */
    private static String pickRole(RandomSource random, Set<String> have, List<String> adding) {
        for (int i = 0; i < 200; i++) {
            String r = Villages.rollRole(random);
            if (!have.contains(r) && !adding.contains(r)) return r;
        }
        return null;
    }

    /** A villager (or, with no role, a Town Guard) somewhere along the village's streets and houses. */
    private static void spawn(ServerLevel level, StructureStart s, String key, String role, RandomSource random) {
        Species villager = Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        if (villager == null) return;
        BlockPos at = spot(level, s, random);
        CreatureEntity e = Ahrivaea.spawn(level, villager, Influence.NONE, Villages.SPAWN_LEVEL, -1f, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        if (e == null) return;
        if (role == null) Villages.makeGuard(e);
        else Villages.setJob(e, new VillagerJob(role, Market.MERCHANT.equals(role) ? 1 : 0, 0));
        e.setPersistenceRequired();
        ((Home) e).serantis$setHome(key);
    }

    private static BlockPos spot(ServerLevel level, StructureStart s, RandomSource random) {
        List<StructurePiece> pieces = s.getPieces();
        for (int i = 0; i < 30 && !pieces.isEmpty(); i++) {
            BoundingBox b = pieces.get(random.nextInt(pieces.size())).getBoundingBox();
            int x = b.minX() + random.nextInt(Math.max(1, b.maxX() - b.minX() + 1));
            int z = b.minZ() + random.nextInt(Math.max(1, b.maxZ() - b.minZ() + 1));
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos p = new BlockPos(x, y, z);
            if (level.getFluidState(p.below()).isEmpty() && level.getFluidState(p).isEmpty()
                    && level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()) return p;
        }
        BoundingBox b = s.getBoundingBox();
        int x = (b.minX() + b.maxX()) / 2, z = (b.minZ() + b.maxZ()) / 2;
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }
}
