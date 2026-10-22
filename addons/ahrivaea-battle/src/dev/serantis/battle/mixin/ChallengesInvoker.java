package dev.serantis.battle.mixin;

import dev.ahrivaea.challenge.ChallengeDef;
import dev.ahrivaea.challenge.ChallengeState;
import dev.ahrivaea.challenge.Challenges;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;
import java.util.Optional;

/** The towers mod's package-private lookups (for TowerZones). */
@Mixin(Challenges.class)
public interface ChallengesInvoker {
    @Invoker("states")
    static Map<String, ChallengeState> serantis$states(MinecraftServer server) { throw new AssertionError(); }

    @Invoker("defOf")
    static Optional<ChallengeDef> serantis$defOf(String id, ChallengeState state) { throw new AssertionError(); }

    @Invoker("box")
    static BoundingBox serantis$box(ServerLevel level, ChallengeDef def, ChallengeState state) { throw new AssertionError(); }
}
