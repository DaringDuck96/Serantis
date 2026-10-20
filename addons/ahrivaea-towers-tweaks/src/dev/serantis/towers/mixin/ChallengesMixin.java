package dev.serantis.towers.mixin;

import dev.ahrivaea.challenge.ChallengeDef;
import dev.ahrivaea.challenge.ChallengeState;
import dev.ahrivaea.challenge.Challenges;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * The first player to make an offering at a tower claims it for good. Others can still help rebuild
 * it (and still earn rank rewards), but when it's finished its Base Core, the extra Base Core and the
 * land around it go to the player who claimed it.
 */
@Mixin(Challenges.class)
public abstract class ChallengesMixin {
    @Shadow static void save(MinecraftServer server, String id, ChallengeState state) { }
    @Shadow static Optional<ChallengeDef> defOf(String id, ChallengeState state) { return Optional.empty(); }

    @Inject(method = "credit", at = @At("HEAD"))
    private static void serantis$claim(MinecraftServer server, String tower, String player, String name, int amount, CallbackInfo ci) {
        if (amount <= 0 || player == null || player.isEmpty()) return;
        ChallengeState st = Challenges.state(server, tower).orElse(null);
        if (st == null || st.complete || st.owner == null || !st.owner.isEmpty()) return;
        // A tower that already had offerings before this rule goes to its top donor so far.
        String claimer = st.donations.isEmpty() ? player : st.ranking().getFirst();
        st.owner = claimer;
        if (claimer.equals(player)) st.names.put(player, name);
        save(server, tower, st);
        String towerName = defOf(tower, st).map(ChallengeDef::name).orElse("This tower");
        String who = st.names.getOrDefault(claimer, name);
        server.getPlayerList().broadcastSystemMessage(Component.literal(who + " has claimed " + towerName
                + "! Anyone can help rebuild it, but it will always be theirs.").withStyle(ChatFormatting.GOLD), false);
    }

    // When it's finished, the tower goes to whoever claimed it (not the top donor).
    @Redirect(method = "complete", at = @At(value = "INVOKE", target = "Ljava/util/List;getFirst()Ljava/lang/Object;", ordinal = 0))
    private static Object serantis$keepOwner(List<?> ranking, MinecraftServer server, ServerLevel level, String id, ChallengeDef def, ChallengeState st) {
        return st.owner != null && !st.owner.isEmpty() ? st.owner : ranking.getFirst();
    }

    // Messages that talked about the top donor claiming the tower.
    @ModifyArg(method = "*", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;literal(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"))
    private static String serantis$wording(String text) {
        if (text == null) return null;
        return text
                .replace("Protected until rebuilt; the top donor then claims it.", "Unclaimed: the first player to make an offering claims it.")
                .replace("Its top donor claims it.", "The first player to make an offering claims it.")
                .replace(" as its top donor. Only", ". Only")
                .replace("(only they and their guild can build around it)", "(once it's rebuilt, only they and their guild can build around it)");
    }
}
