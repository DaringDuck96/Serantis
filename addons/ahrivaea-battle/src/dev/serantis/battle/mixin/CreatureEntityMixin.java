package dev.serantis.battle.mixin;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.serantis.battle.BattleStats;
import dev.serantis.battle.Temperament;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.creature.Stats;
import org.spongepowered.asm.mixin.injection.Redirect;
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
public abstract class CreatureEntityMixin implements MovesHolder, dev.serantis.battle.Market.Holder, dev.serantis.battle.Locomotion.Holder, dev.serantis.battle.Towns.Home {
    @Unique private String serantis$moves;
    @Unique private String serantis$temperament;
    @Unique private String serantis$nickname;
    @Unique private String serantis$apples;
    @Unique private String serantis$tutor;
    @Unique private String serantis$home;
    @Override public String serantis$home() { return serantis$home; }
    @Override public void serantis$setHome(String key) { serantis$home = key; }
    @Override public String serantis$tutor() { return serantis$tutor; }
    @Override public void serantis$setTutor(String offer) { serantis$tutor = offer; }
    @Unique private java.util.List<CaughtCreature> serantis$stock = java.util.List.of();
    @Unique private long serantis$leaveAt;

    @Override public java.util.List<CaughtCreature> serantis$stock() { return serantis$stock == null ? java.util.List.of() : serantis$stock; }
    @Override public void serantis$setStock(java.util.List<CaughtCreature> stock) { serantis$stock = java.util.List.copyOf(stock); }
    @Override public long serantis$leaveAt() { return serantis$leaveAt; }
    @Unique private int serantis$swaps;
    @Override public int serantis$swaps() { return serantis$swaps; }
    @Override public void serantis$setSwaps(int swaps) { serantis$swaps = swaps; }
    @Override public void serantis$setLeaveAt(long tick) { serantis$leaveAt = tick; }

    @Unique private net.minecraft.world.item.ItemStack serantis$carriedLoad;
    @Unique private boolean serantis$announced;                         // its Mutated/Enraged arrival was announced (see SpawnAlerts)

    @Inject(method = "tick", at = @At("HEAD"))
    private void serantis$marketTick(CallbackInfo ci) {
        if (serantis$carriedLoad != null && !((CreatureEntity) (Object) this).level().isClientSide()
                && ((CreatureEntity) (Object) this).level() instanceof net.minecraft.server.level.ServerLevel sl) {   // what it carried when saved: put down
            ((CreatureEntity) (Object) this).spawnAtLocation(sl, serantis$carriedLoad);
            serantis$carriedLoad = null;
        }
        if (serantis$leaveAt > 0) dev.serantis.battle.Safe.run("Market.tick", () -> dev.serantis.battle.Market.tick((CreatureEntity) (Object) this));
        CreatureEntity self = (CreatureEntity) (Object) this;
        if (!self.level().isClientSide()) {
            dev.serantis.battle.Safe.run("Locomotion.tick", () -> dev.serantis.battle.Locomotion.tick(self));
            if (serantis$swims(self) && self.getAirSupply() < self.getMaxAirSupply())
                self.setAirSupply(self.getMaxAirSupply());              // swimmers (and Water creatures) never drown
            if (!serantis$announced && self.tickCount >= 2) {            // just appeared: a Mutated or Enraged one gets noticed
                serantis$announced = true;
                dev.serantis.battle.Safe.run("SpawnAlerts.announce", () -> dev.serantis.battle.SpawnAlerts.announce(self));
            }
            dev.serantis.battle.Safe.run("Companions.tick", () -> dev.serantis.battle.Companions.tick(self));                  // never left behind
            dev.serantis.battle.Safe.run("Riddles.tick", () -> dev.serantis.battle.Riddles.tick(self));                     // a risen End dragon asks its riddle
            if (self.getVariant() != dev.ahrivaea.creature.Variant.NONE && dev.ahrivaea.villager.Villagers.SPECIES.equals(self.getSpeciesId()))
                self.setVariant(dev.ahrivaea.creature.Variant.NONE);     // villagers and Creature Masters are never Enraged or Mutated
        }
    }

