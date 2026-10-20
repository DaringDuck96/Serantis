package dev.serantis.battle.fx;

/** Added to CreatureEntity: battle effects the server triggers and the client plays. */
public interface FxHolder {
    /** Server: flash this colour (one of the Fx kinds). */
    void serantis$flash(int kind);
    /** Server: wiggle (it just attacked). */
    void serantis$wiggle();

    /** Client: the flash playing now (0 for none), and the tick it started. */
    int serantis$flashKind();
    float serantis$flashStart();
    /** Client: the tick the latest wiggle started (or a large negative number). */
    float serantis$wiggleStart();
}
