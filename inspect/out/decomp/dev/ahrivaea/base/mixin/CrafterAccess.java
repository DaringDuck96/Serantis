package dev.ahrivaea.base.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({CrafterBlock.class})
public interface CrafterAccess {
   @Invoker("dispenseFrom")
   void ahrivaea$craft(BlockState var1, ServerLevel var2, BlockPos var3);
}
