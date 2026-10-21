package dev.serantis.battle.mixin;

import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.Pace;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Base workers work at a speed set by their Charisma (see Pace), not just their level. */
@Mixin(WorkerBrain.class)
public abstract class WorkerBrainMixin {
    @Shadow @Final private CreatureEntity self;
    @Shadow @Final private net.minecraft.core.BlockPos core;

    @Inject(method = "speed", at = @At("HEAD"), cancellable = true)
    private void serantis$speed(CallbackInfoReturnable<Double> cir) {
        if (self == null || self.getInfluence() == null) return;
        if (self.getSpecies().isPresent())         // getStats includes its temperament
            cir.setReturnValue(Pace.workSpeed(self.getCreatureLevel(), self.getStats().charisma()) * serantis$bard() * serantis$performer());
    }

    // The base belongs to a Performer (online): everyone there works 5% faster.
    private double serantis$performer() {
        if (core == null || !(self.level() instanceof net.minecraft.server.level.ServerLevel level)) return 1.0;
        var base = dev.ahrivaea.base.Bases.coreAt(level, core);
        if (base == null || base.owner() == null) return 1.0;
        var owner = level.getServer().getPlayerList().getPlayer(base.owner());
        return dev.serantis.battle.PlayerClass.is(owner, "performer") ? dev.serantis.battle.PlayerClass.PERFORMER_WORK : 1.0;
    }

