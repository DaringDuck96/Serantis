package dev.serantis.battle.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every respawn point the game sets is remembered, kept through death and saved with the player (see BedSpawn). */
@Mixin(ServerPlayer.class)
public abstract class BedRememberMixin implements dev.serantis.battle.BedSpawn.Holder {
    @Unique private GlobalPos serantis$bed;

    @Override public GlobalPos serantis$bed() { return serantis$bed; }
    @Override public void serantis$setBed(GlobalPos bed) { serantis$bed = bed; }

    @Inject(method = "setRespawnPosition", at = @At("HEAD"))
    private void serantis$remember(ServerPlayer.RespawnConfig cfg, boolean message, CallbackInfo ci) {
        dev.serantis.battle.Safe.run("remembering a bed", () -> dev.serantis.battle.BedSpawn.set((ServerPlayer) (Object) this, cfg));
    }

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void serantis$keepBed(ServerPlayer old, boolean keepEverything, CallbackInfo ci) {
        if (serantis$bed == null) serantis$bed = ((dev.serantis.battle.BedSpawn.Holder) old).serantis$bed();
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveBed(ValueOutput out, CallbackInfo ci) {
        if (serantis$bed == null) return;
        out.putString("SerantisBedDim", serantis$bed.dimension().identifier().toString());
        out.putLong("SerantisBedPos", serantis$bed.pos().asLong());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$readBed(ValueInput in, CallbackInfo ci) {
        String dim = in.getStringOr("SerantisBedDim", "");
        if (dim.isEmpty()) return;
        try {
            serantis$bed = GlobalPos.of(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dim)), BlockPos.of(in.getLongOr("SerantisBedPos", 0L)));
        } catch (RuntimeException e) {
            serantis$bed = null;
        }
    }
}
