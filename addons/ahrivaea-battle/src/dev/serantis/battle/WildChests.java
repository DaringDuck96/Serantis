package dev.serantis.battle;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.loot.LootTable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wild chests: every morning, chests appear on the surface around each player in the Overworld, filled like
 * the chests of dungeons, mineshafts, shipwrecks, temples and villages; a rare few hold Orichalcum Ingots,
 * diamonds and other treasure. Yesterday's wild chests vanish (spilling anything left in them) and new ones
 * appear somewhere else.
 *
 * Each wild chest has a 60% chance to be a Mimic: open (or try to break) it and it comes to life and attacks,
 * starting a battle. A Mimic was never a real chest, so it holds no loot: all you get is what the Mimic drops.
 */
public final class WildChests {
    public static final double MIMIC_CHANCE = 0.60, RARE_CHANCE = 0.08;
    public static final int PER_PLAYER = 5, MAX_CHESTS = 80, MIN_DIST = 24, MAX_DIST = 96, SPACING = 12;

    private static final String[] TABLES = {
            "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft", "minecraft:chests/shipwreck_supply",
            "minecraft:chests/shipwreck_treasure", "minecraft:chests/desert_pyramid", "minecraft:chests/jungle_temple",
            "minecraft:chests/village/village_weaponsmith", "minecraft:chests/village/village_toolsmith", "minecraft:chests/village/village_armorer",
            "minecraft:chests/ruined_portal", "minecraft:chests/pillager_outpost", "minecraft:chests/igloo_chest",
            "minecraft:chests/underwater_ruin_small", "minecraft:chests/buried_treasure"};

    /** A wild chest: where, whether it's a rare one, and whether its Mimic roll has happened. */
    private static final class Wild {
        String dim;
        BlockPos pos;
        boolean rare, rolled;
    }

    private static final List<Wild> CHESTS = new ArrayList<>();
    private static final List<Wild> STALE = new ArrayList<>();            // yesterday's, to clear once their chunk is loaded
    private static MinecraftServer loadedFor;
    private static boolean wasDark = true, placedToday;
    private static final java.util.Set<java.util.UUID> SERVED = new java.util.HashSet<>();   // players who got today's chests

    private WildChests() {}

    // ------------------------------------------------------------------ saving

    private static Path file(MinecraftServer s) { return s.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("serantis_wild_chests.json"); }

