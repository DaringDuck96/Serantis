package dev.serantis.battle;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.ahrivaea.battle.BattleMove;
import dev.ahrivaea.party.PartySync;
import dev.serantis.battle.moves.MoveBook;
import dev.serantis.battle.moves.MoveExplain;
import dev.serantis.battle.moves.MoveSpec;
import dev.serantis.battle.skills.Skills;
import dev.serantis.battle.skills.XpBank;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Magic Staffs, one design per influence except Normal (Emberwrath, Rimespire, Thundercall, Stoneheart, Tidecaller,
 * Thornbloom, Riftwalker, Moonpetal, Gloomreaver, Runeweaver, Dawnbringer).
 *
 * A Wooden Staff is crafted from three sticks and an Eye of Ender. Until it has a spell it's just a wooden mace: take it to an enchanting
 * table (right-click the table holding it) and you're offered three spells at random from the Move Dex; pick one
 * and pay 25 XP (less with Presence) and the staff is linked to that spell for good, becoming that influence's
 * staff (a fire spell makes it an Emberwrath Staff). (The offer stays the same until you pick.) A Wizard starts with a staff already linked to a random spell
 * of the influence they chose.
 *
 * In battle, a linked staff in your main hand or off hand lets you cast its spell, as well as your normal attack.
 * A staff in hand also starts battles like any weapon.
 */
public final class Staffs {
    public static final Map<String, Item> STAFFS = new LinkedHashMap<>();
    public static Item BLANK;
    public static final int LINK_COST = 25;
    private static final String SPELL = "serantis_spell", OFFER = "serantis_offer";

    private Staffs() {}

