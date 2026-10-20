package dev.serantis.battle.moves;

/** Something that remembers a creature's three moves (caught creatures and creatures in the world). */
public interface MovesHolder {
    String serantis$moves();
    void serantis$setMoves(String moves);
    /** Its temperament (see Temperament). */
    String serantis$temperament();
    void serantis$setTemperament(String temperament);
    /** The name its player gave it, or null. */
    String serantis$nickname();
    void serantis$setNickname(String nickname);
}
