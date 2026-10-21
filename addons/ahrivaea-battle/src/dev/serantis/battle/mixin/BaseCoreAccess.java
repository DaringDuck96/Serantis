package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Which worker is busy with which spot in a base (see WorkerBrainMixin). */
@Mixin(BaseCoreBlockEntity.class)
public interface BaseCoreAccess {
    @Accessor("claimed")
    Map<BlockPos, UUID> serantis$claimed();
}
