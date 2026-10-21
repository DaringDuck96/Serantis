package dev.serantis.battle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Backpacks: a reward, one each time one of your bases reaches Level 2 (they can't be crafted).
 * Right-click one to strap it on: it leaves your inventory and you wear it on your back. Press B to open it:
 * 36 more slots, as many as your inventory. Sneak and press B to take it off (with everything still inside).
 * A worn backpack is never lost, not even when you die. Each backpack keeps its own contents.
 */
public final class Backpacks {
    public static final int SLOTS = 36;
    public static Item BACKPACK;

    /** Added to players: the backpack they're wearing (empty if none). */
    public interface Holder {
        ItemStack serantis$backpack();
        void serantis$setBackpack(ItemStack stack);
    }

    private Backpacks() {}

    public static class BackpackItem extends Item {
        public BackpackItem(Properties p) { super(p); }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            wear(sp, hand);
            return InteractionResult.SUCCESS;
        }
    }

    public static void register() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.parse("serantis_battle:backpack"));
        BACKPACK = Registry.register(BuiltInRegistries.ITEM, key, new BackpackItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).setId(key)));
    }

    private static void tell(ServerPlayer p, String msg) { p.sendSystemMessage(Component.literal(msg).withStyle(ChatFormatting.YELLOW)); }

    static void wear(ServerPlayer p, InteractionHand hand) {
        Holder h = (Holder) p;
        if (!h.serantis$backpack().isEmpty()) { tell(p, "You're already wearing a backpack. Sneak and press B to take it off first."); return; }
        ItemStack stack = p.getItemInHand(hand);
        if (!stack.is(BACKPACK)) return;
        h.serantis$setBackpack(stack.copy());
        p.setItemInHand(hand, ItemStack.EMPTY);
        p.level().playSound(null, p.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1f);
        tell(p, "Backpack on. Press B to open it (sneak and press B to take it off).");
    }

    /** Pressed B: open the worn backpack. */
    public static void open(ServerPlayer p) {
        Holder h = (Holder) p;
        ItemStack pack = h.serantis$backpack();
        if (pack.isEmpty()) { tell(p, "You're not wearing a backpack. Right-click one to put it on."); return; }
        NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        pack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(items);
        SimpleContainer box = new SimpleContainer(SLOTS) {
            @Override
            public void setChanged() {                     // every change goes straight back into the backpack
                super.setChanged();
                List<ItemStack> now = new ArrayList<>();
                for (int i = 0; i < getContainerSize(); i++) now.add(getItem(i).copy());
                ItemStack worn = h.serantis$backpack();
                if (!worn.isEmpty()) worn.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(now));
            }

            @Override
            public boolean stillValid(Player player) { return !h.serantis$backpack().isEmpty(); }
        };
        for (int i = 0; i < SLOTS; i++) box.setItem(i, items.get(i));
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new ChestMenu(MenuType.GENERIC_9x4, id, inv, box, 4),
                pack.getHoverName()));
    }

    /** Sneak + B: take the backpack off, into the inventory (or dropped at your feet if it's full). */
    public static void takeOff(ServerPlayer p) {
        Holder h = (Holder) p;
        ItemStack pack = h.serantis$backpack();
        if (pack.isEmpty()) return;
        p.closeContainer();
        h.serantis$setBackpack(ItemStack.EMPTY);
        ItemStack copy = pack.copy();
        if (!p.getInventory().add(copy) && !copy.isEmpty()) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), copy));
        tell(p, "Backpack off. Right-click it to put it back on.");
    }

    /** A base just reached Level 2: its owner earns a backpack. */
    public static void reward(ServerPlayer p) {
        ItemStack pack = new ItemStack(BACKPACK);
        if (!p.getInventory().add(pack) && !pack.isEmpty()) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), pack));
        p.sendSystemMessage(Component.literal("Your base reached Level 2! You earned a Backpack: right-click it to wear it, then press B to open 36 extra slots.")
                .withStyle(ChatFormatting.GOLD));
    }
}
