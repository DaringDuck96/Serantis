package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A base's look: an Overworld base can take the grass, leaf and water colours of any Overworld or Nether biome, across its
 * whole area. Purely how it looks (client/BaseLooksClient tints it on everyone's screen); the real biome, and with it
 * spawns, weather and everything else, stays as it was. Chosen from the base screen ("Look") by the owner or their
 * group; saved in serantis_base_looks.txt in the world folder.
 */
public final class BaseLooks {
    /** The Overworld biomes a base can look like (vanilla ids). */
    public static final List<String> BIOMES = List.of(
            "plains", "sunflower_plains", "meadow", "cherry_grove", "forest", "flower_forest", "birch_forest", "old_growth_birch_forest",
            "dark_forest", "pale_garden", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga", "snowy_taiga", "snowy_plains",
            "ice_spikes", "grove", "snowy_slopes", "frozen_peaks", "jagged_peaks", "stony_peaks", "windswept_hills",
            "windswept_gravelly_hills", "windswept_forest", "savanna", "savanna_plateau", "windswept_savanna", "desert", "badlands",
            "wooded_badlands", "eroded_badlands", "jungle", "sparse_jungle", "bamboo_jungle", "swamp", "mangrove_swamp",
            "mushroom_fields", "river", "frozen_river", "beach", "snowy_beach", "stony_shore", "warm_ocean", "lukewarm_ocean",
            "ocean", "cold_ocean", "frozen_ocean", "lush_caves", "dripstone_caves", "deep_dark",
            // Nether looks: their own colours and floating particles (see client/BaseLooksClient), since the Nether's
            // real grass and water colours are just a desert's
            "nether_wastes", "crimson_forest", "warped_forest", "soul_sand_valley", "basalt_deltas");

    /** Base core position (Overworld) -> biome id. */
    private static final Map<Long, String> LOOKS = new ConcurrentHashMap<>();
    private static MinecraftServer loadedFor;
    /** Each base's area the last time it was loaded (so a far-off base still shows up). */
    private static final Map<Long, int[]> AREA = new ConcurrentHashMap<>();
    /** What each player was last sent, to send again when it changes. */
    private static final Map<java.util.UUID, String> SENT = new ConcurrentHashMap<>();

    private BaseLooks() {}

    private static java.nio.file.Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serantis_base_looks.txt");
    }

    private static synchronized void load(MinecraftServer server) {
        if (loadedFor == server) return;
        loadedFor = server;
        LOOKS.clear();
        try {
            java.nio.file.Path f = file(server);
            if (java.nio.file.Files.exists(f))
                for (String line : java.nio.file.Files.readAllLines(f)) {
                    String[] p = line.trim().split("=", 2);
                    if (p.length == 2 && BIOMES.contains(p[1])) LOOKS.put(Long.parseLong(p[0]), p[1]);
                }
        } catch (Exception e) {
            // nothing saved: every base looks like its own biome
        }
    }

    private static synchronized void save(MinecraftServer server) {
        List<String> lines = new ArrayList<>();
        for (var e : LOOKS.entrySet()) lines.add(e.getKey() + "=" + e.getValue());
        try {
            java.nio.file.Files.write(file(server), lines);
        } catch (Exception e) {
            // couldn't save: it holds until a restart
        }
    }

    public static String of(BaseCoreBlockEntity be) {
        if (!(be.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) return null;
        load(level.getServer());
        return LOOKS.get(be.getBlockPos().asLong());
    }

    /** From the base screen: give the base this biome's look ("" for its own). */
    public static void set(ServerPlayer p, BlockPos core, String biome) {
        ServerLevel level = p.level().getServer().overworld();
        if (p.level() != level) { p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Only Overworld bases can change their look.")); return; }
        BaseCoreBlockEntity be = Bases.coreAt(level, core);
        if (be == null || p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(core)) > 64 * 64) return;
        if (!dev.serantis.battle.coop.Groups.canUseBase(p, be)) { p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("Only the base's owner (or their group) can change its look.")); return; }
        load(level.getServer());
        if (biome == null || biome.isEmpty()) LOOKS.remove(core.asLong());
        else if (BIOMES.contains(biome)) LOOKS.put(core.asLong(), biome);
        else return;
        save(level.getServer());
        for (ServerPlayer q : level.getServer().getPlayerList().getPlayers()) dev.ahrivaea.party.PartySync.sync(q);   // everyone sees it
        Bases.send(p, be);
    }

    /** "serantis_base_looks" in the party state: every Overworld base with a look, as its area and biome. */
    public static void describe(ServerPlayer p, JsonObject state) {
        ServerLevel level = p.level().getServer().overworld();
        load(level.getServer());
        if (LOOKS.isEmpty()) return;
        JsonArray out = new JsonArray();
        for (var e : LOOKS.entrySet()) {
            BlockPos core = BlockPos.of(e.getKey());
            int[] a;
            if (level.isLoaded(core)) {
                BaseCoreBlockEntity be = Bases.coreAt(level, core);
                if (be == null) { LOOKS.remove(e.getKey()); AREA.remove(e.getKey()); continue; }   // the core is gone
                a = new int[]{core.getX(), core.getZ(), be.radius()};
                AREA.put(e.getKey(), a);
            } else if ((a = AREA.get(e.getKey())) == null) continue;     // never seen since the server started: shows once it loads
            JsonObject o = new JsonObject();
            o.addProperty("cx", a[0]);
            o.addProperty("cz", a[1]);
            o.addProperty("r", a[2]);
            o.addProperty("biome", e.getValue());
            out.add(o);
        }
        if (!out.isEmpty()) state.add("serantis_base_looks", out);
        SENT.put(p.getUUID(), out.toString());
    }

    /** Every few seconds, per player: a base's look (or area) changed since they were told, so tell them again. */
    public static void check(ServerPlayer p) {
        JsonObject probe = new JsonObject();
        String before = SENT.get(p.getUUID());
        describe(p, probe);
        String now = probe.has("serantis_base_looks") ? probe.get("serantis_base_looks").toString() : "[]";
        if (before == null ? !"[]".equals(now) : !before.equals(now)) dev.ahrivaea.party.PartySync.sync(p);
    }
}
