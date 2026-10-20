package dev.serantis.battle.mixin;

import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.WorkerBrain;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.CaughtCreature;
import dev.serantis.battle.Pace;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The base screen shows each worker's Charisma-based work speed. */
@Mixin(Bases.class)
public abstract class BasesMixin {
    // ---- Skill tree: bought at your own Base Core, shown on its screen.
    @org.spongepowered.asm.mixin.injection.Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$skill(net.minecraft.server.level.ServerPlayer player, String json,
                                       org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        com.google.gson.JsonObject o;
        try { o = com.google.gson.JsonParser.parseString(json).getAsJsonObject(); } catch (RuntimeException e) { return; }
        if (!o.has("kind") || !"serantis_skill".equals(o.get("kind").getAsString())) return;
        ci.cancel();
        if (!o.has("pos") || !o.has("job")) { dev.serantis.battle.skills.Skills.answer(player, "The request was missing its base or skill."); return; }
        var p = o.getAsJsonArray("pos");
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(p.get(0).getAsInt(), p.get(1).getAsInt(), p.get(2).getAsInt());
        dev.ahrivaea.base.BaseCoreBlockEntity core = Bases.coreAt(player.level(), pos);
        if (core == null) { dev.serantis.battle.skills.Skills.answer(player, "Couldn't find your Base Core at " + pos.toShortString() + "."); return; }
        if (player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos)) > 4096) {
            dev.serantis.battle.skills.Skills.answer(player, "You're too far from your Base Core.");
            Bases.send(player, core);
            return;
        }
        String msg;
        try {
            msg = dev.serantis.battle.skills.Skills.buy(player, core, o.get("job").getAsString());
        } catch (RuntimeException e) {
            msg = "Something went wrong learning that skill (" + e + ").";
            org.slf4j.LoggerFactory.getLogger("serantis_skills").error("Skill purchase failed", e);
        }
        dev.serantis.battle.skills.Skills.answer(player, msg);
        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal(msg));
        Bases.send(player, core);
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = "view", at = @At("RETURN"))
    private static void serantis$skillsView(net.minecraft.server.level.ServerPlayer player, dev.ahrivaea.base.BaseCoreBlockEntity core,
                                            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<com.google.gson.JsonObject> cir) {
        if (cir.getReturnValue() != null) cir.getReturnValue().add("serantis_skills", dev.serantis.battle.skills.Skills.view(player, core));
    }

    @Unique private static CaughtCreature serantis$shown;

    // The worker whose speed is about to be shown (its level is read just before).
    @Redirect(method = "view", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/party/CaughtCreature;level()I"))
    private static int serantis$level(CaughtCreature creature) {
        serantis$shown = creature;
        return creature.level();
    }

    @Redirect(method = "view", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/base/WorkerBrain;speedAt(I)D"))
    private static double serantis$speed(int level) {
        CaughtCreature c = serantis$shown;
        if (c != null && c.influence() != null) {
            var species = Species.get(c.species());
            if (species.isPresent()) {
                var stats = dev.serantis.battle.Apples.of(c, species.get().statsFor(c.influence()));
                return Pace.workSpeed(level, stats.charisma());
            }
        }
        return WorkerBrain.speedAt(level);
    }
}
