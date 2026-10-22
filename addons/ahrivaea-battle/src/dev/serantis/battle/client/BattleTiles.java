package dev.serantis.battle.client;

/**
 * How big creatures look in battle. Every creature is drawn a little larger than its tiles (see moves/Footprints) so
 * it spills over their edges: SPILL blocks wider than its square (a 1x1 creature up to 1.6 blocks across, a 2x2 one
 * 2.6, a 3x3 one 3.6). The summoned bosses (the End dragons, the Harbinger of Calamity, the False Hydra) spill
 * further, BOSS_SPILL. A one-tile creature smaller than that keeps its own size; a bigger body is sized to fill its
 * square. Only the drawing changes: movement, reach and the arena still go by tiles. The fit uses the model's width
 * and length, never its height, so tall creatures stay tall.
 */
public final class BattleTiles {
    public static final float SPILL = 0.6f, BOSS_SPILL = 1.4f;

    /** Added to the creatures mod's render state (mixin/client/CreatureRenderStateMixin): the creature's tiles per side. */
    public interface Holder {
        int serantis$tiles();
        void serantis$setTiles(int tiles);
    }

    private BattleTiles() {}

    /** The scale for a model this many blocks across, covering this many tiles per side (negative: a boss). */
    public static float shrink(float extent, int tiles) {
        if (!dev.ahrivaea.client.battle.BattleClient.active()) return 1f;
        boolean boss = tiles < 0;
        int n = Math.abs(tiles);
        float across = n + (boss ? BOSS_SPILL : SPILL);
        if (n <= 1 && !boss) return extent > across ? across / extent : 1f;
        return extent > 0.01f ? across / extent : 1f;
    }

    public static boolean boss(String species) {
        return dev.serantis.battle.Harbinger.SPECIES.equals(species) || dev.serantis.battle.FalseHydra.SPECIES.equals(species)
                || dev.serantis.battle.Riddles.isDragon(species);
    }

    public static int tiles(dev.ahrivaea.creature.CreatureEntity e) {
        int n = dev.serantis.battle.moves.Footprints.tiles(e.getSpeciesId(), e.getInfluence().getSerializedName());
        return boss(e.getSpeciesId()) ? -n : n;                          // negative: a boss, drawn bigger
    }
}
