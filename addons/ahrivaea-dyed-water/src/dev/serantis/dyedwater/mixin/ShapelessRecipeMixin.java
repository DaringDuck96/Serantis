package dev.serantis.dyedwater.mixin;

import dev.serantis.dyedwater.DyedWater;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Dyeing water uses up the water bucket whole: the bucket goes into the dyed bucket, so no empty
 * bucket is left behind in the grid (that would hand out a free bucket every time).
 */
@Mixin(ShapelessRecipe.class)
public abstract class ShapelessRecipeMixin implements CraftingRecipe {
    @Shadow @Final private ItemStackTemplate result;

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        if (result != null && DyedWater.isDyedBucket(result.item().value())) return NonNullList.withSize(input.size(), ItemStack.EMPTY);
        return CraftingRecipe.defaultCraftingReminder(input);
    }
}
