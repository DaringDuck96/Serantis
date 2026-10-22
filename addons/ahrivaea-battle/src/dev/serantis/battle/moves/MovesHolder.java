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
    /** Golden apples eaten per stat, "hp,attack,defense,speed,magic,luck,charisma" (see Apples). */
    default String serantis$apples() { return null; }
    default void serantis$setApples(String apples) { }
    /** The Move Tutor's standing offer for it (move ids), or null. */
    default String serantis$tutor() { return null; }
    default void serantis$setTutor(String offer) { }
    /** Knocked out in battle: until this time (epoch ms) it can't be healed or fight (see Knockout); 0 if not. */
    default long serantis$koUntil() { return 0; }
    default void serantis$setKoUntil(long until) { }
    /** A status lingering after a battle, "status@until" (epoch ms), or null (see Ailments). */
    default String serantis$ailment() { return null; }
    default void serantis$setAilment(String ailment) { }
}
