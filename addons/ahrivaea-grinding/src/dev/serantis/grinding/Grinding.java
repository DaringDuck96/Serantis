package dev.serantis.grinding;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Grindstone grinding: cobblestone -> gravel, gravel -> sand, 2 for 1 (64 makes 32).
 * Put the stack in either top slot of a grindstone with the other slot empty.
 */
public final class Grinding {
    /** What's left in the input slot once the result is taken (an odd item stays put). */
    public record Leftover(int slot, ItemStack stack, ItemStack input) {}

    private static final Map<Container, Leftover> LEFTOVER = new WeakHashMap<>();

    private Grinding() {}

    public static Item output(ItemStack in) {
        if (in.is(Items.COBBLESTONE)) return Items.GRAVEL;
        if (in.is(Items.GRAVEL)) return Items.SAND;
        return null;
    }

    public static boolean grindable(ItemStack in) { return !in.isEmpty() && output(in) != null; }

    /** The result for these two slots, or null if they aren't a grinding recipe. Remembers the leftover. */
    public static ItemStack result(Container slots, ItemStack top, ItemStack bottom) {
        synchronized (LEFTOVER) { LEFTOVER.remove(slots); }
        int index;
        ItemStack in;
        if (grindable(top) && bottom.isEmpty()) { index = 0; in = top; }
        else if (grindable(bottom) && top.isEmpty()) { index = 1; in = bottom; }
        else return null;
        int made = in.getCount() / 2;
        if (made <= 0) return ItemStack.EMPTY;
        synchronized (LEFTOVER) { LEFTOVER.put(slots, new Leftover(index, in.copyWithCount(in.getCount() - made * 2), in.copy())); }
        return new ItemStack(output(in), made);
    }

    public static Leftover leftover(Container slots) {
        synchronized (LEFTOVER) { return LEFTOVER.get(slots); }
    }
}