    private static synchronized void ensure(MinecraftServer server) {
        if (loadedFor == server) return;
        loadedFor = server;
        CHESTS.clear();
        STALE.clear();
        SERVED.clear();
        wasDark = true;
        placedToday = false;
        try {
            Path f = file(server);
            if (!Files.exists(f)) return;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var el : root.getAsJsonArray("chests")) CHESTS.add(read(el.getAsJsonObject()));
            if (root.has("stale")) for (var el : root.getAsJsonArray("stale")) STALE.add(read(el.getAsJsonObject()));
            wasDark = !root.has("wasDark") || root.get("wasDark").getAsBoolean();
            placedToday = root.has("placedToday") && root.get("placedToday").getAsBoolean();
            if (root.has("served")) for (var el : root.getAsJsonArray("served")) SERVED.add(java.util.UUID.fromString(el.getAsString()));
        } catch (Exception e) {
            Ahrivaea.LOGGER.warn("Couldn't read serantis_wild_chests.json", e);
        }
    }

    private static Wild read(JsonObject o) {
        Wild w = new Wild();
        w.dim = o.get("dim").getAsString();
        w.pos = BlockPos.of(o.get("pos").getAsLong());
        w.rare = o.has("rare") && o.get("rare").getAsBoolean();
        w.rolled = o.has("rolled") && o.get("rolled").getAsBoolean();
        return w;
    }

    private static JsonObject write(Wild w) {
        JsonObject o = new JsonObject();
        o.addProperty("dim", w.dim);
        o.addProperty("pos", w.pos.asLong());
        o.addProperty("rare", w.rare);
        o.addProperty("rolled", w.rolled);
        return o;
    }

    private static synchronized void save(MinecraftServer server) {
        JsonObject root = new JsonObject();
        JsonArray a = new JsonArray(), s = new JsonArray(), served = new JsonArray();
        for (Wild w : CHESTS) a.add(write(w));
        for (Wild w : STALE) s.add(write(w));
        for (var id : SERVED) served.add(id.toString());
        root.add("chests", a);
        root.add("stale", s);
        root.add("served", served);
        root.addProperty("wasDark", wasDark);
        root.addProperty("placedToday", placedToday);
        try {
            Files.writeString(file(server), new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Ahrivaea.LOGGER.warn("Couldn't save serantis_wild_chests.json", e);
        }
    }

    private static Wild at(Level level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        for (Wild w : CHESTS) if (w.pos.equals(pos) && w.dim.equals(dim)) return w;
        return null;
    }

    // ------------------------------------------------------------------ every few seconds, per player

    public static synchronized void tick(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        ensure(server);
        ServerLevel overworld = server.overworld();
        boolean dark = overworld.isDarkOutside();
        boolean changed = false;
        if (wasDark && !dark) {                                         // morning: yesterday's chests go, new ones come
            for (Wild w : new ArrayList<>(CHESTS)) if (!remove(server, w)) STALE.add(w);
            CHESTS.clear();
            SERVED.clear();
            changed = true;
        }
        if (wasDark != dark) { wasDark = dark; changed = true; }
        for (var it = STALE.iterator(); it.hasNext(); ) if (remove(server, it.next())) { it.remove(); changed = true; }
        if (!dark && p.level() == overworld && !SERVED.contains(p.getUUID()) && CHESTS.size() < MAX_CHESTS) {
            int placed = 0;
            RandomSource r = overworld.getRandom();
            for (int tries = 0; tries < 40 && placed < PER_PLAYER && CHESTS.size() < MAX_CHESTS; tries++)
                if (place(overworld, p, r)) placed++;
            if (placed > 0) SERVED.add(p.getUUID());
            changed |= placed > 0;
        }
        if (changed) save(server);
    }

    /** Takes a wild chest away (spilling what's in it). False if its chunk isn't loaded yet. */
    private static boolean remove(MinecraftServer server, Wild w) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(w.dim)));
        if (level == null) return true;
        if (!level.isLoaded(w.pos)) return false;
        if (level.getBlockEntity(w.pos) instanceof ChestBlockEntity chest) {
            chest.setLootTable(null);                                   // unopened loot just goes with it
            Containers.dropContents(level, w.pos, chest);
            level.setBlock(w.pos, Blocks.AIR.defaultBlockState(), 3);
        }
        return true;
    }

    private static boolean place(ServerLevel level, ServerPlayer p, RandomSource r) {
        double angle = r.nextDouble() * Math.PI * 2, dist = MIN_DIST + r.nextDouble() * (MAX_DIST - MIN_DIST);
        int x = (int) Math.floor(p.getX() + Math.cos(angle) * dist), z = (int) Math.floor(p.getZ() + Math.sin(angle) * dist);
        BlockPos column = new BlockPos(x, 0, z);
        if (!level.isLoaded(column)) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos pos = new BlockPos(x, y, z);
        var below = level.getBlockState(pos.below());
        if (!level.getBlockState(pos).canBeReplaced() || !level.getFluidState(pos).isEmpty() || !level.getFluidState(pos.below()).isEmpty()) return false;
        if (below.getCollisionShape(level, pos.below()).isEmpty() || below.is(net.minecraft.tags.BlockTags.LEAVES)) return false;
        if (dev.ahrivaea.base.Bases.inAnyBaseColumn(level, pos)) return false;      // never in someone's base
        for (Wild w : CHESTS) if (w.pos.distSqr(pos) < SPACING * SPACING) return false;
        Direction facing = Direction.Plane.HORIZONTAL.getRandomDirection(r);
        level.setBlock(pos, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing), 3);
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
        Wild w = new Wild();
        w.dim = level.dimension().identifier().toString();
        w.pos = pos.immutable();
        w.rare = r.nextDouble() < RARE_CHANCE;
        if (w.rare) fillRare(chest, r);
        else chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, Identifier.parse(TABLES[r.nextInt(TABLES.length)])), r.nextLong());
        CHESTS.add(w);
        return true;
    }

    /** A rare chest: Orichalcum, diamonds and other treasure. */
    private static void fillRare(ChestBlockEntity chest, RandomSource r) {
        List<ItemStack> loot = new ArrayList<>();
        loot.add(new ItemStack(InfluenceArmor.ORICHALCUM, 2 + r.nextInt(4)));
        loot.add(new ItemStack(Items.DIAMOND, 1 + r.nextInt(4)));
        loot.add(new ItemStack(Items.EMERALD, 2 + r.nextInt(6)));
        loot.add(new ItemStack(Items.GOLD_INGOT, 3 + r.nextInt(6)));
        if (r.nextFloat() < 0.6f) loot.add(new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(2)));
        if (r.nextFloat() < 0.35f) loot.add(new ItemStack(Items.AMETHYST_SHARD, 4 + r.nextInt(8)));
        if (r.nextFloat() < 0.2f) loot.add(new ItemStack(Items.NETHERITE_SCRAP));
        if (r.nextFloat() < 0.15f) loot.add(new ItemStack(Items.ENDER_PEARL, 1 + r.nextInt(3)));
        if (r.nextFloat() < 0.03f) loot.add(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < chest.getContainerSize(); i++) slots.add(i);
        Collections.shuffle(slots, new java.util.Random(r.nextLong()));
        for (int i = 0; i < loot.size() && i < slots.size(); i++) chest.setItem(slots.get(i), loot.get(i));
    }

    // ------------------------------------------------------------------ opening one

    /** A player opens (or tries to break) a chest. True if it was a Mimic and the battle has begun. */
    public static synchronized boolean touched(ServerPlayer p, BlockPos pos) {
        if (p.isSpectator() || !(p.level() instanceof ServerLevel level)) return false;
        ensure(level.getServer());
        Wild w = at(level, pos);
        if (w == null || w.rolled) return false;
        w.rolled = true;
        save(level.getServer());
        if (level.getRandom().nextDouble() >= MIMIC_CHANCE || Battles.of(p) != null) return false;   // just a chest
        if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) return false;
        Species mimic = Species.get("mimic").orElse(null);
        if (mimic == null) return false;
        chest.setLootTable(null);                                       // a Mimic holds no loot: only what it drops
        chest.clearContent();
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        CHESTS.remove(w);
        save(level.getServer());
        int me = dev.ahrivaea.character.PlayerCharacter.get(p).level();    // the player's level window (see LevelBand); rare chests a bit tougher
        int lo = Math.max(1, LevelBand.lo(me)), hi = Math.max(lo, LevelBand.hi(me));
        int lvl = Math.min(20, lo + level.getRandom().nextInt(hi - lo + 1) + (w.rare ? 2 : 0));
        CreatureEntity e = Ahrivaea.spawn(level, mimic, mimic.formOrDefault(Influence.NONE), lvl, -1f, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        if (e == null) return true;
        level.playSound(null, pos, SoundEvents.CHEST_OPEN, SoundSource.HOSTILE, 1f, 0.5f);
        level.playSound(null, pos, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.8f, 1.4f);
        p.sendSystemMessage(Component.literal("The chest was a Mimic!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        Battles.start(p, e);
        return true;
    }
}
