package dev.serantis.battle.mixin.client;

import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Players get the 3D armour layer (influence armour, Winged and Teleporting Boots). */
@Mixin(AvatarRenderer.class)
public abstract class AvatarArmorMixin {
    @SuppressWarnings({"unchecked", "rawtypes"})
    @Inject(method = "<init>", at = @At("RETURN"))
    private void serantis$armor(EntityRendererProvider.Context ctx, boolean slim, CallbackInfo ci) {
        ((LivingRendererInvoker) (Object) this).serantis$addLayer(new dev.serantis.battle.client.ArmorModelLayer((net.minecraft.client.renderer.entity.RenderLayerParent) (Object) this, ctx));
    }
}
