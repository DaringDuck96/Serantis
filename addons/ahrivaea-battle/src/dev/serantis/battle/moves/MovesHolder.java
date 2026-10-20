package dev.serantis.battle.moves;

/** Something that remembers a creature's three moves (caught creatures and creatures in the world). */
public interface MovesHolder {
    String serantis$moves();
    void serantis$setMoves(String moves);
}