    /** Can this creature swim: a swimming species, or a Water form of any species. */
    @Unique
    private static boolean serantis$swims(CreatureEntity c) {
        return c.getInfluence() == dev.ahrivaea.creature.Influence.WATER
                || c.getSpecies().map(sp -> sp.movement() == dev.ahrivaea.creature.Species.Movement.SWIM).orElse(false);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "setVariant", at = @At("HEAD"), argsOnly = true)
    private dev.ahrivaea.creature.Variant serantis$plainVillagers(dev.ahrivaea.creature.Variant v) {
        return dev.ahrivaea.villager.Villagers.SPECIES.equals(((CreatureEntity) (Object) this).getSpeciesId()) ? dev.ahrivaea.creature.Variant.NONE : v;
    }

    // How it moves in the wild (see Locomotion): flying, diving, crawling the bottom.
    @Unique private byte serantis$moveMode = dev.serantis.battle.Locomotion.UNSET;
    @Unique private net.minecraft.world.phys.Vec3 serantis$moveTarget;
    @Unique private int serantis$retarget;
    @Override public byte serantis$moveMode() { return serantis$moveMode; }
    @Override public void serantis$setMoveMode(byte mode) { serantis$moveMode = mode; }
    @Override public net.minecraft.world.phys.Vec3 serantis$moveTarget() { return serantis$moveTarget; }
    @Override public void serantis$setMoveTarget(net.minecraft.world.phys.Vec3 target) { serantis$moveTarget = target; }
    @Override public int serantis$retarget() { return serantis$retarget; }
    @Override public void serantis$setRetarget(int ticks) { serantis$retarget = ticks; }

    @Unique private long serantis$koUntil;
    @Override public long serantis$koUntil() { return serantis$koUntil; }
    @Override public void serantis$setKoUntil(long until) { serantis$koUntil = until; }
    @Unique private String serantis$ailment;
    @Override public String serantis$ailment() { return serantis$ailment; }
    @Override public void serantis$setAilment(String ailment) { serantis$ailment = ailment; }
    @Override public String serantis$apples() { return serantis$apples; }
    @Override public void serantis$setApples(String apples) { serantis$apples = apples; }

    @Override public String serantis$nickname() { return serantis$nickname; }
    @Override public void serantis$setNickname(String nickname) { serantis$nickname = nickname; }

    // A named creature shows its name over its head instead of its species.
    @Inject(method = "displayName", at = @At("RETURN"), cancellable = true)
    private void serantis$displayName(CallbackInfoReturnable<String> cir) {
        if (serantis$nickname != null && !serantis$nickname.isEmpty()) cir.setReturnValue(serantis$nickname);
    }

    @Override public String serantis$moves() { return serantis$moves; }
    @Override public void serantis$setMoves(String moves) { serantis$moves = moves; }
    @Override public String serantis$temperament() {
        if (serantis$temperament == null) serantis$temperament = Temperament.roll(((CreatureEntity) (Object) this).getRandom());
        return serantis$temperament;
    }
    @Override public void serantis$setTemperament(String temperament) { serantis$temperament = temperament; }

    // A creature gets its temperament when it first appears, and it changes its stats (max health here).
    @Inject(method = "setup", at = @At("HEAD"))
    private void serantis$rollTemperament(Species species, Influence influence, int level, float health, CallbackInfo ci) {
        serantis$temperament();
    }

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/creature/Species;statsFor(Ldev/ahrivaea/creature/Influence;)Ldev/ahrivaea/creature/Stats;"))
    private Stats serantis$setupStats(Species species, Influence influence, Species s, Influence i, int level, float health) {
        return dev.serantis.battle.Apples.full(species.statsFor(influence), serantis$temperament(), serantis$apples, species.id(), influence, level);
    }

    @Inject(method = "getStats", at = @At("RETURN"), cancellable = true)
    private void serantis$stats(CallbackInfoReturnable<Stats> cir) {
        if (((CreatureEntity) (Object) this).level().isClientSide()) return;      // temperaments live on the server
        CreatureEntity self = (CreatureEntity) (Object) this;
        cir.setReturnValue(dev.serantis.battle.Apples.full(cir.getReturnValue(), serantis$temperament(), serantis$apples,
                self.getSpeciesId(), self.getInfluence(), self.getCreatureLevel()));
    }

