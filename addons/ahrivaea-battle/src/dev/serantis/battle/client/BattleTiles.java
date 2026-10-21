package dev.serantis.battle.client;

/**
 * How big creatures look in battle: a creature as big as one tile is shrunk to at most 1.3 blocks across (as
 * before), and a bigger one (2x2, 3x3, 4x4 tiles, see moves/Footprints) is sized to fill all of its tiles.
 */
public final class BattleTiles {
    /** Added to the creatures mod's render state (mixin/client/CreatureRenderStateMixin): the creature's tiles per side. */
    public interface Holder {
        int serantis$tiles();
        void serantis$setTiles(int tiles);
    }

    private BattleTiles() {}

    /** The scale for a model this many blocks across, covering this many tiles per side. */
    public static float shrink(float extent, int tiles) {
        if (!dev.ahrivaea.client.battle.BattleClient.active()) return 1f;
        if (tiles <= 1) return extent > 1.3f ? 1.3f / extent : 1f;
        return extent > 0.01f ? (tiles - 0.15f) / extent : 1f;
    }

    public static int tiles(dev.ahrivaea.creature.CreatureEntity e) {
        return dev.serantis.battle.moves.Footprints.tiles(e.getSpeciesId(), e.getInfluence().getSerializedName());
    }
}
