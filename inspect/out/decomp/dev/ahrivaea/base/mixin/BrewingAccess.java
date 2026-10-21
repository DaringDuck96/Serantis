package dev.ahrivaea.base.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({BrewingStandBlockEntity.class})
public interface BrewingAccess {
   @Accessor("brewTime")
   int ahrivaea$brewTime();

   @Accessor("brewTime")
   void ahrivaea$setBrewTime(int var1);

   @Accessor("fuel")
   int ahrivaea$fuel();

   @Accessor("fuel")
   void ahrivaea$setFuel(int var1);

   @Invoker("isBrewable")
   static boolean ahrivaea$isBrewable(ServerLevel level, BrewingStandBlockEntity stand) {
      throw new AssertionError();
   }
}
