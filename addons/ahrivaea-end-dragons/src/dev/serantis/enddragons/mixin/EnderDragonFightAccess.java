package dev.serantis.enddragons.mixin;

import net.minecraft.world.level.dimension.end.EnderDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

/** Which dragon the End's dragon fight is about (see VanillaMobsMixin). */
@Mixin(EnderDragonFight.class)
public interface EnderDragonFightAccess {
    @Accessor("dragonUUID")
    void serantis$setDragonUUID(UUID id);
}