    // A seed a worker plants counts towards its base owner's Seeds Planted.
    @Inject(method = "complete", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;shrink(I)V"))
    private void serantis$planted(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (self != null && self.level() instanceof net.minecraft.server.level.ServerLevel level) dev.serantis.battle.BaseCredit.planted(level, core);
    }

    // A Bard working at the same base: everyone there works 15% faster.
    private double serantis$bard() {
        if (core == null || !(self.level() instanceof net.minecraft.server.level.ServerLevel level)) return 1.0;
        var base = dev.ahrivaea.base.Bases.coreAt(level, core);
        return dev.serantis.battle.Villages.has(base, "bard") ? dev.serantis.battle.Villages.BARD_SPEED : 1.0;
    }

    // ---- never stuck (see the class notes in the README's base section)
    // The creatures mod gives up on a target it can't walk to after 15 seconds, but then picks the same target
    // again, forever. Instead: the first time, the worker hops next to the target (as miners already do); if
    // that doesn't work either, the target is set aside for a minute so the worker gets on with other jobs.
    @Shadow private int travelTicks;
    @Shadow private void finish(dev.ahrivaea.base.BaseCoreBlockEntity be, int wait) { }
    @Shadow private net.minecraft.core.BlockPos standNear(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos target) { return null; }

    @org.spongepowered.asm.mixin.Unique private boolean serantis$hopped;
    @org.spongepowered.asm.mixin.Unique private final java.util.Map<net.minecraft.core.BlockPos, Long> serantis$unreachable = new java.util.HashMap<>();
    @org.spongepowered.asm.mixin.Unique private final java.util.List<net.minecraft.core.BlockPos> serantis$setAside = new java.util.ArrayList<>();
    @org.spongepowered.asm.mixin.Unique private static final java.util.UUID SERANTIS_SET_ASIDE = new java.util.UUID(0x5e7a, 0x51de);
    @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Field serantis$taskField;
    @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Method serantis$targetOf;

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.core.BlockPos serantis$target() {
        try {
            if (serantis$taskField == null) {
                serantis$taskField = WorkerBrain.class.getDeclaredField("task");
                serantis$taskField.setAccessible(true);
            }
            Object t = serantis$taskField.get(this);
            if (t == null) return null;
            if (serantis$targetOf == null) {
                serantis$targetOf = t.getClass().getDeclaredMethod("target");
                serantis$targetOf.setAccessible(true);
            }
            return (net.minecraft.core.BlockPos) serantis$targetOf.invoke(t);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/base/WorkerBrain;finish(Ldev/ahrivaea/base/BaseCoreBlockEntity;I)V"))
    private void serantis$notStuck(WorkerBrain me, dev.ahrivaea.base.BaseCoreBlockEntity be, int wait, net.minecraft.server.level.ServerLevel level) {
        if (wait != 100) { finish(be, wait); return; }                 // 100: gave up walking to it
        net.minecraft.core.BlockPos target = serantis$target();
        if (target != null && !serantis$hopped) {
            net.minecraft.core.BlockPos stand = standNear(level, target);
            if (stand != null) {                                        // hop next to it and carry on
                serantis$hopped = true;
                self.getNavigation().stop();
                self.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                travelTicks = 1;
                return;
            }
        }
        if (target != null) serantis$unreachable.put(target.immutable(), level.getGameTime() + 1200);   // set aside for a minute
        finish(be, wait);
    }

    @Inject(method = "plan", at = @At("HEAD"))
    private void serantis$skipUnreachable(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, CallbackInfoReturnable<?> cir) {
        serantis$hopped = false;                                        // a new task gets its own hop
        long now = level.getGameTime();
        serantis$unreachable.values().removeIf(until -> until < now);
        serantis$setAside.clear();
        for (net.minecraft.core.BlockPos p : serantis$unreachable.keySet())
            if (!((BaseCoreAccess) (Object) be).serantis$claimed().containsKey(p)) { ((BaseCoreAccess) (Object) be).serantis$claimed().put(p, SERANTIS_SET_ASIDE); serantis$setAside.add(p); }
    }

    @Inject(method = "plan", at = @At("RETURN"))
    private void serantis$restoreClaims(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, CallbackInfoReturnable<?> cir) {
        for (net.minecraft.core.BlockPos p : serantis$setAside) ((BaseCoreAccess) (Object) be).serantis$claimed().remove(p, SERANTIS_SET_ASIDE);
        serantis$setAside.clear();
    }

    // ---- getting through: a worker that isn't getting closer jumps up to 2 blocks, or digs through natural blocks
    @Shadow private String status;

    @org.spongepowered.asm.mixin.Unique private static final net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> SERANTIS_NATURAL =
            net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK, net.minecraft.resources.Identifier.parse("serantis_battle:linx_movable"));

    @Inject(method = "tick", at = @At("RETURN"))
    private void serantis$getThrough(net.minecraft.server.level.ServerLevel level, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (status == null || !status.startsWith("On the way") || travelTicks < 30 || travelTicks % 15 != 0) return;
        net.minecraft.core.BlockPos target = serantis$target();
        if (target == null || self.isNoGravity() || self.isInWater() || self.isPassenger() || self.isVehicle()) return;
        dev.ahrivaea.base.BaseCoreBlockEntity be = dev.ahrivaea.base.Bases.coreAt(level, core);
        if (be == null) return;
        net.minecraft.world.phys.Vec3 at = self.position();
        double dx = target.getX() + 0.5 - at.x, dz = target.getZ() + 0.5 - at.z;
        if (dx * dx + dz * dz < 0.5) return;                            // right above or below it: nothing in the way sideways
        net.minecraft.core.Direction dir = net.minecraft.core.Direction.getApproximateNearest(dx, 0, dz);
        int height = Math.max(1, (int) Math.ceil(self.getBbHeight()));
        int width = Math.max(1, (int) Math.ceil(self.getBbWidth()));
        net.minecraft.core.BlockPos feet = self.blockPosition();
        net.minecraft.core.BlockPos front = feet.relative(dir, Math.max(1, (width + 1) / 2));
        // 1) a step of 1-2 blocks with room on top: jump it (twice a normal jump)
        int step = 0;
        while (step < 3 && serantis$solid(level, front.above(step))) step++;
        if (step >= 1 && step <= 2 && self.onGround()) {
            boolean room = true;
            for (int y = 0; y < height + 1 && room; y++) {
                room = !serantis$solid(level, front.above(step + y)) && !serantis$solid(level, feet.above(height + y));
            }
            if (room) {
                self.setDeltaMovement(dir.getStepX() * 0.28, step == 2 ? 0.62 : 0.5, dir.getStepZ() * 0.28);
                self.hasImpulse = true;
                return;
            }
        }
        // 2) otherwise dig through natural blocks in the way (never anything a player placed)
        int broken = 0;
        net.minecraft.core.Direction side = dir.getClockWise();
        for (int y = height - 1; y >= 0 && broken < 3; y--)
            for (int w = -(width - 1) / 2; w <= width / 2 && broken < 3; w++) {
                net.minecraft.core.BlockPos p = front.above(y).relative(side, w);
                if (serantis$diggable(level, be, p)) {
                    level.destroyBlock(p, true, self);
                    broken++;
                }
            }
        if (target.getY() > feet.getY()) {                              // climbing up: clear headroom too
            net.minecraft.core.BlockPos head = feet.above(height);
            if (broken < 3 && serantis$diggable(level, be, head)) level.destroyBlock(head, true, self);
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean serantis$solid(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p) {
        return !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
    }

    /** A natural block inside the base that a worker may dig out of its way. */
    @org.spongepowered.asm.mixin.Unique
    private static boolean serantis$diggable(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, net.minecraft.core.BlockPos p) {
        if (!be.contains(p) || !level.isLoaded(p)) return false;
        var st = level.getBlockState(p);
        if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || !st.is(SERANTIS_NATURAL)) return false;
        if (st.is(net.minecraft.tags.BlockTags.SAPLINGS) || st.getBlock() instanceof net.minecraft.world.level.block.CropBlock) return false;   // the farm stays
        float hard = st.getDestroySpeed(level, p);
        if (hard < 0 || hard > 3f) return false;                        // no obsidian or the like
        return !dev.ahrivaea.base.PlacedBlocks.isPlaced(level, p);      // never something a player built
    }
}