    // Note where a creature spawns naturally (for Nobles: better odds near their base).
    @Redirect(method = "finalizeSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/ServerLevelAccessor;getBiome(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/Holder;"))
    private net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> serantis$spawnSpot(net.minecraft.world.level.ServerLevelAccessor level, net.minecraft.core.BlockPos pos) {
        dev.serantis.battle.Villages.markSpawn(level.getLevel(), pos);
        return level.getBiome(pos);
    }

    // A creature's level is kept within 0-20 when it is set up (was 0-50).
    @ModifyConstant(method = "setup", constant = @Constant(intValue = 50))
    private int serantis$levelCap(int original) {
        return BattleStats.MAX_LEVEL;
    }

    // Its moves are saved with it in the world...
    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void serantis$saveMoves(ValueOutput out, CallbackInfo ci) {
        if (serantis$moves != null) out.putString("SerantisMoves", serantis$moves);
        if (serantis$temperament != null) out.putString("SerantisTemperament", serantis$temperament);
        if (serantis$nickname != null) out.putString("SerantisName", serantis$nickname);
        if (serantis$apples != null) out.putString("SerantisApples", serantis$apples);
        if (serantis$tutor != null) out.putString("SerantisTutor", serantis$tutor);
        if (serantis$koUntil != 0) out.putLong("SerantisKoUntil", serantis$koUntil);
        if (serantis$ailment != null) out.putString("SerantisAilment", serantis$ailment);
        if (serantis$home != null) out.putString("SerantisHome", serantis$home);
        out.putInt("SerantisMoveMode", serantis$moveMode);
        if (serantis$announced) out.putBoolean("SerantisAnnounced", true);
        if (dev.serantis.battle.BattleHold.isHeld((CreatureEntity) (Object) this)) out.putBoolean("SerantisHeld", true);
        var brain = ((CreatureEntity) (Object) this).brain();             // a worker's load is saved with it
        if (brain != null && !((WorkerBrainAccess) (Object) brain).serantis$carried().isEmpty())
            out.store("SerantisCarried", net.minecraft.world.item.ItemStack.CODEC, ((WorkerBrainAccess) (Object) brain).serantis$carried());
        if (serantis$leaveAt > 0) {
            out.putLong("SerantisLeaveAt", serantis$leaveAt);
            out.putInt("SerantisSwaps", serantis$swaps);
            out.store("SerantisStock", CaughtCreature.CODEC.listOf(), serantis$stock());
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void serantis$loadMoves(ValueInput in, CallbackInfo ci) {
        String saved = in.getStringOr("SerantisMoves", "");
        if (!saved.isEmpty()) serantis$moves = saved;
        String temperament = in.getStringOr("SerantisTemperament", "");
        if (!temperament.isEmpty()) serantis$temperament = temperament;
        String nickname = in.getStringOr("SerantisName", "");
        if (!nickname.isEmpty()) serantis$nickname = nickname;
        String apples = in.getStringOr("SerantisApples", "");
        if (!apples.isEmpty()) serantis$apples = apples;
        String home = in.getStringOr("SerantisHome", "");
        if (!home.isEmpty()) serantis$home = home;
        String tutor = in.getStringOr("SerantisTutor", "");
        if (!tutor.isEmpty()) serantis$tutor = tutor;
        serantis$leaveAt = in.getLongOr("SerantisLeaveAt", 0L);
        serantis$moveMode = (byte) in.getIntOr("SerantisMoveMode", dev.serantis.battle.Locomotion.UNSET);
        serantis$swaps = in.getIntOr("SerantisSwaps", 0);
        if (serantis$leaveAt > 0) serantis$stock = in.read("SerantisStock", CaughtCreature.CODEC.listOf()).orElse(java.util.List.of());
        if (in.getBooleanOr("SerantisHeld", false)) dev.serantis.battle.BattleHold.loadedHeld((CreatureEntity) (Object) this);   // saved mid-battle
        serantis$koUntil = in.getLongOr("SerantisKoUntil", 0L);
        String ailment = in.getStringOr("SerantisAilment", "");
        serantis$ailment = ailment.isEmpty() ? null : ailment;
        serantis$carriedLoad = in.read("SerantisCarried", net.minecraft.world.item.ItemStack.CODEC).orElse(null);
        serantis$announced = in.getBooleanOr("SerantisAnnounced", false) || in.getIntOr("SerantisMoveMode", -99) != -99;   // creatures saved before this: already seen
    }

    // ...and carried over when it's caught or sent out from the party.
    @Inject(method = "toCaught", at = @At("RETURN"))
    private void serantis$toCaught(CallbackInfoReturnable<CaughtCreature> cir) {
        if (serantis$moves != null && cir.getReturnValue() != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setMoves(serantis$moves);
        if (cir.getReturnValue() != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setTemperament(serantis$temperament());
        if (cir.getReturnValue() != null && serantis$nickname != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setNickname(serantis$nickname);
        if (cir.getReturnValue() != null && serantis$apples != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setApples(serantis$apples);
        if (cir.getReturnValue() != null && serantis$tutor != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setTutor(serantis$tutor);
        if (cir.getReturnValue() != null && serantis$koUntil != 0) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setKoUntil(serantis$koUntil);
        if (cir.getReturnValue() != null && serantis$ailment != null) ((MovesHolder) (Object) cir.getReturnValue()).serantis$setAilment(serantis$ailment);
    }

    // Farm animals follow the food they like (see Leading).
    @Inject(method = "registerGoals", at = @At("RETURN"))
    private void serantis$tempt(CallbackInfo ci) {
        CreatureEntity self = (CreatureEntity) (Object) this;
        ((MobGoalsAccess) (Object) this).serantis$goals().addGoal(4,
                new net.minecraft.world.entity.ai.goal.TemptGoal(self, 1.1, s -> dev.serantis.battle.Leading.tempts(self, s), false));
    }

    // Knocked out (see Knockout): food doesn't heal it yet.
    @Inject(method = "eat", at = @At("HEAD"), cancellable = true)
    private void serantis$koEat(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.ItemStack food,
                                CallbackInfoReturnable<net.minecraft.world.InteractionResult> cir) {
        if (!dev.serantis.battle.Knockout.is(this)) return;
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(((CreatureEntity) (Object) this).displayName() + " is knocked out: it can't be healed for another "
                + dev.serantis.battle.Knockout.clock(this) + "."));
        cir.setReturnValue(net.minecraft.world.InteractionResult.FAIL);
    }

    @Inject(method = "applyCaught", at = @At("HEAD"))
    private void serantis$applyCaught(CaughtCreature caught, CallbackInfo ci) {
        String moves = ((MovesHolder) (Object) caught).serantis$moves();
        if (moves != null) serantis$moves = moves;
        String temperament = ((MovesHolder) (Object) caught).serantis$temperament();
        if (temperament != null) serantis$temperament = temperament;
        serantis$nickname = ((MovesHolder) (Object) caught).serantis$nickname();
        serantis$apples = ((MovesHolder) (Object) caught).serantis$apples();
        serantis$tutor = ((MovesHolder) (Object) caught).serantis$tutor();
        serantis$koUntil = ((MovesHolder) (Object) caught).serantis$koUntil();
        serantis$ailment = ((MovesHolder) (Object) caught).serantis$ailment();
    }

    // ---- sounds (see CreatureSounds): borrowed from Minecraft's mobs to suit each creature; humanoids stay quiet
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return dev.serantis.battle.CreatureSounds.ambient((CreatureEntity) (Object) this);
    }

    protected net.minecraft.sounds.SoundEvent getHurtSound(net.minecraft.world.damagesource.DamageSource source) {
        CreatureEntity self = (CreatureEntity) (Object) this;
        if (dev.serantis.battle.CreatureSounds.silent(self)) return null;
        net.minecraft.sounds.SoundEvent s = dev.serantis.battle.CreatureSounds.hurt(self);
        return s != null ? s : net.minecraft.sounds.SoundEvents.GENERIC_HURT;
    }

    protected net.minecraft.sounds.SoundEvent getDeathSound() {
        CreatureEntity self = (CreatureEntity) (Object) this;
        if (dev.serantis.battle.CreatureSounds.silent(self)) return null;
        net.minecraft.sounds.SoundEvent s = dev.serantis.battle.CreatureSounds.death(self);
        return s != null ? s : net.minecraft.sounds.SoundEvents.GENERIC_DEATH;
    }

    public float getVoicePitch() {
        CreatureEntity self = (CreatureEntity) (Object) this;
        var r = self.getRandom();
        return dev.serantis.battle.CreatureSounds.pitch(self) * ((r.nextFloat() - r.nextFloat()) * 0.15f + 1f);
    }

    public int getAmbientSoundInterval() { return 240; }     // now and then, not constantly
}
