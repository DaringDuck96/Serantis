package dev.serantis.battle.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drinking a bucket of milk uses the bucket up: no empty bucket comes back (milk is the cure for statuses, see Ailments). */
@Mixin(ItemStack.class)
public abstract class MilkBucketMixin {
    @Unique private static final ThreadLocal<Boolean> SERANTIS_MILK = ThreadLocal.withInitial(() -> false);

    @Inject(method = "finishUsingItem", at = @At("HEAD"))
    private void serantis$drinking(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        SERANTIS_MILK.set(((ItemStack) (Object) this).is(Items.MILK_BUCKET));
    }

    @Inject(method = "finishUsingItem", at = @At("RETURN"), cancellable = true)
    private void serantis$noBucket(Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        boolean milk = SERANTIS_MILK.get();
        SERANTIS_MILK.set(false);
        if (milk && cir.getReturnValue() != null && cir.getReturnValue().is(Items.BUCKET)) cir.setReturnValue(ItemStack.EMPTY);
    }
}
