package dev.serantis.battle;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Weapon enchantments count in creature battles (your weapon attack, not staff spells):
 *  - Sharpness: +0.5 per level + 0.5 attack damage (as in Minecraft), added to the weapon's power.
 *  - Smite: +10% damage per level against Darkness and Void creatures.
 *  - Bane of Arthropods: +10% per level against Nature creatures.
 *  - Impaling: +10% per level against Water creatures.
 *  - Fire Aspect (+8% per level) and Flame (+15%): against anything but Fire creatures.
 *  - Power: +12% per level (bows and crossbows).
 *  - Breach: +8% per level (it cuts through armour; here, defence). Density: +5% per level.
 *  - Looting: +5% per level that each creature you beat drops its loot twice.
 */
public final class WeaponEnchants {
    private WeaponEnchants() {}

    public static int level(Player p, ItemStack stack, ResourceKey<Enchantment> key) {
        if (p == null || stack == null || stack.isEmpty()) return 0;
        return p.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(key)
                .map(h -> EnchantmentHelper.getItemEnchantmentLevel(h, stack)).orElse(0);
    }

    /** Extra attack damage from Sharpness. */
    public static double sharpness(Player p, ItemStack weapon) {
        int l = level(p, weapon, Enchantments.SHARPNESS);
        return l > 0 ? 0.5 * l + 0.5 : 0;
    }

    /** Damage multiplier for the player's weapon attack on a creature of this influence. */
    public static double multiplier(Player p, ItemStack weapon, String targetInfluence) {
        if (weapon == null || weapon.isEmpty()) return 1;
        String t = targetInfluence == null ? "none" : targetInfluence;
        double m = 1;
        if ("darkness".equals(t) || "void".equals(t)) m += 0.10 * level(p, weapon, Enchantments.SMITE);
        if ("nature".equals(t)) m += 0.10 * level(p, weapon, Enchantments.BANE_OF_ARTHROPODS);
        if ("water".equals(t)) m += 0.10 * level(p, weapon, Enchantments.IMPALING);
        if (!"fire".equals(t)) {
            m += 0.08 * level(p, weapon, Enchantments.FIRE_ASPECT);
            m += 0.15 * Math.min(1, level(p, weapon, Enchantments.FLAME));
        }
        m += 0.12 * level(p, weapon, Enchantments.POWER);
        m += 0.08 * level(p, weapon, Enchantments.BREACH);
        m += 0.05 * level(p, weapon, Enchantments.DENSITY);
        return m;
    }

    /** Extra chance (0-1) that a beaten creature drops its loot twice, from Looting on the weapon in hand. */
    public static float looting(Player p) {
        return p == null ? 0 : 0.05f * level(p, p.getMainHandItem(), Enchantments.LOOTING);
    }
}
