package dev.serantis.battle.mixin;

import com.mojang.serialization.Codec;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.villager.VillagerJob;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Variant;
import dev.serantis.battle.Temperament;
import dev.serantis.battle.moves.MoveBook;
import dev.serantis.battle.moves.MovesCodec;
import net.minecraft.util.RandomSource;
import dev.serantis.battle.moves.MovesHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A caught creature remembers its three moves, saved with it and kept through every update. */
@Mixin(CaughtCreature.class)
public abstract class CaughtCreatureMixin implements MovesHolder {
    @Shadow @Final @Mutable public static Codec<CaughtCreature> CODEC;
    @Unique private String serantis$moves;
    @Unique private String serantis$temperament;
    @Unique private String serantis$nickname;
    @Unique private String serantis$apples;
    @Unique private String serantis$tutor;
    @Override public String serantis$tutor() { return serantis$tutor; }
    @Override public void serantis$setTutor(String offer) { serantis$tutor = offer; }

    @Override public String serantis$apples() { return serantis$apples; }
    @Override public void serantis$setApples(String apples) { serantis$apples = apples; }

    @Override public String serantis$nickname() { return serantis$nickname; }
    @Override public void serantis$setNickname(String nickname) { serantis$nickname = nickname; }

    @Override public String serantis$moves() { return serantis$moves; }
    @Override public void serantis$setMoves(String moves) { serantis$moves = moves; }
    @Override public String serantis$temperament() { return serantis$temperament; }
    @Override public void serantis$setTemperament(String temperament) { serantis$temperament = temperament; }

    // Every caught creature has a temperament and three moves from the moment it exists (starters
    // included). Saved ones replace these when a creature is loaded or copied.
    @Inject(method = "<init>(Ljava/lang/String;Ldev/ahrivaea/creature/Influence;IIFIIZLdev/ahrivaea/creature/Variant;Ldev/ahrivaea/villager/VillagerJob;)V",
            at = @At("RETURN"))
    private void serantis$born(String species, Influence influence, int level, int xp, float health, int mountTier, int mountFed,
                               boolean saddled, Variant variant, VillagerJob job, CallbackInfo ci) {
        java.util.Random random = new java.util.Random();
        if (serantis$temperament == null) serantis$temperament = Temperament.roll(random);
        if (serantis$moves == null && influence != null && MoveBook.available())
            serantis$moves = MoveBook.encode(MoveBook.roll(influence, RandomSource.create(random.nextLong())));
    }

    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void serantis$wrapCodec(CallbackInfo ci) { CODEC = MovesCodec.wrap(CODEC); }

    @Inject(method = "withProgress", at = @At("RETURN"))
    private void serantis$keepOnProgress(int level, int xp, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Inject(method = "withHealth", at = @At("RETURN"))
    private void serantis$keepOnHealth(float health, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Inject(method = "withJob", at = @At("RETURN"))
    private void serantis$keepOnJob(VillagerJob job, CallbackInfoReturnable<CaughtCreature> cir) { serantis$copy(cir.getReturnValue()); }

    @Unique
    private void serantis$copy(CaughtCreature next) {
        if (next != null && serantis$moves != null) ((MovesHolder) (Object) next).serantis$setMoves(serantis$moves);
        if (next != null && serantis$temperament != null) ((MovesHolder) (Object) next).serantis$setTemperament(serantis$temperament);
        if (next != null && serantis$nickname != null) ((MovesHolder) (Object) next).serantis$setNickname(serantis$nickname);
        if (next != null && serantis$apples != null) ((MovesHolder) (Object) next).serantis$setApples(serantis$apples);
        if (next != null && serantis$tutor != null) ((MovesHolder) (Object) next).serantis$setTutor(serantis$tutor);
    }
}
