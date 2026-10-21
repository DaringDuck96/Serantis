package dev.serantis.battle;

import dev.ahrivaea.base.BaseCoreBlockEntity;
import dev.ahrivaea.base.Bases;
import dev.ahrivaea.base.Worker;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.villager.Profession;
import dev.ahrivaea.villager.VillagerJob;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Serantis villagers.
 *
 * Every villager in a village spawns at Level 5 with its profession already set (so recruiting one
 * gets you that profession; it can't be retrained into another). Besides the creatures mod's
 * professions there are three that work from a base when assigned there as a worker:
 *  - Bard: every creature working at that base works 25% faster;
 *  - Innkeeper: sleeping through the night refills the base owner's hearts;
 *  - Noble: creatures that spawn in or within a chunk of that base are more often Rare or rarer.
 * The Alchemist also turns golden apples into XP (20 each).
 */
public final class Villages {
    public static final int SPAWN_LEVEL = 5;
    public static final int APPLE_XP = 20;
    public static final double BARD_SPEED = 1.25;

    /** Professions villagers spawn with, and how often (out of the total). */
    private static final String[] ROLES = {
            "farmer", "fisherman", "gardener", "weaver", "hunter", "librarian", "explorer", "smith", "netherwright",
            "carpenter", "engineer", "cook", "leatherworker", "mason", "jeweler", "voidwalker", "alchemist",
            "bard", "innkeeper", "noble", "merchant"};
    private static final int[] WEIGHTS = {
            10, 8, 8, 8, 8, 8, 6, 8, 4,
            8, 6, 8, 6, 10, 4, 3, 6,
            5, 5, 3, 6};

    private Villages() {}

    public static String rollRole(RandomSource random) {
        int total = 0;
        for (int w : WEIGHTS) total += w;
        int r = random.nextInt(total);
        for (int i = 0; i < ROLES.length; i++) {
            if (r < WEIGHTS[i]) return ROLES[i];
            r -= WEIGHTS[i];
        }
        return ROLES[0];
    }

    // ------------------------------------------------------------------ the job on a creature in the world
    // (stored by the creatures mod as a Fabric attachment; reached by reflection so this add-on needs no Fabric API to build)

    private static Object jobType;
    private static Method setAttached, getAttached;

    private static boolean reflect(Object entity) {
        try {
            if (jobType == null) {
                Field f = dev.ahrivaea.villager.Villagers.class.getField("JOB");
                jobType = f.get(null);
            }
            if (setAttached == null || getAttached == null)
                for (Method m : entity.getClass().getMethods()) {
                    if (m.getName().equals("setAttached") && m.getParameterCount() == 2) setAttached = m;
                    if (m.getName().equals("getAttached") && m.getParameterCount() == 1) getAttached = m;
                }
            return jobType != null && setAttached != null && getAttached != null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    public static void setJob(CreatureEntity e, VillagerJob job) {
        if (!reflect(e)) return;
        try { setAttached.invoke(e, jobType, job); } catch (ReflectiveOperationException | RuntimeException ignored) { }
    }

    public static VillagerJob jobOf(CreatureEntity e) {
        if (e == null || !reflect(e)) return null;
        try { return getAttached.invoke(e, jobType) instanceof VillagerJob j && j.profession() != null && !j.profession().isEmpty() ? j : null; } catch (ReflectiveOperationException | RuntimeException ex) { return null; }
    }

    /** A villager that just spawned in a village gets its profession. */
    public static void assign(CreatureEntity e, RandomSource random) {
        if (e == null || jobOf(e) != null) return;
        String role = rollRole(random);
        setJob(e, new VillagerJob(role, Market.MERCHANT.equals(role) ? 1 : 0, 0));     // merchants trade from the start
    }

    public static String professionName(String id) {
        Profession p = id == null ? null : Profession.get(id);
        return p != null ? p.name() : id;
    }

    /** A recruited villager starts its first deed from now (like a freshly trained one). */
    public static CaughtCreature onRecruit(ServerPlayer player, CaughtCreature c) {
        VillagerJob job = c == null ? null : c.job();
        if (job == null || job.rank() != 0) return c;
        Profession p = Profession.get(job.profession());
        if (p == null || p.ranks().isEmpty()) return c;
        return c.withJob(new VillagerJob(job.profession(), 0, p.ranks().getFirst().deed().value(player)));
    }

    // ------------------------------------------------------------------ town guards

    public static final String GUARD = "town_guard";
    /** Share of village villagers that are Town Guards standing watch (they can't be fought). */
    public static final double GUARD_SHARE = 0.2;
    /** The next unit added to a battle is a Town Guard. */
    public static final ThreadLocal<Boolean> NEXT_GUARD = ThreadLocal.withInitial(() -> false);
    /** Town Guards are tougher than villagers (Level 20 values). */
    public static final dev.ahrivaea.creature.Stats GUARD_STATS = new dev.ahrivaea.creature.Stats(75, 70, 75, 55, 40, 50, 60);

    public static boolean isGuard(net.minecraft.world.entity.Entity e) {
        if (!(e instanceof CreatureEntity c)) return false;
        VillagerJob j = jobOf(c);
        return j != null && GUARD.equals(j.profession());
    }

    /** A villager that will call the guards when attacked. */
    public static boolean defendedByGuards(CreatureEntity e) {
        if (e == null || !dev.ahrivaea.villager.Villagers.SPECIES.equals(e.getSpeciesId())) return false;
        VillagerJob j = jobOf(e);
        return j == null || !(j.profession().startsWith(Masters.ROLE + ":") || GUARD.equals(j.profession()) || Market.BLACK_MARKET.equals(j.profession()) || Wanderer.ROLE.equals(j.profession()));
    }

    /** A Town Guard joining a battle: a villager a few levels above the one it defends. */
    public static dev.ahrivaea.creature.Species.LevelledPick guardPick(int level, RandomSource random) {
        var sp = dev.ahrivaea.creature.Species.get(dev.ahrivaea.villager.Villagers.SPECIES).orElse(null);
        return sp == null ? null : new dev.ahrivaea.creature.Species.LevelledPick(sp, dev.ahrivaea.creature.Influence.NONE,
                Math.min(20, level + 3 + random.nextInt(5)));
    }

    /** A villager spawning in a village becomes a Town Guard standing watch. */
    public static void makeGuard(CreatureEntity e) {
        setJob(e, new VillagerJob(GUARD, 1, 0));
        e.setCustomName(net.minecraft.network.chat.Component.literal("Town Guard"));
        e.setCustomNameVisible(true);
    }

    // ------------------------------------------------------------------ bases

    public static boolean has(BaseCoreBlockEntity core, String role) {
        if (core == null) return false;
        for (Worker w : core.workers()) {
            CaughtCreature c = w.creature();
            if (c != null && c.job() != null && role.equals(c.job().profession())) return true;
        }
        return false;
    }

    /** Does the player own a base (in any dimension) with a worker of this profession? */
    public static boolean ownsBaseWith(MinecraftServer server, UUID owner, String role) {
        for (ServerLevel level : server.getAllLevels())
            for (BlockPos pos : Bases.cores(level)) {
                BaseCoreBlockEntity core = Bases.coreAt(level, pos);
                if (core != null && owner.equals(core.owner()) && has(core, role)) return true;
            }
        return false;
    }

    /** Is this spot inside, or within a chunk of, a base with a Noble at work? */
    public static boolean nobleNear(ServerLevel level, BlockPos pos) {
        for (BlockPos p : Bases.cores(level)) {
            BaseCoreBlockEntity core = Bases.coreAt(level, p);
            if (core == null) continue;
            if (pos.getX() >= core.minX() - 16 && pos.getX() <= core.maxX() + 16 && pos.getZ() >= core.minZ() - 16 && pos.getZ() <= core.maxZ() + 16
                    && has(core, "noble")) return true;
        }
        return false;
    }

    /** Set where the next creature spawn happens (read once by SpawnOdds). */
    public static final ThreadLocal<Boolean> NOBLE_SPAWN = ThreadLocal.withInitial(() -> false);

    public static void markSpawn(ServerLevel level, BlockPos pos) {
        boolean noble = false;
        try { noble = level != null && pos != null && nobleNear(level, pos); } catch (RuntimeException ignored) { }
        NOBLE_SPAWN.set(noble);
        try { Masters.markSpawn(level, pos); } catch (RuntimeException ignored) { }
    }

    // ------------------------------------------------------------------ the Alchemist

    private static boolean hasAlchemist(ServerPlayer p) {
        for (CaughtCreature c : Party.get(p)) if (c.job() != null && "alchemist".equals(c.job().profession())) return true;
        for (CaughtCreature c : Party.box(p)) if (c.job() != null && "alchemist".equals(c.job().profession())) return true;
        return false;
    }

    /** Trades one golden apple for XP. */
    public static void appleForXp(ServerPlayer p) {
        if (!hasAlchemist(p)) { tell(p, "You need an Alchemist for that."); return; }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var s = inv.getItem(i);
            if (!s.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) continue;
            s.shrink(1);
            inv.setChanged();
            Achievements.NOT_GAINED.set(true);            // a trade, not XP earned
            try { p.giveExperiencePoints(APPLE_XP); } finally { Achievements.NOT_GAINED.set(false); }
            tell(p, "The Alchemist turned a golden apple into " + APPLE_XP + " XP.");
            return;
        }
        tell(p, "You need a golden apple.");
    }

    private static void tell(ServerPlayer p, String msg) {
        p.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
    }
}
