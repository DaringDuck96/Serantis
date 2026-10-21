package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A full set of armour for every influence: as strong as diamond, and each piece worn takes 6.25% off the damage
 * from attacks of its influence in battle (a full set: 25%). Crafted like diamond armour, but of Orichalcum Ingots (from village bounties), with two of the
 * ingots swapped for something of the influence. Named like the influence's staff (Emberwrath, Rimespire...).
 */
public final class InfluenceArmor {
    public static final String[] PIECES = {"helmet", "chestplate", "leggings", "boots"};
    private static final ArmorType[] TYPES = {ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS};
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final double PER_PIECE = 0.0625;

    /** Influence -> the name its armour (and staff) goes by. */
    public static final Map<String, String> NAMES = new java.util.LinkedHashMap<>();
    static {
        NAMES.put("fire", "Emberwrath"); NAMES.put("frost", "Rimespire"); NAMES.put("storm", "Thundercall");
        NAMES.put("earth", "Stoneheart"); NAMES.put("water", "Tidecaller"); NAMES.put("nature", "Thornbloom"); NAMES.put("void", "Riftwalker");
        NAMES.put("fae", "Moonpetal"); NAMES.put("darkness", "Gloomreaver"); NAMES.put("arcane", "Runeweaver"); NAMES.put("radiant", "Dawnbringer");
    }

    private static final Map<Item, String> INFLUENCE_OF = new HashMap<>();

    private InfluenceArmor() {}

    /** Orichalcum Ingot: never found in the world, only paid out by village bounties. Influence armour is made of it. */
    public static Item ORICHALCUM;

    public static void register() {
        ResourceKey<Item> ore = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:orichalcum_ingot"));
        ORICHALCUM = Registry.register(BuiltInRegistries.ITEM, ore, new Item(new Item.Properties().setId(ore)
                .component(DataComponents.LORE, new ItemLore(List.of(Component.literal("Only from village bounty boards (right-click a bell).")
                        .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)),
                        Component.literal("Makes influence armour and the Helm of Linx.")
                        .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))))));
        for (String inf : NAMES.keySet())
            for (int i = 0; i < PIECES.length; i++) {
                ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:" + inf + "_" + PIECES[i]));
                String what = Masters.influenceName(inf);
                Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties()
                        .humanoidArmor(ArmorMaterials.DIAMOND, TYPES[i]).setId(key)
                        .component(DataComponents.LORE, new ItemLore(List.of(Component.literal("Resists " + what + " attacks in battle")
                                .withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GRAY)))))));
                INFLUENCE_OF.put(item, inf);
            }
    }

    /** How much less damage this player takes from an attack of this influence (0-0.25). */
    public static double resistance(Player p, String influence) {
        if (p == null) return 0;
        String want = influence == null ? "none" : influence;
        int n = 0;
        for (EquipmentSlot s : SLOTS) {
            ItemStack worn = p.getItemBySlot(s);
            if (!worn.isEmpty() && want.equals(INFLUENCE_OF.get(worn.getItem()))) n++;
        }
        return n * PER_PIECE;
    }
}
