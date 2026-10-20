package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.serantis.battle.BattleStats;
import dev.serantis.battle.fx.Fx;
import dev.serantis.battle.fx.FxHolder;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import dev.serantis.battle.moves.MovesHolder;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreatureEntity.class)
public abstract class CreatureEntityMixin implements MovesHolder, FxHolder {
    @Unique private String serantis$moves;

    // ---- battle effects: flash = (count << 3) | kind, wiggle = count. The client plays one when its value changes.
    @Unique private static final EntityDataAccessor<Integer> SERANTIS_FLASH = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    @Unique private static final EntityDataAccessor<Integer> SERANTIS_WIGGLE = SynchedEntityData.defineId(CreatureEntity.class, EntityDataSerializers.INT);
    @Unique private long serantis$flashTick = -1;     // server: game tick of the last flash, and its kind
    @Unique private int serantis$flashSent;
    @Unique private int serantis$seenFlash = Integer.MIN_VALUE, serantis$seenWiggle = Integer.MIN_VALUE;   // client
    @Unique private int serantis$playKind;
    @Unique private float serantis$playStart = -1000f, serantis$wiggleAt = -1000f;

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void serantis$defineFx(SynchedEntityData.Builder builder, CallbackInfo ci) {
        builder.define(SERANTIS_FLASH, 0);
        builder.define(SERANTIS_WIGGLE, 0);
    }

    @Override public void serantis$flash(int kind) {
        CreatureEntity self = (CreatureEntity) (Object) this;
        long now = self.level().getGameTime();
        if (now == serantis$flashTick && Fx.priority(kind) < Fx.priority(serantis$flashSent)) return;
        serantis$flashTick = now;
        serantis$flashSent = kind;
        int count = (self.getEntityData().get(SERANTIS_FLASH) >>> 3) + 1;
        self.getEntityData().set(SERANTIS_FLASH, ((count & 0xFFFFF) << 3) | kind);
    }

    @Override public void serantis$wiggle() {
        CreatureEntity self = (CreatureEntity) (Object) this;
        self.getEntityData().set(SERANTIS_WIGGLE, self.getEntityData().get(SERANTIS_WIGGLE) + 1);
    }

    // Client: notice new effects as they arrive (not just when the creature is next drawn).
    @Inject(method = "tick", at = @At("HEAD"))
    private void serantis$watchFx(CallbackInfo ci) {
        CreatureEntity self = (CreatureEntity) (Object) this;
        if (!self.level().isClientSide()) return;
        int flash = self.getEntityData().get(SERANTIS_FLASH), wiggle = self.getEntityData().get(SERANTIS_WIGGLE);
        if (flash != serantis$seenFlash) {
            if (serantis$seenFlash != Integer.MIN_VALUE) { serantis$playKind = flash & 7; serantis$playStart = self.tickCount; }
            serantis$seenFlash = flash;
        }
        if (wiggle != serantis$seenWiggle) {
            if (serantis$seenWiggle != Integer.MIN_VALUE) serantis$wiggleAt = self.tickCount;
            serantis$seenWiggle = wiggle;
        }
    }

    @Override public int serantis$flashKind() { return serantis$playKind; }
    @Override public float serantis$flashStart() { return serantis$playStart; }
    @Override public float serantis$wiggleStart() { return serantis$wiggleAt; }

    @Override public String serantis$moves() { return serantis$moves; }
    @Override public void serantis$setMoves(String moves) { serantis$moves = moves; }

    // A creature's level is kept within 0-20 when it is set up (was 0-50).
    @ModifyConstant(method = "setup", constant = @Constant(intValue = 50))
    private int serantis$levelCap(int original) {
        return BattleStats.MAX_LEVEL;
    }

    // Its moves are saved with it in the world...
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveMoves(ValueOutput out, CallbackInfo ci) {
        if (serantis$moves != null) out.putString("SerantisMoves", serantis$moves);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$loadMoves(ValueInput in, CallbackInfo ci) {
        String saved = in.getStringOr("SerantisMoves", "");
        if (!saved.isEmpty()) serantis$moves = saved;
    }

    // ...and carried over when it's caught or sent out from the party.
    @Inject(method = "toCaught", at = @At("RETURN"))
    private void serantis$toCaught(CallbackInfoReturnable<CaughtCreature> cir) {
        if (serantis$moves != null && cir.getReturnValue() != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setMoves(serantis$moves);
    }

    @Inject(method = "applyCaught", at = @At("RETURN"))
    private void serantis$applyCaught(CaughtCreature caught, CallbackInfo ci) {
        String moves = ((MovesHolder) (Object) caught).serantis$moves();
        if (moves != null) serantis$moves = moves;
    }
}
