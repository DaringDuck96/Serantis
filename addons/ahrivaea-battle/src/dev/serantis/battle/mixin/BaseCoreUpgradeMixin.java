package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upgrading a base from Level 1 to Level 2 earns its owner a Backpack (see Backpacks). */
@Mixin(BaseCoreBlockEntity.class)
public abstract class BaseCoreUpgradeMixin {
    @Unique private int serantis$levelBefore;

    @Inject(method = "tryUpgrade", at = @At("HEAD"))
    private void serantis$before(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        serantis$levelBefore = ((BaseCoreBlockEntity) (Object) this).baseLevel();
    }

    @Inject(method = "tryUpgrade", at = @At("RETURN"))
    private void serantis$after(ServerPlayer player, ItemStack stack, CallbackInfo ci) {
        if (serantis$levelBefore < 2 && ((BaseCoreBlockEntity) (Object) this).baseLevel() >= 2 && player != null) dev.serantis.battle.Backpacks.reward(player);
    }
}
