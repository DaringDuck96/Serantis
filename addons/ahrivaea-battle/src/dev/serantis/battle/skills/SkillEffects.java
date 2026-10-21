package dev.serantis.battle.skills;

import dev.ahrivaea.battle.Battle;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.battle.DamageCalc;
import dev.ahrivaea.battle.Unit;
import dev.ahrivaea.creature.Drops;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** What skill tree nodes do outside of plain stats. */
public final class SkillEffects {
    /** The player whose creature loot is being rolled (for Treasure Sense). */
    public static final ThreadLocal<Player> LOOTER = new ThreadLocal<>();
    /** The player breaking a block right now (for Prospector and Tireless). */
    public static final ThreadLocal<Player> BREAKER = new ThreadLocal<>();

    private SkillEffects() {}

    /** Heavy Hitter, Overpower, Spellpower (the player's attacks) and Steady (hits on the player). */
    public static DamageCalc.Result battle(Battle b, Unit attacker, Unit target, BattleMove move, boolean crit, DamageCalc.Result r) {
        if (r == null || r.damage() <= 0) return r;
        ServerPlayer p = b.player();
        if (p == null) return r;
        double mult = 1;
        if (attacker.isPlayer) {
            if (move.maxRange() <= 1 && Skills.has(p, "might_4")) mult *= 1.05;
            if (move.move().category() == DamageCalc.Category.MAGIC && Skills.has(p, "arcana_3")) mult *= 1.05;
            if (crit && Skills.has(p, "might_6")) mult *= 2.25 / 2.0;
        }
        if (target.isPlayer && move.maxRange() > 1 && Skills.has(p, "guard_5")) mult *= 0.95;
        if (mult == 1) return r;
        int damage = Math.max(1, (int) Math.round(r.damage() * mult));
        return new DamageCalc.Result(damage, r.multiplier(), r.hitChance(), r.critChance(), r.expected() * mult);
    }

    /** Spoils (5% chance a beaten creature drops twice) and Treasure Sense. */
    public static List<ItemStack> creatureLoot(ServerPlayer p, Species species, Influence influence, int level, RandomSource random, RegistryAccess access) {
        LOOTER.set(p);
        try {
            List<ItemStack> out = new ArrayList<>(Drops.roll(species, influence, level, random, access));
            if (p != null && Skills.has(p, "fortune_4") && random.nextFloat() < 0.05f) out.addAll(Drops.roll(species, influence, level, random, access));
            ItemStack lore = dev.serantis.battle.LoreDrops.roll(random);          // 2.5%: a lore book
            if (lore != null) {
                out.add(lore);
                if (p != null) ((dev.serantis.battle.Achievements.Holder) p).serantis$addLoreBooks(1);
            }
            return out;
        } finally {
            LOOTER.remove();
        }
    }

    /** Treasure Sense: drops under 25% are twice as likely. */
    public static float dropChance(float chance) {
        Player p = LOOTER.get();
        return p != null && chance < 0.25f && Skills.has(p, "fortune_6") ? Math.min(0.5f, chance * 2) : chance;
    }
}
