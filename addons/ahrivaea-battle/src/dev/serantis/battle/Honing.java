package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.villager.VillagerJob;
import dev.serantis.battle.skills.Skills;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weapon improving at a Smith (in a village or working at your base): right-click one while holding a
 * weapon. Each improvement adds +1 attack damage (in the world and in battle), up to +5 per weapon.
 * The XP cost is the weapon's material price times the improvement being bought: an iron sword costs
 * 25, 50, 75, 100 and 125 XP for +1 to +5 (375 in all). Presence discounts apply.
 */
public final class Honing {
    public static final String ROLE = "smith";
    public static final int MAX = 5;
    private static final String KEY = "serantis_honed";
    private static final Identifier MOD_ID = Identifier.parse("serantis:honed");

    private static final Map<UUID, Integer> TALKING = new ConcurrentHashMap<>();

    private Honing() {}

    public static boolean isSmith(Entity e) {
        if (!(e instanceof CreatureEntity c)) return false;
        VillagerJob j = Villages.jobOf(c);
        return j != null && ROLE.equals(j.profession());
    }

    /** Weapons a Smith can improve: swords, axes, spears, maces, tridents, bows, crossbows, Wooden Staffs. */
    public static boolean improvable(ItemStack s) {
        if (s == null || s.isEmpty() || !Weapons.isWeapon(s)) return false;
        return !Staffs.isStaff(s) || s.is(Staffs.BLANK);
    }

    public static int level(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? 0 : d.copyTag().getIntOr(KEY, 0);
    }

    /** XP per step, by material. */
    public static int base(ItemStack s) {
        if (s.is(Items.MACE) || s.is(Items.TRIDENT)) return 50;
        if (s.is(Items.BOW)) return 20;
        if (s.is(Items.CROSSBOW)) return 25;
        String id = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
        if (id.startsWith("netherite_")) return 60;
        if (id.startsWith("diamond_")) return 40;
        if (id.startsWith("iron_")) return 25;
        if (id.startsWith("copper_")) return 20;
        if (id.startsWith("stone_")) return 15;
        return 10;                                       // wooden, golden, staffs and anything else
    }

    public static int cost(ServerPlayer p, ItemStack s) {
        return Skills.price(p, base(s) * (level(s) + 1));
    }

    private static boolean near(ServerPlayer p, int entityId) {
        Entity e = p.level().getEntity(entityId);
        return e != null && isSmith(e) && e.isAlive() && p.distanceToSqr(e) <= 10 * 10;
    }

    public static void open(ServerPlayer p, int entityId) {
        if (!near(p, entityId)) return;
        TALKING.put(p.getUUID(), entityId);
        PartySync.sync(p);
    }

    public static void close(ServerPlayer p) {
        if (TALKING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    public static void improve(ServerPlayer p) {
        Integer smith = TALKING.get(p.getUUID());
        if (smith == null || !near(p, smith)) return;
        ItemStack s = p.getMainHandItem();
        if (!improvable(s)) { tell(p, "Hold the weapon you want improved."); return; }
        int lvl = level(s);
        if (lvl >= MAX) { tell(p, "That weapon is already improved as far as it goes (+" + MAX + ")."); return; }
        int cost = cost(p, s);
        boolean free = p.getAbilities().instabuild;
        if (!free && XpBank.points(p) < cost) { tell(p, "That costs " + cost + " XP."); return; }
        if (!free) XpBank.take(p, cost);
        set(s, lvl + 1);
        p.level().playSound(null, p.blockPosition(), SoundEvents.SMITHING_TABLE_USE, SoundSource.PLAYERS, 1f, 1f);
        tell(p, s.getHoverName().getString() + " improved to +" + (lvl + 1) + "! (-" + cost + " XP)");
        PartySync.sync(p);
    }

    private static void set(ItemStack s, int lvl) {
        CompoundTag tag = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY, lvl);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        ItemAttributeModifiers mods = s.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        s.set(DataComponents.ATTRIBUTE_MODIFIERS, mods.withModifierAdded(Attributes.ATTACK_DAMAGE,
                new AttributeModifier(MOD_ID, lvl, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND));
        List<Component> lore = new ArrayList<>();
        ItemLore had = s.get(DataComponents.LORE);
        if (had != null) for (Component c : had.lines()) if (!c.getString().startsWith("Improved +")) lore.add(c);
        lore.add(0, Component.literal("Improved +" + lvl).withStyle(st -> st.withItalic(false).withColor(ChatFormatting.GOLD)));
        s.set(DataComponents.LORE, new ItemLore(lore));
    }

    /** "serantis_smith" in the party state while a player is at a Smith. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Integer smith = TALKING.get(p.getUUID());
        if (smith == null) return;
        if (!near(p, smith)) { TALKING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        o.addProperty("entity", smith);
        o.addProperty("xp", XpBank.points(p));
        o.addProperty("max", MAX);
        ItemStack s = p.getMainHandItem();
        if (improvable(s)) {
            o.addProperty("name", s.getHoverName().getString());
            o.addProperty("level", level(s));
            o.addProperty("cost", level(s) >= MAX ? 0 : cost(p, s));
        }
        state.add("serantis_smith", o);
    }

    private static void tell(ServerPlayer p, String msg) {
        p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW));
    }
}
