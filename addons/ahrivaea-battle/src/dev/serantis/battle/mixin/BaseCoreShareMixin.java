package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.UUID;

/** Shared bases: a co-op group member can upgrade another member's base (see BaseShareMixin). */
@Mixin(BaseCoreBlockEntity.class)
public abstract class BaseCoreShareMixin {
    @Redirect(method = "tryUpgrade", at = @At(value = "FIELD", target = "Ldev/ahrivaea/base/BaseCoreBlockEntity;owner:Ljava/util/UUID;", opcode = 180))
    private UUID serantis$upgradeShared(BaseCoreBlockEntity self, ServerPlayer p, ItemStack stack) {
        return dev.serantis.battle.coop.Groups.baseOwnerFor(self, p);
    }
}
