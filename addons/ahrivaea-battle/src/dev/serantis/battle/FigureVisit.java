package dev.serantis.battle;

import com.google.gson.JsonObject;
import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.PartySync;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Mysterious Figure's visits after the opening scene (serantis_battle/figure_scenes.json): at milestones it
 * walks up to you wherever you are (you can't move while it does; client/FigureScreen), talks, and vanishes.
 * Each plays once: after 10 battles, a base at Level 2, the first Gym Master Stone, the first tower rebuilt,
 * the first steps in the Nether, three towers rebuilt. At most one every 5 minutes.
 */
public final class FigureVisit {
    public static final int BATTLES = 10;
    private static final double STEP = 0.11, STOP = 2.2;

    /** The scenes, by id: their flag bits. */
    private static java.util.Map<String, Integer> scenes;
    private static final java.util.Map<UUID, Long> LAST_SCENE = new ConcurrentHashMap<>();
    private static final long GAP = 20 * 60 * 5;

    /** Who each scene is with ("first" or "last"), and the scene that has to come before it. */
    private static final java.util.Map<String, String> NPC = new java.util.HashMap<>(), AFTER = new java.util.HashMap<>();

    private static synchronized java.util.Map<String, Integer> scenes() {
        if (scenes == null) {
            java.util.Map<String, Integer> out = new java.util.LinkedHashMap<>();
            try (var in = FigureVisit.class.getResourceAsStream("/serantis_battle/figure_scenes.json")) {
                if (in != null) for (var e : com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject().getAsJsonArray("scenes")) {
                    var o = e.getAsJsonObject();
                    out.put(o.get("id").getAsString(), o.get("bit").getAsInt());
                    NPC.put(o.get("id").getAsString(), o.has("npc") ? o.get("npc").getAsString() : "first");
                    if (o.has("after")) AFTER.put(o.get("id").getAsString(), o.get("after").getAsString());
                }
            } catch (Exception ignored) { }
            scenes = out;
        }
        return scenes;
    }

    /** A milestone met for this scene? */
    private static boolean reached(ServerPlayer p, String id) {
        String after = AFTER.get(id);
        if (after != null && (((Welcome.Holder) p).serantis$figureMet() & scenes().getOrDefault(after, 0)) == 0) return false;
        return switch (id) {
            case "last1" -> firstMeetings(p) >= 3;
            case "last2" -> ownsBase(p, 3);
            case "last3" -> dev.ahrivaea.party.Dex.count(p, dev.ahrivaea.party.Dex.CAUGHT) >= 400;
            case "battles" -> ((Welcome.Holder) p).serantis$battlesFought() >= BATTLES;
            case "base" -> ownsBase(p, 2);
            case "stone" -> stones(p) >= 1;
            case "tower" -> Achievements.value(p, "towers") >= 1;
            case "towers" -> Achievements.value(p, "towers") >= 3;
            case "eternita" -> ((Masters.Holder) p).serantis$eternita();
            case "nether" -> p.level().dimension() == net.minecraft.world.level.Level.NETHER;
            default -> false;
        };
    }

    /** How many times you've met the Acolyte of the First (the opening scene counts). */
    private static int firstMeetings(ServerPlayer p) {
        int met = ((Welcome.Holder) p).serantis$figureMet(), n = PlayerClass.of(p) != null ? 1 : 0;
        for (var e : scenes().entrySet()) if ("first".equals(NPC.get(e.getKey())) && (met & e.getValue()) != 0) n++;
        return n;
    }

    private static int stones(ServerPlayer p) {
        int n = 0;
        for (String inf : Masters.INFLUENCES) n += ((Masters.Holder) p).serantis$stones(inf);
        return n;
    }

    /** A base of theirs (loaded right now) at this level or higher. */
    private static boolean ownsBase(ServerPlayer p, int lvl) {
        for (ServerLevel level : p.level().getServer().getAllLevels())
            for (BlockPos pos : dev.ahrivaea.base.Bases.cores(level)) {
                if (!level.isLoaded(pos)) continue;
                var core = dev.ahrivaea.base.Bases.coreAt(level, pos);
                if (core != null && p.getUUID().equals(core.owner()) && core.baseLevel() >= lvl) return true;
            }
        return false;
    }

    /** Set once you've asked who it is (the 10-battle visit): from then on it's the Acolyte of the First. */
    public static final int NAMED = 256;
    /** Set once you've recruited it (its dex page shows as caught). */
    public static final int RECRUITED = 512;
    public static final String TITLE = "Mysterious Figure", REVEALED = "Acolyte of the First";

    public static String name(ServerPlayer p) {
        return p != null && (((Welcome.Holder) p).serantis$figureMet() & NAMED) != 0 ? REVEALED : TITLE;
    }

    /** The Acolyte of the Last: recruited, beaten in his trial, fought in his trial. */
    public static final int LAST_RECRUITED = 16384, CHAOS_WON = 8192, CHAOS_FOUGHT = 32768;
    public static final String LAST_NAME = "Acolyte of the Last", LAST_ROLE = "acolyte_of_the_last";

    public static final String LAST_TITLE = "Spooky Mysterious Figure";
    /** The Acolyte of the Last's second visit (after it, you know his name). */
    public static final int LAST_KNOWN = 2048;

    public static String nameOf(ServerPlayer p, String npc) {
        if (!"last".equals(npc)) return name(p);
        return p != null && (((Welcome.Holder) p).serantis$figureMet() & LAST_KNOWN) != 0 ? LAST_NAME : LAST_TITLE;
    }

    /** Extra chance (in %) that a creature spawning near this player is Enraged: the Chaos Seeker achievement. */
    public static int enragedBonus(net.minecraft.world.entity.player.Player p) {
        if (!(p instanceof Welcome.Holder h)) return 0;
        int met = h.serantis$figureMet();
        return (met & CHAOS_WON) != 0 ? 2 : (met & CHAOS_FOUGHT) != 0 ? 1 : 0;
    }

    private static final class Visit {
        String scene, npc;
        Vec3 spot;                  // the Acolyte of the Last: where the shadows gather before he steps out
        UUID figure;
        boolean arrived;
        int ticks;
    }

    private static final Map<UUID, Visit> ACTIVE = new ConcurrentHashMap<>();
    /** When each player's last battle ended (game time), so the visit waits a moment after it. */
    private static final Map<UUID, Long> LAST_BATTLE = new ConcurrentHashMap<>();

    private FigureVisit() {}

    public static boolean owns(Entity e) { return ownerOf(e) != null; }

    /** The player whose visit this Acolyte is (null: none). */
    public static UUID ownerOf(Entity e) {
        for (var en : ACTIVE.entrySet()) if (e.getUUID().equals(en.getValue().figure)) return en.getKey();
        return null;
    }

    /** A battle ended (won or lost): count it. */
    public static void battleOver(ServerPlayer p) {
        if (p == null) return;
        Welcome.Holder h = (Welcome.Holder) p;
        seed(p);
        h.serantis$setBattlesFought(h.serantis$battlesFought() + 1);
        LAST_BATTLE.put(p.getUUID(), p.level().getGameTime());
    }

    /** Players from before the count existed start from their battles won. */
    private static void seed(ServerPlayer p) {
        Welcome.Holder h = (Welcome.Holder) p;
        if (h.serantis$battlesFought() < 0)
            h.serantis$setBattlesFought(p.getStats().getValue(net.minecraft.stats.Stats.CUSTOM, dev.ahrivaea.villager.Villagers.BATTLES_WON));
    }

    /** Every tick, per player. */
    public static void tick(ServerPlayer p) {
        Visit v = ACTIVE.get(p.getUUID());
        if (v == null) {
            if (p.tickCount % 20 != 13) return;
            Welcome.Holder h = (Welcome.Holder) p;
            seed(p);
            if (Intro.pending(p) || Battles.of(p) != null || p.isSpectator()) return;
            long now = p.level().getGameTime();
            if (now - LAST_BATTLE.getOrDefault(p.getUUID(), -1000L) < 60 || now - LAST_SCENE.getOrDefault(p.getUUID(), -GAP) < GAP) return;
            if (!p.onGround() || p.isPassenger() || p.isSleeping()) return;
            for (var e : scenes().entrySet())
                if ((h.serantis$figureMet() & e.getValue()) == 0 && reached(p, e.getKey())) { start(p, e.getKey()); return; }
            return;
        }
        ServerLevel level = (ServerLevel) p.level();
        if (v.figure == null) { gather(p, level, v); return; }
        Entity e = level.getEntity(v.figure);
        if (e == null || !e.isAlive()) {
            ACTIVE.remove(p.getUUID());
            if ("last".equals(v.npc)) p.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
            PartySync.sync(p);
            return;
        }
        v.ticks++;
        Vec3 to = p.position().subtract(e.position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        float yaw = (float) Math.toDegrees(Math.atan2(to.z, to.x)) - 90f;
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        if (e instanceof CreatureEntity c) c.setYBodyRot(yaw);
        if (!v.arrived) {
            if (flat <= STOP || v.ticks > 20 * 20) {
                v.arrived = true;
                PartySync.sync(p);
            } else {
                double step = Math.min(STEP, flat - STOP);
                double ny = e.getY() + Math.max(-0.3, Math.min(0.3, p.getY() - e.getY()));
                e.snapTo(e.getX() + to.x / flat * step, ny, e.getZ() + to.z / flat * step);
            }
        }
        if (v.ticks > 20 * 120) done(p);                  // never left hanging (and the dark lifts)
    }

    private static void start(ServerPlayer p, String scene) {
        ServerLevel level = (ServerLevel) p.level();
        // it comes from the first direction with open ground ~9 blocks away, else from close by
        Vec3 from = null;
        for (int i = 0; i < 8 && from == null; i++) {
            double a = (p.getYRot() + 90 + i * 45) * Math.PI / 180.0;
            for (double d = 9; d >= 4 && from == null; d -= 1) {
                Vec3 c = p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                if (clearPath(level, p.position(), c)) from = c;
            }
        }
        if (from == null) from = p.position().add(p.getLookAngle().x * 3, 0, p.getLookAngle().z * 3);
        String npc = NPC.getOrDefault(scene, "first");
        if ("last".equals(npc)) {                       // he doesn't walk up: the world darkens and he steps out of the shadows
            Vec3 near = null;
            for (int i = 0; i < 8 && near == null; i++) {
                double a = (p.getYRot() + 90 + i * 45) * Math.PI / 180.0;
                Vec3 c = p.position().add(Math.cos(a) * 3.5, 0, Math.sin(a) * 3.5);
                if (clearPath(level, p.position(), c)) near = c;
            }
            Visit v = new Visit();
            v.scene = scene;
            v.npc = npc;
            v.spot = near != null ? near : from;
            LAST_SCENE.put(p.getUUID(), p.level().getGameTime());
            ACTIVE.put(p.getUUID(), v);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 20 * 150, 0, false, false));
            return;
        }
        CreatureEntity f = Intro.figureAt(level, from.x, from.y, from.z, "last".equals(npc) ? LAST_ROLE : Intro.ROLE);
        if (f == null) return;
        Visit v = new Visit();
        v.scene = scene;
        v.npc = npc;
        v.figure = f.getUUID();
        LAST_SCENE.put(p.getUUID(), p.level().getGameTime());
        ACTIVE.put(p.getUUID(), v);
        PartySync.sync(p);
    }

    /** The shadows gather for about two seconds (black smoke and souls, a heartbeat), then he steps out of them. */
    private static void gather(ServerPlayer p, ServerLevel level, Visit v) {
        v.ticks++;
        Vec3 s = v.spot;
        if (v.ticks % 2 == 0) {
            Intro.particles(p, ParticleTypes.SQUID_INK, s.x, s.y + 1, s.z, 12, 0.25, 0.7, 0.25, 0.01);
            Intro.particles(p, ParticleTypes.LARGE_SMOKE, s.x, s.y + 0.8, s.z, 6, 0.3, 0.6, 0.3, 0.005);
            Intro.particles(p, ParticleTypes.SOUL, s.x, s.y + 0.4, s.z, 2, 0.4, 0.3, 0.4, 0.02);
        }
        if (v.ticks % 18 == 1) Intro.sound(p, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT), s.x, s.y, s.z, 1.4f, 0.8f);
        if (v.ticks < 40) return;
        CreatureEntity f = Intro.figureAt(level, s.x, s.y, s.z, LAST_ROLE);
        if (f == null) { ACTIVE.remove(p.getUUID()); p.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS); return; }
        Intro.particles(p, ParticleTypes.SQUID_INK, s.x, s.y + 1, s.z, 60, 0.4, 0.9, 0.4, 0.05);
        Intro.particles(p, ParticleTypes.SOUL, s.x, s.y + 1, s.z, 20, 0.5, 0.8, 0.5, 0.04);
        Intro.sound(p, SoundEvents.SOUL_ESCAPE, s.x, s.y, s.z, 1.5f, 0.6f);
        v.figure = f.getUUID();
        v.ticks = 0;
        PartySync.sync(p);
    }

    /** Open ground all the way (feet and head clear, something to stand on). */
    private static boolean clearPath(ServerLevel level, Vec3 a, Vec3 b) {
        int n = (int) Math.ceil(a.distanceTo(b));
        for (int i = 1; i <= n; i++) {
            Vec3 c = a.lerp(b, i / (double) n);
            BlockPos at = BlockPos.containing(c);
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) return false;
            if (!level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty()) return false;
            if (level.getBlockState(at.below()).getCollisionShape(level, at.below()).isEmpty()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ the trial

    /** Set while the Mysterious Figure's battle is being started (the battle code reads it). */
    public static final ThreadLocal<Boolean> FIGHTING = ThreadLocal.withInitial(() -> false);
    private static final Map<dev.ahrivaea.battle.Battle, String> TRIALS = java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());

    public static boolean isTrial(dev.ahrivaea.battle.Battle b) { return b != null && TRIALS.containsKey(b); }

    /** The talk ended with "prove it": the whole party against the figure. */
    public static void fight(ServerPlayer p) {
        Visit v = ACTIVE.remove(p.getUUID());
        if (v == null) return;
        Welcome.Holder h = (Welcome.Holder) p;
        h.serantis$setFigureMet(h.serantis$figureMet() | scenes().getOrDefault(v.scene, 0));
        ServerLevel level = (ServerLevel) p.level();
        Entity e = level.getEntity(v.figure);
        dev.ahrivaea.battle.Battle b = null;
        if (e instanceof CreatureEntity c && Battles.of(p) == null) {
            FIGHTING.set(true);
            try { b = Battles.start(p, c); } finally { FIGHTING.set(false); }
        }
        if (b == null) { if (e != null) e.discard(); PartySync.sync(p); return; }
        TRIALS.put(b, v.npc);
        if ("last".equals(v.npc)) {
            p.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
            p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DARKNESS, 20 * 6, 0, false, false));
        }
        fillParty(p, b);
        dev.serantis.battle.moves.MoveEngine.figureBoss(b, nameOf(p, v.npc));
        Battles.send(b);
        PartySync.sync(p);
    }

    /** Every party creature fights (a battle usually takes four). */
    private static void fillParty(ServerPlayer p, dev.ahrivaea.battle.Battle b) {
        var me = b.playerUnit();
        if (me == null) return;
        java.util.Set<Integer> in = new java.util.HashSet<>();
        for (var u : b.units) if (u.side == dev.ahrivaea.battle.Unit.Side.PLAYER && !u.isPlayer) in.add(u.partySlot);
        var acc = (dev.serantis.battle.mixin.BattleRaidAccess) (Object) b;
        var party = dev.ahrivaea.party.Party.get(p);
        for (int i = 0; i < party.size(); i++) {
            var c = party.get(i);
            if (in.contains(i) || c == null || c.health() <= 0) continue;
            var sp = dev.ahrivaea.creature.Species.get(c.species()).orElse(null);
            if (sp == null) continue;
            int[] cell = acc.serantis$freeCellNear(me.x, me.z, dev.ahrivaea.battle.Unit.Side.PLAYER);
            if (cell == null) break;
            var u = acc.serantis$addCreature(dev.ahrivaea.battle.Unit.Side.PLAYER, sp, c.influence(), c.variant(), c.level(), c.health(), i, cell[0], cell[1]);
            if (u == null) continue;
            BlockPos at = b.cellPos(u);
            CreatureEntity e = dev.ahrivaea.Ahrivaea.spawn(b.level, sp, c.influence(), c.level(), c.health(), at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            if (e == null) continue;
            e.setVariant(c.variant());
            e.tame(p);
            e.setNoAi(true);
            e.capSize(1.4f);
            u.entity = e.getUUID();
        }
    }

    /** The trial ended. Win: the figure can be recruited. Lose: you keep the stone, but not the figure. */
    public static void finished(dev.ahrivaea.battle.Battle b, dev.ahrivaea.battle.Battle.Outcome outcome) {
        String npc = TRIALS.remove(b);
        if (npc == null) return;
        clearWild(b);
        boolean won = outcome == dev.ahrivaea.battle.Battle.Outcome.WON;
        if (won) WON.put(b, npc);
        ServerPlayer p = b.player();
        if (p == null) return;
        if ("last".equals(npc)) {
            Welcome.Holder h = (Welcome.Holder) p;
            h.serantis$setFigureMet(h.serantis$figureMet() | CHAOS_FOUGHT | (won ? CHAOS_WON : 0));
            if (won) say(p, "\"Impossible...\" The Acolyte of the Last drops to one knee. \"Fine. The chaos chose you, not me. Take me, if you dare.\"");
            else say(p, "\"Pathetic.\" The Acolyte of the Last fades into the shadows, laughing. \"Keep your little creatures. The chaos is coming for them anyway.\"");
            return;
        }
        if (won)
            say(p, "\"You are worthy. More than I dared hope.\" The " + name(p) + " kneels. \"If you'll have me... I'll follow you.\"");
        else say(p, "\"Not yet.\" The " + name(p) + " turns away. \"Keep the stone. You'll need it. But you aren't ready for me.\" And it's gone.");
    }

    /** No wild creature comes back into the world after this battle. */
    public static void clearWild(dev.ahrivaea.battle.Battle b) {
        try {
            var f = dev.ahrivaea.battle.Battle.class.getDeclaredField("wild");
            f.setAccessible(true);
            f.set(b, null);
        } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    private static void say(ServerPlayer p, String text) {
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(text).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
    }

    /** Level 25 against Level 20: how much stronger every stat is (the level curve carried on past 20). */
    public static final double LEVEL_25 = (0.5 + 0.5 * 24 / 19.0) / 1.0;

    private static dev.ahrivaea.creature.Stats legend;

    /** The average Level 20 stats of the Legendary creatures: what the Mysterious Figure is built on. */
    public static synchronized dev.ahrivaea.creature.Stats legendStats() {
        if (legend == null) {
            double[] sum = new double[7];
            int n = 0;
            for (var sp : dev.ahrivaea.creature.Species.all())
                for (var inf : sp.forms().keySet()) {
                    if (!"Legendary".equals(dev.ahrivaea.party.RecruitRules.rarity(sp, inf))) continue;
                    var s = sp.statsFor(inf);
                    int[] v = {s.hp(), s.attack(), s.defense(), s.speed(), s.magic(), s.luck(), s.charisma()};
                    for (int i = 0; i < 7; i++) sum[i] += v[i];
                    n++;
                }
            if (n == 0) return new dev.ahrivaea.creature.Stats(85, 85, 85, 85, 85, 85, 85);
            int[] a = new int[7];
            for (int i = 0; i < 7; i++) a[i] = (int) Math.round(sum[i] / n);
            legend = new dev.ahrivaea.creature.Stats(a[0], a[1], a[2], a[3], a[4], a[5], a[6]);
        }
        return legend;
    }

    /** A recruited Acolyte, of the First or of the Last (a villager with that role). */
    public static boolean isFigure(dev.ahrivaea.party.CaughtCreature c) {
        return c != null && c.job() != null && (Intro.ROLE.equals(c.job().profession()) || LAST_ROLE.equals(c.job().profession()));
    }

    /** A recruited Mysterious Figure: Level 10, with a standard Legendary's stats and fresh moves. */
    public static dev.ahrivaea.party.CaughtCreature recruited(dev.ahrivaea.battle.Battle b, dev.ahrivaea.party.CaughtCreature c) {
        String npc = b == null ? null : WON.get(b);
        if (npc == null || c == null) return c;
        if (b.player() != null) {
            Welcome.Holder h = (Welcome.Holder) b.player();
            h.serantis$setFigureMet(h.serantis$figureMet() | ("last".equals(npc) ? LAST_RECRUITED : RECRUITED));
        }
        var sp = dev.ahrivaea.creature.Species.get(c.species()).orElse(null);
        float hp = legendStats().maxHealth(10);
        c = c.withProgress(10, 0).withHealth(hp);
        var moves = dev.serantis.battle.moves.MoveBook.rollAny(net.minecraft.util.RandomSource.create(), 6);   // six fresh moves, any influence
        ((dev.serantis.battle.moves.MovesHolder) (Object) c).serantis$setMoves(moves.isEmpty() ? null : dev.serantis.battle.moves.MoveBook.encode(moves));
        ((dev.serantis.battle.moves.MovesHolder) (Object) c).serantis$setNickname(nameOf(b.player(), npc));
        return c;
    }

    private static final Map<dev.ahrivaea.battle.Battle, String> WON = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** The talk is over: it vanishes in a puff of smoke. */
    public static void done(ServerPlayer p, boolean named) {
        if (named && ACTIVE.containsKey(p.getUUID())) {
            Welcome.Holder h = (Welcome.Holder) p;
            h.serantis$setFigureMet(h.serantis$figureMet() | NAMED);
        }
        done(p);
    }

    public static void done(ServerPlayer p) {
        Visit v = ACTIVE.remove(p.getUUID());
        if (v == null) return;
        Welcome.Holder h = (Welcome.Holder) p;
        h.serantis$setFigureMet(h.serantis$figureMet() | scenes().getOrDefault(v.scene, 0));
        ServerLevel level = (ServerLevel) p.level();
        Entity e = level.getEntity(v.figure);
        if (e != null && "last".equals(v.npc)) {           // he sinks back into the shadows, and the dark lifts
            Intro.particles(p, ParticleTypes.SQUID_INK, e.getX(), e.getY() + 1, e.getZ(), 60, 0.4, 0.9, 0.4, 0.05);
            Intro.particles(p, ParticleTypes.SOUL, e.getX(), e.getY() + 0.6, e.getZ(), 25, 0.5, 0.6, 0.5, 0.03);
            Intro.sound(p, SoundEvents.SOUL_ESCAPE, e.getX(), e.getY(), e.getZ(), 1.5f, 0.5f);
            e.discard();
            p.removeEffect(net.minecraft.world.effect.MobEffects.DARKNESS);
        } else if (e != null) {
            Intro.particles(p, ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 30, 0.3, 0.6, 0.3, 0.02);
            Intro.particles(p, ParticleTypes.PORTAL, e.getX(), e.getY() + 1, e.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
            Intro.sound(p, net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.ILLUSIONER_MIRROR_MOVE), e.getX(), e.getY(), e.getZ(), 1f, 0.8f);
            e.discard();
        }
        PartySync.sync(p);
    }

    /** "serantis_visit" in the party state during the visit: the figure's entity id, and whether it's arrived. */
    public static void describe(ServerPlayer p, JsonObject state) {
        Visit v = ACTIVE.get(p.getUUID());
        if (v == null) return;
        Entity e = ((ServerLevel) p.level()).getEntity(v.figure);
        JsonObject o = new JsonObject();
        o.addProperty("entity", e == null ? -1 : e.getId());
        o.addProperty("arrived", v.arrived);
        o.addProperty("scene", v.scene);
        o.addProperty("name", nameOf(p, v.npc));
        o.addProperty("npc", v.npc);
        state.add("serantis_visit", o);
    }
}
