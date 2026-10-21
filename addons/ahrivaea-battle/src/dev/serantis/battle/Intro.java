package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.Ahrivaea;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.creature.Influence;
import dev.ahrivaea.creature.Species;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import dev.ahrivaea.villager.VillagerJob;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The opening scene, before anything else: a new player wakes at spawn next to a Mysterious Figure, who
 * talks with them (client/IntroScreen), asks who they are (class) and who's with them (starter), then
 * vanishes after a roar in the distance. The welcome guide comes after.
 *
 * A player needs the intro when they have no class, no creatures and haven't read the guide. The figure is a
 * villager with the "mysterious_figure" role, only there for the scene.
 */
public final class Intro {
    public static final String ROLE = "mysterious_figure";
    public static final String TAG = "serantis_figure";

    /** Players in the scene, and their figure. */
    private static final Map<UUID, UUID> ACTIVE = new ConcurrentHashMap<>();

    private Intro() {}

    public static boolean isFigure(Entity e) {
        return e instanceof CreatureEntity && e.entityTags().contains(TAG);
    }

    private static boolean needsIntro(ServerPlayer p) {
        return !((Welcome.Holder) p).serantis$welcomed() && PlayerClass.of(p) == null
                && Party.get(p).isEmpty() && Party.box(p).isEmpty();
    }

    public static boolean active(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    /**
     * Each player's Acolyte is theirs alone: only the player whose scene it is can see it (and its smoke and
     * sounds), so on a shared server nobody sees anyone else's cutscene and everyone gets their own.
     */
    public static boolean visibleTo(Entity figure, ServerPlayer viewer) {
        UUID owner = SPAWNING_FOR.get();
        if (owner == null) for (var en : ACTIVE.entrySet()) if (figure.getUUID().equals(en.getValue())) owner = en.getKey();
        if (owner == null) owner = FigureVisit.ownerOf(figure);
        return owner == null || owner.equals(viewer.getUUID());
    }

    static void particles(ServerPlayer p, net.minecraft.core.particles.ParticleOptions type, double x, double y, double z, int n,
                          double dx, double dy, double dz, double speed) {
        ((ServerLevel) p.level()).sendParticles(p, type, true, false, x, y, z, n, dx, dy, dz, speed);
    }

    static void sound(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> sound, double x, double y, double z, float volume, float pitch) {
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(sound, net.minecraft.sounds.SoundSource.HOSTILE,
                x, y, z, volume, pitch, p.getRandom().nextLong()));
    }

    /** The scene is on, or about to start: the welcome guide waits. */
    public static boolean pending(ServerPlayer p) { return active(p) || needsIntro(p); }

    /** Every second: start the scene for a new player, keep its figure there, and clear away stray figures. */
    public static void tick(ServerPlayer p) {
        ServerLevel level = (ServerLevel) p.level();
        UUID figure = ACTIVE.get(p.getUUID());
        if (figure == null && p.tickCount >= 30 && needsIntro(p)) {
            ACTIVE.put(p.getUUID(), new UUID(0, 0));
            figure = ACTIVE.get(p.getUUID());
        }
        if (figure != null) {
            Entity e = level.getEntity(figure);
            if (e == null || !e.isAlive() || e.distanceToSqr(p) > 12 * 12) {
                if (e != null) e.discard();
                CreatureEntity f = spawn(level, p);
                if (f != null) { ACTIVE.put(p.getUUID(), f.getUUID()); PartySync.sync(p); }
            }
        }
        for (CreatureEntity c : level.getEntitiesOfClass(CreatureEntity.class, p.getBoundingBox().inflate(48), Intro::isFigure))
            if (!ACTIVE.containsValue(c.getUUID()) && !FigureVisit.owns(c)) c.discard();
    }

    static CreatureEntity spawn(ServerLevel level, ServerPlayer p) {
        Species villager = Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        if (villager == null) return null;
        var look = p.getLookAngle();
        double dx = look.x, dz = look.z, len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) { dx = 0; dz = 1; len = 1; }
        dx /= len; dz /= len;
        double x = p.getX() + dx * 2.5, z = p.getZ() + dz * 2.5;
        CreatureEntity f = figureAt(level, x, p.getY(), z, ROLE, p);
        if (f == null) return null;
        float yaw = (float) Math.toDegrees(Math.atan2(p.getZ() - z, p.getX() - x)) - 90f;
        f.setYRot(yaw);
        f.setYHeadRot(yaw);
        f.setYBodyRot(yaw);
        return f;
    }

    /** A Mysterious Figure standing here (no AI, can't be hurt or battled). */
    /** While an Acolyte is being spawned: whose scene it's for (so nobody else is ever sent it, even for a tick). */
    public static final ThreadLocal<UUID> SPAWNING_FOR = new ThreadLocal<>();

    static CreatureEntity figureAt(ServerLevel level, double x, double y, double z, String role, ServerPlayer owner) {
        SPAWNING_FOR.set(owner.getUUID());
        try { return figureAt(level, x, y, z, role); } finally { SPAWNING_FOR.remove(); }
    }

    /** An Acolyte (role: the First's or the Last's) standing here (no AI, can't be hurt or battled). */
    static CreatureEntity figureAt(ServerLevel level, double x, double y, double z, String role) {
        Species villager = Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        if (villager == null) return null;
        CreatureEntity f = Ahrivaea.spawn(level, villager, Influence.NONE, 20, -1f, x, y, z);
        if (f == null) return null;
        Villages.setJob(f, new VillagerJob(role, 1, 0));
        f.addTag(TAG);
        f.setNoAi(true);
        f.setCustomName(ROLE.equals(role) ? Component.literal("Mysterious Figure").withStyle(ChatFormatting.DARK_PURPLE)
                : Component.literal(FigureVisit.LAST_TITLE).withStyle(ChatFormatting.DARK_RED));
        f.setCustomNameVisible(false);
        return f;
    }

    /** The scene ended: the figure is gone. */
    public static void done(ServerPlayer p) {
        UUID figure = ACTIVE.remove(p.getUUID());
        if (figure != null) {
            Entity e = ((ServerLevel) p.level()).getEntity(figure);
            if (e != null) e.discard();
            p.sendSystemMessage(net.minecraft.network.chat.Component.literal("Tip: hit a wild creature with your weapon to start your first battle. The battle screen will walk you through it.")
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
        }
        PartySync.sync(p);
    }

    /** "serantis_intro" in the party state while the scene is on: the figure's entity id. */
    public static void describe(ServerPlayer p, JsonObject state) {
        UUID figure = ACTIVE.get(p.getUUID());
        if (figure == null && !needsIntro(p)) return;
        Entity e = figure == null ? null : ((ServerLevel) p.level()).getEntity(figure);
        JsonObject o = new JsonObject();
        o.addProperty("entity", e == null ? -1 : e.getId());
        state.add("serantis_intro", o);
    }
}