    private static Item item(String name, Item.Properties props) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:" + name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(props.setId(key)));
    }

    private static Item item(String name) { return item(name, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)); }

    public static void register() {
        // The Wooden Staff: until it's linked to a spell it's just a wooden mace (5 damage, slow swing, wears out like wood).
        BLANK = item("magic_staff", new Item.Properties().durability(59).attributes(
                net.minecraft.world.item.component.ItemAttributeModifiers.builder()
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID, 4.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                        .add(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED, new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID, -3.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE),
                                net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND)
                        .build()));
        for (String inf : Masters.INFLUENCES) STAFFS.put(inf, item(inf + "_staff"));
    }

    public static boolean isStaff(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(BLANK)) return true;
        for (Item i : STAFFS.values()) if (stack.is(i)) return true;
        return false;
    }

    private static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    /** The spell linked to this staff, or null. */
    public static MoveSpec spellOf(ItemStack stack) {
        if (!isStaff(stack)) return null;
        String id = data(stack).getStringOr(SPELL, "");
        MoveSpec s = id.isEmpty() ? null : MoveBook.byId(id);
        return s != null && s.enabled ? s : null;
    }

    private static String influenceOf(MoveSpec s) {
        for (String inf : Masters.INFLUENCES) if (inf.equals(s.influence)) return inf;
        return "none";                                          // universal moves
    }

    /** A staff linked to this spell (its colour is the spell's influence). */
    public static ItemStack linked(MoveSpec spell) {
        String inf = influenceOf(spell);
        ItemStack stack = new ItemStack(STAFFS.get(inf));
        CompoundTag tag = new CompoundTag();
        tag.putString(SPELL, spell.id);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        stack.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Spell: " + spell.name + " (" + Masters.influenceName(inf) + ")").withStyle(ChatFormatting.GOLD),
                Component.literal(MoveExplain.line(spell)).withStyle(ChatFormatting.GRAY),
                Component.literal("Cast it in battle with this staff in either hand.").withStyle(ChatFormatting.DARK_GRAY))));
        return stack;
    }

    /** Spells a staff can hold: every move of the eleven influences that have staffs (not Normal or universal moves). */
    private static List<MoveSpec> spells() {
        List<MoveSpec> out = new ArrayList<>();
        for (MoveSpec s : MoveBook.all())
            if (s.enabled && !"none".equals(s.influence) && STAFFS.containsKey(s.influence)) out.add(s);
        return out;
    }

    /** Influences with a staff of their own (all but Normal). */
    public static boolean hasStaff(String inf) { return inf != null && !"none".equals(inf) && STAFFS.containsKey(inf); }

    /** A Wizard's first staff: linked to a random spell of the chosen influence. */
    public static ItemStack starter(String inf, RandomSource random) {
        List<MoveSpec> pool = new ArrayList<>();
        for (MoveSpec s : spells()) if (inf.equals(s.influence)) pool.add(s);
        if (pool.isEmpty()) return new ItemStack(BLANK);
        return linked(pool.get(random.nextInt(pool.size())));
    }

    // ------------------------------------------------------------------ battle

    /** The spells the player can cast: a linked staff's in their main hand and in their off hand. */
    public static List<BattleMove> spellsInHand(Player p) {
        List<BattleMove> out = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (ItemStack s : List.of(p.getMainHandItem(), p.getOffhandItem())) {
            MoveSpec spell = spellOf(s);
            if (spell != null && !seen.contains(spell.id)) { seen.add(spell.id); out.add(MoveBook.toBattleMove(spell)); }
        }
        return out;
    }

    // ------------------------------------------------------------------ linking at an enchanting table

    private record Linking(BlockPos table) {}
    private static final Map<UUID, Linking> LINKING = new ConcurrentHashMap<>();

    /** The staff without a spell the player is holding (main hand first). */
    private static InteractionHand blankHand(Player p) {
        if (isStaff(p.getMainHandItem()) && spellOf(p.getMainHandItem()) == null) return InteractionHand.MAIN_HAND;
        if (isStaff(p.getOffhandItem()) && spellOf(p.getOffhandItem()) == null) return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean atTable(ServerPlayer p, BlockPos table) {
        return table != null && p.level().getBlockState(table).is(Blocks.ENCHANTING_TABLE)
                && p.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table)) < 6 * 6;
    }

    /** Right-clicked an enchanting table with an unlinked staff: show its three spells (rolled once, kept on the staff). */
    public static void open(ServerPlayer p, BlockPos table) {
        InteractionHand hand = blankHand(p);
        if (hand == null || !atTable(p, table)) return;
        ItemStack staff = p.getItemInHand(hand);
        if (data(staff).getStringOr(OFFER, "").isEmpty()) {
            List<MoveSpec> pool = spells();
            List<String> ids = new ArrayList<>();
            while (ids.size() < 3 && !pool.isEmpty()) ids.add(pool.remove(p.getRandom().nextInt(pool.size())).id);
            CustomData.update(DataComponents.CUSTOM_DATA, staff, t -> t.putString(OFFER, String.join(",", ids)));
        }
        LINKING.put(p.getUUID(), new Linking(table));
        PartySync.sync(p);
    }

    public static void close(ServerPlayer p) {
        if (LINKING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    private static List<MoveSpec> offer(ItemStack staff) {
        List<MoveSpec> out = new ArrayList<>();
        for (String id : data(staff).getStringOr(OFFER, "").split(",")) {
            MoveSpec s = id.isBlank() ? null : MoveBook.byId(id.trim());
            if (s != null && s.enabled) out.add(s);
        }
        return out;
    }

    /** Links the chosen spell to the staff for good. */
    public static void link(ServerPlayer p, int index) {
        Linking l = LINKING.get(p.getUUID());
        InteractionHand hand = blankHand(p);
        if (l == null || hand == null || !atTable(p, l.table())) return;
        List<MoveSpec> offer = offer(p.getItemInHand(hand));
        if (index < 0 || index >= offer.size()) return;
        int cost = Skills.price(p, LINK_COST);
        boolean free = p.getAbilities().instabuild;
        if (!free && XpBank.points(p) < cost) { p.sendSystemMessage(Component.literal("Linking a spell costs " + cost + " XP.")); return; }
        if (!free) XpBank.take(p, cost);
        MoveSpec spell = offer.get(index);
        p.setItemInHand(hand, linked(spell));
        LINKING.remove(p.getUUID());
        p.level().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
        p.sendSystemMessage(Component.literal("Your Wooden Staff became " + p.getItemInHand(hand).getHoverName().getString()
                + ", linked to " + spell.name + " for good. Hold it in either hand to cast it in battle. (-"
                + cost + " XP)").withStyle(ChatFormatting.AQUA));
        PartySync.sync(p);
    }

    /** "serantis_staff" in the party state while a player is linking a staff. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Linking l = LINKING.get(p.getUUID());
        if (l == null) return;
        InteractionHand hand = blankHand(p);
        if (hand == null || !atTable(p, l.table())) { LINKING.remove(p.getUUID()); return; }
        JsonObject o = new JsonObject();
        JsonArray spells = new JsonArray();
        for (MoveSpec s : offer(p.getItemInHand(hand))) {
            JsonObject m = new JsonObject();
            m.addProperty("name", s.name);
            m.addProperty("influence", Masters.influenceName(influenceOf(s)));
            m.addProperty("line", MoveExplain.line(s));
            m.addProperty("explain", MoveExplain.explain(s));
            spells.add(m);
        }
        o.add("spells", spells);
        o.addProperty("cost", Skills.price(p, LINK_COST));
        o.addProperty("xp", XpBank.points(p));
        state.add("serantis_staff", o);
    }
}
