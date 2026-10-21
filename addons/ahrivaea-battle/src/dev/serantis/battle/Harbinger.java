package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Harbinger of Calamity takes the Warden's place: summoned the same way (sculk shriekers), it claws its
 * way up out of the ground over three seconds. Everyone nearby watches it happen in a cutscene
 * (client/HarbingerScreen): a shot of it rising, then a close-up as it roars its line. In battle it taunts you.
 */
public final class Harbinger {
    public static final String SPECIES = "harbinger_of_calamity";
    public static final String ROAR = "Gwarrrrrgh!!! It's time for the Harbinger of Calamity to rule!";
    public static final String TAUNT = "You can't stop the chaos!!!";
    private static final int RISE_TICKS = 60, DEPTH = 6, SEE = 48;

    private record Pending(ServerLevel level, double x, double y, double z) {}
    private record Rising(CreatureEntity e, double x, double fromY, double toY, double z, int[] t) {}

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final List<Rising> RISING = new ArrayList<>();
    /** Players watching a Harbinger rise: its entity id, and whether it's up. */
    private static final Map<UUID, int[]> WATCHING = new ConcurrentHashMap<>();

    private Harbinger() {}

    public static boolean is(Entity e) {
        return e instanceof CreatureEntity c && SPECIES.equals(c.getSpeciesId());
    }

    /** A Warden appeared: the Harbinger comes instead (spawned on the next tick). */
    public static void queue(ServerLevel level, double x, double y, double z) {
        synchronized (PENDING) { PENDING.add(new Pending(level, x, y, z)); }
    }

    /** Every server tick. */
    public static void tick() {
        List<Pending> todo;
        synchronized (PENDING) { todo = new ArrayList<>(PENDING); PENDING.clear(); }
        for (Pending p : todo) spawn(p);
        if (!RISING.isEmpty()) rise();
    }

    private static void spawn(Pending p) {
        Species sp = Species.get(SPECIES).orElse(null);
        if (sp == null) return;
        Influence inf = sp.formOrDefault(Influence.DARKNESS);
        CreatureEntity e = Ahrivaea.spawn(p.level(), sp, inf, 20, -1f, p.x(), p.y() - DEPTH, p.z());
        if (e == null) return;
        e.setNoAi(true);
        e.setNoGravity(true);
        e.noPhysics = true;
        RISING.add(new Rising(e, p.x(), p.y() - DEPTH, p.y(), p.z(), new int[]{0}));
        for (ServerPlayer pl : p.level().players())
            if (pl.distanceToSqr(p.x(), p.y(), p.z()) < SEE * SEE && !pl.isSpectator()) {
                WATCHING.put(pl.getUUID(), new int[]{e.getId(), 0, 0});
                PartySync.sync(pl);
            }
    }

    private static void rise() {
        for (var it = RISING.iterator(); it.hasNext(); ) {
            Rising r = it.next();
            CreatureEntity e = r.e();
            if (e.isRemoved() || !(e.level() instanceof ServerLevel level)) { it.remove(); continue; }
            int t = ++r.t()[0];
            double f = Math.min(1.0, t / (double) RISE_TICKS), eased = 1 - Math.pow(1 - f, 3);
            e.setDeltaMovement(0, 0, 0);
            e.setPos(r.x(), r.fromY() + (r.toY() - r.fromY()) * eased, r.z());
            e.setHealth(e.getMaxHealth());
            var below = level.getBlockState(net.minecraft.core.BlockPos.containing(r.x(), r.toY() - 1, r.z()));
            var crack = new BlockParticleOption(ParticleTypes.BLOCK, below.isAir() ? Blocks.DEEPSLATE.defaultBlockState() : below);
            level.sendParticles(crack, r.x(), r.toY() + 0.1, r.z(), 30, 1.8, 0.2, 1.8, 0.15);
            if (t % 2 == 0) level.sendParticles(ParticleTypes.SCULK_SOUL, r.x(), r.toY() + 0.5, r.z(), 6, 1.5, 0.6, 1.5, 0.02);
            if (t % 10 == 1) level.playSound(null, r.x(), r.toY(), r.z(), SoundEvents.WARDEN_EMERGE, SoundSource.HOSTILE, 3f, 0.7f);
            if (t >= RISE_TICKS) {
                e.noPhysics = false;
                e.setNoGravity(false);
                e.setNoAi(false);
                level.playSound(null, r.x(), r.toY(), r.z(), SoundEvents.WARDEN_ROAR, SoundSource.HOSTILE, 4f, 0.6f);
                for (var en : WATCHING.entrySet())
                    if (en.getValue()[0] == e.getId()) {
                        en.getValue()[1] = 1;                                // risen: the close-up and the roar
                        ServerPlayer pl = level.getServer().getPlayerList().getPlayer(en.getKey());
                        if (pl != null) PartySync.sync(pl);
                    }
                it.remove();
            }
        }
    }

    /** The player's cutscene ended. */
    public static void watched(ServerPlayer p) {
        if (WATCHING.remove(p.getUUID()) != null) PartySync.sync(p);
    }

    /** Every second, per player: never stuck watching. */
    public static void check(ServerPlayer p) {
        int[] w = WATCHING.get(p.getUUID());
        if (w != null && ++w[2] > 30) watched(p);
    }

    /** A battle against a Harbinger began: it taunts you. */
    public static void taunt(ServerPlayer p) {
        p.sendSystemMessage(Component.literal("Harbinger of Calamity: \"" + TAUNT + "\"").withStyle(ChatFormatting.DARK_RED));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(5, 50, 15));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(Component.literal("")));
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal("\"" + TAUNT + "\"").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
    }

    /** "serantis_harbinger" in the party state while the player watches one rise. */
    public static void describe(ServerPlayer p, JsonObject state) {
        int[] w = WATCHING.get(p.getUUID());
        if (w == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("entity", w[0]);
        o.addProperty("risen", w[1] == 1);
        state.add("serantis_harbinger", o);
    }
}
