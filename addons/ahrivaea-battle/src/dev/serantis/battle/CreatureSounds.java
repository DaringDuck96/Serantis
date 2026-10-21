package dev.serantis.battle;

import dev.ahrivaea.creature.CreatureEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Creature sounds, borrowed from Minecraft's own mobs. Each creature has a kind (serantis_battle/creature_sounds.tsv):
 * humanoid people sound like villagers ("person"; "silent" would make none); the rest get an idle sound, a hurt sound and a death sound that suit their body
 * (wolves growl, birds chirp, dragons roar...), pitched by their size: small ones higher, big ones deeper.
 */
public final class CreatureSounds {
    /** A kind of creature: candidate sound ids for idle, hurt and death (the first that exists is used), and a pitch. */
    record Kind(String[] ambient, String[] hurt, String[] death, float pitch) {}

    private static String[] s(String... ids) { return ids; }

    private static final Map<String, Kind> KINDS = new HashMap<>();
    static {
        KINDS.put("person", new Kind(s("entity.villager.ambient"), s("entity.villager.hurt"), s("entity.villager.death"), 1f));
        KINDS.put("canine", new Kind(s("entity.wolf.growl", "entity.wolf.ambient", "entity.wolf_classic.ambient"), s("entity.wolf.hurt", "entity.wolf_classic.hurt"), s("entity.wolf.death", "entity.wolf_classic.death"), 0.95f));
        KINDS.put("bigcat", new Kind(s("entity.polar_bear.warning", "entity.polar_bear.ambient"), s("entity.polar_bear.hurt"), s("entity.polar_bear.death"), 1.1f));
        KINDS.put("cat", new Kind(s("entity.cat.ambient"), s("entity.cat.hurt"), s("entity.cat.death"), 1f));
        KINDS.put("fox", new Kind(s("entity.fox.ambient"), s("entity.fox.hurt"), s("entity.fox.death"), 1f));
        KINDS.put("bear", new Kind(s("entity.polar_bear.ambient"), s("entity.polar_bear.hurt"), s("entity.polar_bear.death"), 0.9f));
        KINDS.put("bird", new Kind(s("entity.parrot.ambient"), s("entity.parrot.hurt"), s("entity.parrot.death"), 1f));
        KINDS.put("raptor", new Kind(s("entity.phantom.ambient"), s("entity.phantom.hurt"), s("entity.phantom.death"), 1f));
        KINDS.put("dragon", new Kind(s("entity.ender_dragon.growl"), s("entity.ender_dragon.hurt"), s("entity.ender_dragon.hurt"), 0.95f));
        KINDS.put("reptile", new Kind(s("entity.cat.hiss"), s("entity.turtle.hurt"), s("entity.turtle.death"), 0.6f));
        KINDS.put("beast", new Kind(s("entity.ravager.ambient"), s("entity.ravager.hurt"), s("entity.ravager.death"), 1f));
        KINDS.put("horse", new Kind(s("entity.horse.ambient"), s("entity.horse.hurt"), s("entity.horse.death"), 1f));
        KINDS.put("cow", new Kind(s("entity.cow.ambient"), s("entity.cow.hurt"), s("entity.cow.death"), 1f));
        KINDS.put("sheep", new Kind(s("entity.sheep.ambient"), s("entity.sheep.hurt"), s("entity.sheep.death"), 1f));
        KINDS.put("pig", new Kind(s("entity.pig.ambient"), s("entity.pig.hurt"), s("entity.pig.death"), 1f));
        KINDS.put("rabbit", new Kind(s("entity.rabbit.ambient"), s("entity.rabbit.hurt"), s("entity.rabbit.death"), 1f));
        KINDS.put("chicken", new Kind(s("entity.chicken.ambient"), s("entity.chicken.hurt"), s("entity.chicken.death"), 1f));
        KINDS.put("spider", new Kind(s("entity.spider.ambient"), s("entity.spider.hurt"), s("entity.spider.death"), 1f));
        KINDS.put("bee", new Kind(s(), s("entity.bee.hurt"), s("entity.bee.death"), 0.8f));
        KINDS.put("bug", new Kind(s("entity.silverfish.ambient"), s("entity.silverfish.hurt"), s("entity.silverfish.death"), 0.8f));
        KINDS.put("slime", new Kind(s("entity.slime.squish"), s("entity.slime.hurt"), s("entity.slime.death"), 1f));
        KINDS.put("frog", new Kind(s("entity.frog.ambient"), s("entity.frog.hurt"), s("entity.frog.death"), 1f));
        KINDS.put("fish", new Kind(s(), s("entity.cod.hurt"), s("entity.cod.death"), 1f));
        KINDS.put("dolphin", new Kind(s("entity.dolphin.ambient"), s("entity.dolphin.hurt"), s("entity.dolphin.death"), 1f));
        KINDS.put("seamonster", new Kind(s("entity.elder_guardian.ambient"), s("entity.elder_guardian.hurt"), s("entity.elder_guardian.death"), 0.85f));
        KINDS.put("squid", new Kind(s("entity.squid.ambient"), s("entity.squid.hurt"), s("entity.squid.death"), 1f));
        KINDS.put("turtle", new Kind(s("entity.turtle.ambient_land"), s("entity.turtle.hurt"), s("entity.turtle.death"), 1f));
        KINDS.put("golem", new Kind(s(), s("entity.iron_golem.hurt"), s("entity.iron_golem.death"), 1f));
        KINDS.put("spirit", new Kind(s("entity.vex.ambient"), s("entity.vex.hurt"), s("entity.vex.death"), 0.8f));
        KINDS.put("fairy", new Kind(s("entity.allay.ambient_without_item"), s("entity.allay.hurt"), s("entity.allay.death"), 1f));
        KINDS.put("fire", new Kind(s("entity.blaze.ambient"), s("entity.blaze.hurt"), s("entity.blaze.death"), 1f));
        KINDS.put("wind", new Kind(s("entity.breeze.idle_air", "entity.breeze.idle_ground"), s("entity.breeze.hurt"), s("entity.breeze.death"), 1f));
        KINDS.put("horror", new Kind(s("entity.warden.ambient"), s("entity.warden.hurt"), s("entity.warden.death"), 1.1f));
        KINDS.put("ender", new Kind(s("entity.enderman.ambient"), s("entity.enderman.hurt"), s("entity.enderman.death"), 1f));
        KINDS.put("plant", new Kind(s("entity.creaking.ambient", "block.wood.break"), s("entity.creaking.sway", "block.wood.break"), s("entity.creaking.death", "block.wood.break"), 0.9f));
    }

