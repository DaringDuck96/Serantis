package dev.serantis.battle.mixin;

import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.creature.CreatureEntity;
import dev.serantis.battle.Pace;
import net.minecraft.world.item.ItemStack;
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

    // ---- never stuck
    // The creatures mod gives up on a target it can't walk to after 15 seconds, then picks the same target again,
    // forever. Instead: the first time, the worker hops to a safe spot next to the target (inside its base); if it
    // still can't do the job, the target is set aside for every worker at that base, for 1 minute, then 2, 4...
    // up to 30, and anything it was carrying for it is left at the Base Core.
    @Shadow private int travelTicks;
    @Shadow private int workTicks;
    @Shadow private ItemStack carried;
    @Shadow private net.minecraft.core.BlockPos plantAt;
    @Shadow private void finish(dev.ahrivaea.base.BaseCoreBlockEntity be, int wait) { }

    @org.spongepowered.asm.mixin.Unique private boolean serantis$hopped;
    @org.spongepowered.asm.mixin.Unique private final java.util.List<net.minecraft.core.BlockPos> serantis$setAside = new java.util.ArrayList<>();
    @org.spongepowered.asm.mixin.Unique private static final java.util.UUID SERANTIS_SET_ASIDE = new java.util.UUID(0x5e7a, 0x51de);
    /** Per base core: target -> {set aside until (game time), strikes}. Shared by all its workers. */
    @org.spongepowered.asm.mixin.Unique private static final java.util.Map<net.minecraft.core.BlockPos, java.util.Map<net.minecraft.core.BlockPos, long[]>> SERANTIS_ASIDE = new java.util.concurrent.ConcurrentHashMap<>();
    @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Field serantis$taskField;
    @org.spongepowered.asm.mixin.Unique private static java.lang.reflect.Method serantis$targetOf, serantis$kindOf;

    @org.spongepowered.asm.mixin.Unique
    private Object serantis$task() {
        try {
            if (serantis$taskField == null) {
                serantis$taskField = WorkerBrain.class.getDeclaredField("task");
                serantis$taskField.setAccessible(true);
            }
            Object t = serantis$taskField.get(this);
            if (t != null && serantis$targetOf == null) {
                serantis$targetOf = t.getClass().getDeclaredMethod("target");
                serantis$targetOf.setAccessible(true);
                serantis$kindOf = t.getClass().getDeclaredMethod("kind");
                serantis$kindOf.setAccessible(true);
            }
            return t;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.core.BlockPos serantis$target() {
        try { Object t = serantis$task(); return t == null ? null : (net.minecraft.core.BlockPos) serantis$targetOf.invoke(t); }
        catch (ReflectiveOperationException | RuntimeException e) { return null; }
    }

    @org.spongepowered.asm.mixin.Unique
    private String serantis$kind() {
        try { Object t = serantis$task(); return t == null ? "" : ((Enum<?>) serantis$kindOf.invoke(t)).name(); }
        catch (ReflectiveOperationException | RuntimeException e) { return ""; }
    }

    @org.spongepowered.asm.mixin.Unique
    private static java.util.Map<net.minecraft.core.BlockPos, long[]> serantis$aside(net.minecraft.core.BlockPos core) {
        return SERANTIS_ASIDE.computeIfAbsent(core.immutable(), k -> new java.util.concurrent.ConcurrentHashMap<>());
    }

    @org.spongepowered.asm.mixin.Unique
    private void serantis$setAsideTarget(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos target) {
        long[] e = serantis$aside(core).computeIfAbsent(target.immutable(), k -> new long[]{0, 0});
        e[1] = Math.min(e[1] + 1, 6);
        e[0] = level.getGameTime() + 1200L * (1L << (e[1] - 1));      // 1, 2, 4, 8, 16, 32 minutes (30-ish at most)
    }

    /** A safe spot inside the base, close enough to the target to work on it, nearest the target. */
    @org.spongepowered.asm.mixin.Unique
    private net.minecraft.core.BlockPos serantis$spotNear(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, net.minecraft.core.BlockPos target) {
        net.minecraft.core.BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        net.minecraft.world.phys.Vec3 aim = net.minecraft.world.phys.Vec3.atCenterOf(target);
        for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(target.offset(-2, -3, -2), target.offset(2, 1, 2))) {
            if (p.equals(target) || !be.contains(p) || !level.isLoaded(p) || !dev.serantis.battle.TeleportBoots.safe(level, p)) continue;
            net.minecraft.world.phys.Vec3 feet = net.minecraft.world.phys.Vec3.atBottomCenterOf(p);
            double d = Math.min(feet.distanceTo(aim), feet.add(0, self.getBbHeight() * 0.85, 0).distanceTo(aim));
            if (d > 2.0 || d >= bestD) continue;
            if (!level.noCollision(self, self.getBoundingBox().move(feet.subtract(self.position())))) continue;
            best = p.immutable();
            bestD = d;
        }
        return best;
    }

    @org.spongepowered.asm.mixin.injection.Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Ldev/ahrivaea/base/WorkerBrain;finish(Ldev/ahrivaea/base/BaseCoreBlockEntity;I)V"))
    private void serantis$notStuck(WorkerBrain me, dev.ahrivaea.base.BaseCoreBlockEntity be, int wait, net.minecraft.server.level.ServerLevel level) {
        net.minecraft.core.BlockPos target = serantis$target();
        String kind = serantis$kind();
        if (wait == 20 && workTicks > 1200 && target != null && ("SMELT".equals(kind) || "BREW".equals(kind))) {
            serantis$setAsideTarget(level, target);                     // a furnace or stand that never finishes: let it be for a while
            finish(be, wait);
            return;
        }
        if (wait != 100) { finish(be, wait); return; }                 // 100: gave up walking to it
        if (target != null && !serantis$hopped) {
            net.minecraft.core.BlockPos stand = serantis$spotNear(level, be, target);
            if (stand != null) {                                        // hop next to it and carry on
                serantis$hopped = true;
                self.getNavigation().stop();
                self.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
                travelTicks = 1;
                return;
            }
        }
        if (!carried.isEmpty() && ("DELIVER".equals(kind) || "PLANT".equals(kind) || "FETCH".equals(kind))) {   // can't take it there: leave it at the core
            net.minecraft.world.entity.item.ItemEntity item = new net.minecraft.world.entity.item.ItemEntity(level, core.getX() + 0.5, core.getY() + 1.2, core.getZ() + 0.5, carried);
            level.addFreshEntity(item);
            carried = ItemStack.EMPTY;
        }
        if ("PLANT".equals(kind) || "FETCH".equals(kind)) {             // the replant spot it claimed goes back too
            if (plantAt != null) ((BaseCoreAccess) (Object) be).serantis$claimed().remove(plantAt, self.getUUID());
            plantAt = null;
        }
        if (target != null) serantis$setAsideTarget(level, target);
        finish(be, wait);
    }

    @Inject(method = "complete", at = @At("HEAD"))
    private void serantis$done(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        net.minecraft.core.BlockPos target = serantis$target();
        if (target != null) serantis$aside(core).remove(target);      // it worked after all: no more strikes
    }

    @Inject(method = "plan", at = @At("HEAD"))
    private void serantis$skipUnreachable(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, CallbackInfoReturnable<?> cir) {
        serantis$hopped = false;                                        // a new task gets its own hop
        long now = level.getGameTime();
        var claimed = ((BaseCoreAccess) (Object) be).serantis$claimed();
        claimed.entrySet().removeIf(e -> !SERANTIS_SET_ASIDE.equals(e.getValue()) && be.workerFor(e.getValue()) == null);   // claims of workers that are gone
        serantis$setAside.clear();
        var aside = serantis$aside(core);
        aside.entrySet().removeIf(e -> e.getValue()[0] < now - 20L * 60 * 60);   // forgotten after an hour of not failing
        for (var e : aside.entrySet())
            if (e.getValue()[0] >= now && !claimed.containsKey(e.getKey())) { claimed.put(e.getKey(), SERANTIS_SET_ASIDE); serantis$setAside.add(e.getKey()); }
        for (net.minecraft.core.BlockPos p : ((BaseCoreAccess) (Object) be).serantis$growables())   // nothing to tend underwater
            if (!level.getFluidState(p).isEmpty() && !claimed.containsKey(p)) { claimed.put(p, SERANTIS_SET_ASIDE); serantis$setAside.add(p); }
    }

    @Inject(method = "plan", at = @At("RETURN"))
    private void serantis$restoreClaims(net.minecraft.server.level.ServerLevel level, dev.ahrivaea.base.BaseCoreBlockEntity be, CallbackInfoReturnable<?> cir) {
        var claimed = ((BaseCoreAccess) (Object) be).serantis$claimed();
        for (net.minecraft.core.BlockPos p : serantis$setAside) claimed.remove(p, SERANTIS_SET_ASIDE);
        serantis$setAside.clear();
    }

    // A worker its base no longer lists (a stale copy after a respawn): it leaves, dropping what it carried.
    // In water or lava it keeps working (FloatGoalMixin lets the brain run) and swims up on its own.
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void serantis$ghostAndSwim(net.minecraft.server.level.ServerLevel level, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.ahrivaea.base.BaseCoreBlockEntity be = dev.ahrivaea.base.Bases.coreAt(level, core);
        if (be != null && be.workerFor(self.getUUID()) == null) {
            if (!carried.isEmpty()) { self.spawnAtLocation(level, carried); carried = ItemStack.EMPTY; }
            self.discard();
            ci.cancel();
            return;
        }
        if ((self.isInWater() || self.isInLava()) && self.getFluidHeight(self.isInLava() ? net.minecraft.tags.FluidTags.LAVA : net.minecraft.tags.FluidTags.WATER) > self.getFluidJumpThreshold())
            self.getJumpControl().jump();
    }

    // Arriving is measured from the nearest of its feet, middle or eyes, so a tall chest stack can be reached.
    @org.spongepowered.asm.mixin.injection.Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/Vec3;distanceTo(Lnet/minecraft/world/phys/Vec3;)D", ordinal = 0))
    private double serantis$reachFromBody(net.minecraft.world.phys.Vec3 feet, net.minecraft.world.phys.Vec3 where) {
        double h = self.getBbHeight();
        return Math.min(feet.distanceTo(where), Math.min(feet.add(0, h * 0.5, 0).distanceTo(where), feet.add(0, h * 0.9, 0).distanceTo(where)));
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
