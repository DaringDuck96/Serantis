package dev.serantis.battle.client;

import java.util.ArrayList;
import java.util.List;

/**
 * Battle-screen juice: words that pop up over units (CRITICAL!, MISS, Super effective!, KO!) and a short camera
 * nudge on crits and knockouts. The battle camera reads its position through BattleClient.camX/Y/Z, which
 * BattleCameraMixin offsets by {@link #dx()}, {@link #dy()}, {@link #dz()}.
 */
public final class BattleFeel {
    /** A word over a grid cell: {cell, kind, start millis}. */
    public static final List<long[]> LABELS = new ArrayList<>();
    private static long shakeStart = -1;
    private static double shakePower;
    private static boolean dip;
    private static final long SHAKE_MS = 320, LABEL_MS = 1100;

    private BattleFeel() {}

    public static void crit() { shake(0.18, false); }

    public static void knockout() { shake(0.12, true); }

    private static void shake(double power, boolean ko) {
        long now = System.currentTimeMillis();
        if (shakeStart >= 0 && now - shakeStart < SHAKE_MS && shakePower > power) return;
        shakeStart = now;
        shakePower = power;
        dip = ko;
    }

    private static double t() {
        if (shakeStart < 0) return -1;
        double t = (System.currentTimeMillis() - shakeStart) / (double) SHAKE_MS;
        if (t >= 1) { shakeStart = -1; return -1; }
        return t;
    }

    public static double dx() { double t = t(); return t < 0 ? 0 : Math.sin(t * Math.PI * 7) * shakePower * (1 - t); }

    public static double dz() { double t = t(); return t < 0 ? 0 : Math.cos(t * Math.PI * 5) * shakePower * 0.6 * (1 - t); }

    /** A knockout dips the camera toward the field and back. */
    public static double dy() { double t = t(); return t < 0 || !dip ? 0 : -Math.sin(t * Math.PI) * 0.9; }

    public static void label(long cell, int kind) {
        LABELS.add(new long[]{cell, kind, System.currentTimeMillis()});
    }

    public static void prune() {
        long now = System.currentTimeMillis();
        LABELS.removeIf(l -> now - l[2] > LABEL_MS);
    }

    public static float age(long[] l) { return (System.currentTimeMillis() - l[2]) / (float) LABEL_MS; }

    public static void reset() { LABELS.clear(); shakeStart = -1; }
}
