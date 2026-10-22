package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Upgrading a base can't make it overlap another player's base (see BaseShape.clash), and the message tells its real size. */
@Mixin(BaseCoreBlockEntity.class)
public abstract class BaseOverlapMixin {
    @Inject(method = "tryUpgrade", at = @At("HEAD"), cancellable = true)
    private void serantis$noOverlap(ServerPlayer p, ItemStack stack, CallbackInfo ci) {
        BaseCoreBlockEntity self = (BaseCoreBlockEntity) (Object) this;
        if (!self.isUpgradeItem(stack) || !(self.getLevel() instanceof ServerLevel level)) return;
        java.util.UUID owner = self.owner() != null ? self.owner() : p.getUUID();
        String other = dev.serantis.battle.BaseShape.clash(level, self.getBlockPos(), dev.serantis.battle.BaseShape.radiusAt(self.baseLevel() + 1), owner, self.getBlockPos());
        if (other == null) return;
        p.sendOverlayMessage(Component.literal("Upgrading would make this base overlap " + other + "'s base. Bases can only overlap their owner's other bases."));
        ci.cancel();
    }

    @ModifyArg(method = "tryUpgrade", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private String serantis$upgradeText(String text) {
        BaseCoreBlockEntity self = (BaseCoreBlockEntity) (Object) this;
        if (text == null || !text.startsWith("Base upgraded to level ")) return text;
        return "Base upgraded to level " + self.baseLevel() + ": " + (2 * self.radius()) + " blocks across, up to " + self.cap() + " workers.";
    }
}
