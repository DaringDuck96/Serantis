package dev.serantis.battle.mixin;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;
import java.util.UUID;

/**
 * Shared bases: a co-op group member can open another member's base, change its jobs and settings, upgrade it and
 * see its quarries, as if it were their own. Only the owner adds or removes workers (they'd change hands).
 */
@Mixin(Bases.class)
public abstract class BaseShareMixin {
    private static final Set<String> SERANTIS_OWNER_ONLY = Set.of("assign_party", "assign_box", "unassign");

    @Redirect(method = "open", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/base/BaseCoreBlockEntity;owner()Ljava/util/UUID;"))
    private static UUID serantis$openShared(BaseCoreBlockEntity be, ServerPlayer p, BaseCoreBlockEntity same) {
        return dev.serantis.battle.coop.Groups.baseOwnerFor(be, p);
    }

    @Redirect(method = "handle", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/base/BaseCoreBlockEntity;owner()Ljava/util/UUID;"))
    private static UUID serantis$handleShared(BaseCoreBlockEntity be, ServerPlayer p, String json) {
        return dev.serantis.battle.coop.Groups.baseOwnerFor(be, p);
    }

    @Redirect(method = "quarryInfo", at = @At(value = "INVOKE", target = "Ldev/ahrivaea/base/BaseCoreBlockEntity;owner()Ljava/util/UUID;"))
    private static UUID serantis$quarryShared(BaseCoreBlockEntity be, ServerPlayer p, BlockPos post) {
        return dev.serantis.battle.coop.Groups.baseOwnerFor(be, p);
    }

    @Inject(method = "handle", at = @At("HEAD"), cancellable = true)
    private static void serantis$ownerOnly(ServerPlayer p, String json, CallbackInfo ci) {
        try {
            var j = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            if (!SERANTIS_OWNER_ONLY.contains(j.get("kind").getAsString())) return;
            var at = j.getAsJsonArray("pos");
            BaseCoreBlockEntity be = Bases.coreAt(p.level(), new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()));
            if (be == null || be.owner() == null || be.owner().equals(p.getUUID())) return;
            p.sendOverlayMessage(Component.literal("Only " + be.ownerName() + " can add or remove this base's workers."));
            ci.cancel();
        } catch (RuntimeException ignored) {
            // let the base handle it
        }
    }
}
