package dev.serantis.battle;

import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Variant;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When a Mutated or Enraged creature appears in the wild, every player close enough to see it hears it:
 * a happy jingle and "Did you see something shiny?" for a Mutated one, a roar and "Ooh! They look mad!"
 * for an Enraged one. Each player hears each kind at most once every few seconds.
 */
public final class SpawnAlerts {
    private static final long QUIET_MS = 6000;
    private static final Map<UUID, long[]> LAST = new ConcurrentHashMap<>();   // {mutated, enraged} millis

    private SpawnAlerts() {}

    public static void announce(CreatureEntity c) {
        if (!(c.level() instanceof ServerLevel level) || c.isTame()) return;
        Variant v = c.getVariant();
        if (v == null || v == Variant.NONE) return;
        if (level.dimension().equals(dev.ahrivaea.battle.Arena.DIMENSION) || dev.ahrivaea.battle.Battles.inBattle(c)) return;
        boolean shiny = v == Variant.MUTATED;
        double range = level.getServer().getPlayerList().getViewDistance() * 16.0;
        long now = System.currentTimeMillis();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(c) > range * range) continue;
            long[] last = LAST.computeIfAbsent(p.getUUID(), k -> new long[]{0, 0});
            int i = shiny ? 0 : 1;
            if (now - last[i] < QUIET_MS) continue;
            last[i] = now;
            if (shiny) {
                sound(p, "minecraft:ui.toast.challenge_complete", 0.6f, 1.3f);
                sound(p, "minecraft:block.amethyst_block.chime", 1f, 1.6f);
                p.sendOverlayMessage(Component.literal("✦ Did you see something shiny? ✦").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            } else {
                sound(p, "minecraft:entity.ravager.roar", 0.7f, 0.7f);
                sound(p, "minecraft:ambient.cave", 0.8f, 0.8f);
                p.sendOverlayMessage(Component.literal("Ooh! They look mad!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
            }
        }
    }

    private static void sound(ServerPlayer p, String id, float volume, float pitch) {
        var s = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.parse(id));
        if (s == null) return;
        p.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(s), SoundSource.MASTER,
                p.getX(), p.getEyeY(), p.getZ(), volume, pitch, p.getRandom().nextLong()));
    }

    public static void forget(ServerPlayer p) { LAST.remove(p.getUUID()); }
}
