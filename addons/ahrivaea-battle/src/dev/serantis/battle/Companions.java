package dev.serantis.battle;

import dev.ahrivaea.battle.Battles;
import dev.ahrivaea.creature.CreatureEntity;
import dev.ahrivaea.party.CaughtCreature;
import dev.ahrivaea.party.Party;
import dev.ahrivaea.party.PartySync;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A creature is never lost unless its player releases it.
 *
 * Every creature a player has sent out is remembered on the player (a copy of it, kept up to date). If it's
 * left behind (the player teleports, changes dimension, logs out, or just walks far away), it comes back to
 * the party (or the box when the party is full): straight away if it's still loaded, or from the remembered
 * copy if its chunk has unloaded; the copy left in the world then vanishes the next time it loads, so there
 * are never two. A creature that belongs to a player can't die in the world either: a blow that would finish
 * it leaves it on 1 HP, and one that's out goes back to the party to recover.
 */
public final class Companions {
    /** Further than this from its player (or in another dimension), a creature that's out comes home. */
    public static final double LEASH = 96;

    /** Added to players: their creatures that are out, and copies left behind that should vanish. */
    public interface Holder {
        Map<UUID, CaughtCreature> serantis$out();
        Set<UUID> serantis$gone();
    }

    private Companions() {}

    /** A creature that's out with its player (not working at a base, not in a battle). */
    private static ServerPlayer ownerIfOut(CreatureEntity e) {
        if (!e.isTame() || e.brain() != null || e.isPreview() || Battles.inBattle(e)) return null;
        return e.getOwner() instanceof ServerPlayer p ? p : null;
    }

    /** Every server tick of a creature. */
    public static void tick(CreatureEntity e) {
        if (!(e.level() instanceof ServerLevel level) || e.isRemoved()) return;
        if (!e.isTame()) return;
        ServerPlayer owner = e.getOwner() instanceof ServerPlayer p ? p : null;
        if (owner == null) return;
        Holder h = (Holder) owner;
        if (h.serantis$gone().remove(e.getUUID())) {         // already brought home from its copy
            e.discard();
            return;
        }
        if (ownerIfOut(e) == null) { h.serantis$out().remove(e.getUUID()); return; }
        if (!h.serantis$out().containsKey(e.getUUID()) || e.tickCount % 20 == 0) h.serantis$out().put(e.getUUID(), e.toCaught());
        if (owner.level() != level || owner.distanceToSqr(e) > LEASH * LEASH) {
            e.recallTo(owner);                                  // left behind: home it goes
            h.serantis$out().remove(e.getUUID());
        }
    }

    /** Every second for each player: creatures that are out but no longer anywhere in the world come home. */
    public static void check(ServerPlayer p) {
        Holder h = (Holder) p;
        if (h.serantis$out().isEmpty()) return;
        var server = ((ServerLevel) p.level()).getServer();
        List<String> back = new ArrayList<>();
        for (Iterator<Map.Entry<UUID, CaughtCreature>> it = h.serantis$out().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, CaughtCreature> en = it.next();
            Entity found = null;
            for (ServerLevel l : server.getAllLevels()) if ((found = l.getEntity(en.getKey())) != null) break;
            if (found != null && !found.isRemoved()) continue;
            it.remove();
            h.serantis$gone().add(en.getKey());
            Party.addAnywhere(p, en.getValue());
            back.add(dev.ahrivaea.creature.Species.get(en.getValue().species()).map(sp -> sp.nameFor(en.getValue().influence())).orElse("Your creature"));
        }
        if (!back.isEmpty()) {
            p.sendSystemMessage(Component.literal(String.join(", ", back) + (back.size() == 1 ? " was" : " were")
                    + " left behind, so " + (back.size() == 1 ? "it" : "they") + " came back to your party (or box).").withStyle(ChatFormatting.AQUA));
            PartySync.sync(p);
        }
    }

    /** A creature is gone for good (recalled, released, discarded): stop remembering it. */
    public static void forget(CreatureEntity e) {
        if (e.level().isClientSide() || !e.isTame()) return;
        if (e.getOwner() instanceof ServerPlayer p) ((Holder) p).serantis$out().remove(e.getUUID());
    }

    /** A blow that would finish a player's creature: it's left on 1 HP, and if it's out it goes home. True if it was saved. */
    public static boolean spare(CreatureEntity e) {
        if (e.level().isClientSide() || !e.isTame() || e.getOwner() == null || Battles.inBattle(e)) return false;
        e.setHealth(1f);
        ServerPlayer owner = ownerIfOut(e);
        if (owner != null) {
            owner.sendSystemMessage(Component.literal(e.displayName() + " was knocked out and went back to your party to recover.").withStyle(ChatFormatting.YELLOW));
            e.recallTo(owner);
        }
        return true;
    }
}
