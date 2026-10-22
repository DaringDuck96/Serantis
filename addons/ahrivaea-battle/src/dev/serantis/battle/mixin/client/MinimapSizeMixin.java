package dev.serantis.battle.mixin.client;

import dev.ahrivaea.map.client.MapView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** The minimap is 25% smaller on screen (75 pixels across instead of 100), at the same zoom. */
@Mixin(MapView.class)
public abstract class MinimapSizeMixin {
    @ModifyConstant(method = "minimap", constant = @Constant(intValue = 100))
    private static int serantis$size(int size) { return 75; }
}