    private static Map<String, String> bySpecies;
    private static final Map<String, SoundEvent> RESOLVED = new HashMap<>();

    private CreatureSounds() {}

    private static synchronized Map<String, String> table() {
        if (bySpecies != null) return bySpecies;
        Map<String, String> m = new HashMap<>();
        try (InputStream in = CreatureSounds.class.getResourceAsStream("/serantis_battle/creature_sounds.tsv")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    if (line.isBlank() || line.startsWith("#")) continue;
                    String[] f = line.split("\t");
                    if (f.length >= 2) m.put(f[0].trim(), f[1].trim());
                }
            }
        } catch (Exception ignored) { }
        return bySpecies = m;
    }

    private static Kind kindOf(CreatureEntity e) {
        String k = table().get(e.getSpeciesId());
        return k == null ? null : KINDS.get(k);
    }

    /** True for creatures marked "silent" (no sounds at all). */
    public static boolean silent(CreatureEntity e) {
        return "silent".equals(table().get(e.getSpeciesId()));
    }

    private static SoundEvent first(String[] ids) {
        for (String id : ids) {
            SoundEvent s = RESOLVED.computeIfAbsent(id, k -> {
                try { return BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse("minecraft:" + k)); } catch (RuntimeException ex) { return null; }
            });
            if (s != null) return s;
        }
        return null;
    }

    public static SoundEvent ambient(CreatureEntity e) { Kind k = kindOf(e); return k == null ? null : first(k.ambient()); }
    public static SoundEvent hurt(CreatureEntity e) { Kind k = kindOf(e); return k == null ? null : first(k.hurt()); }
    public static SoundEvent death(CreatureEntity e) { Kind k = kindOf(e); return k == null ? null : first(k.death()); }

    /** The kind's pitch, higher for small creatures and deeper for big ones. */
    public static float pitch(CreatureEntity e) {
        Kind k = kindOf(e);
        float base = k == null ? 1f : k.pitch();
        float size = e.getSpecies().map(sp -> sp.size()).orElse(1f);
        float bySize = size <= 0.8f ? 1.25f : size >= 1.5f ? 0.75f : 1f;
        return base * bySize;
    }
}
