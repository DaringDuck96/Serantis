package dev.ahrivaea.base.mixin;

import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({AbstractFurnaceBlockEntity.class})
public interface FurnaceAccess {
   @Accessor("litTimeRemaining")
   int ahrivaea$litTime();

   @Accessor("litTimeRemaining")
   void ahrivaea$setLitTime(int var1);

   @Accessor("litTotalTime")
   void ahrivaea$setLitTotal(int var1);

   @Accessor("cookingTimer")
   int ahrivaea$cookTime();

   @Accessor("cookingTimer")
   void ahrivaea$setCookTime(int var1);

   @Accessor("cookingTotalTime")
   int ahrivaea$cookTotal();
}
