package dev.serantis.battle.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The conversation camera: while someone is talking, the camera leaves first person and frames a close-up of
 * whoever is speaking (the NPC, or you when you answer), gliding between shots, then glides back when it ends.
 * Read by mixin/client/DialogueCameraMixin.
 */
public final class DialogueCamera {
    private static boolean active, returning;
    private static Entity speaker;
    private static float faceHeight;
    private static boolean onPlayer;
    private static Vec3 pos;
    private static float yaw, pitch;
    private static long last, returnStart;

    private DialogueCamera() {}

    /** Starts a conversation with this entity; faceHeight: how high its face is above its feet (blocks). */
    public static void start(Entity who, float face) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        speaker = who;
        faceHeight = face;
        onPlayer = false;
        active = true;
        returning = false;
        pos = mc.player.getEyePosition();
        yaw = mc.player.getYRot();
        pitch = mc.player.getXRot();
        last = System.nanoTime();
        // turn to face them
        Vec3 d = face(who).subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f);
        mc.player.setXRot((float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG));
        mc.player.setYHeadRot(mc.player.getYRot());
    }

    /** Who the shot is on: the speaker (false) or you (true). */
    public static void focusPlayer(boolean player) { onPlayer = player; }

    /** Ends the conversation: a quick glide back (or straight back, e.g. when a battle takes over the camera). */
    public static void end(boolean glide) {
        if (!active) return;
        if (glide) { returning = true; returnStart = System.nanoTime(); }
        else { active = false; returning = false; }
    }

    public static boolean active() { return active; }

    private static Vec3 face(Entity e) {
        return new Vec3(e.getX(), e.getY() + faceHeight, e.getZ());
    }

    /** Where the camera should be this frame: {x, y, z, yaw, pitch}, or null to leave it alone. */
    public static double[] frame(float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.player == null) return null;
        if (speaker == null || speaker.isRemoved()) { active = false; return null; }
        Vec3 me = mc.player.getEyePosition(partial);
        Vec3 them = face(speaker);
        Vec3 flat = new Vec3(me.x - them.x, 0, me.z - them.z);
        Vec3 dir = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();      // from them towards you
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        Vec3 targetPos, lookAt;
        if (returning) {
            targetPos = me;
            lookAt = me.add(mc.player.getViewVector(partial));
        } else if (onPlayer) {                                           // your close-up: just in front of your face
            targetPos = me.subtract(dir.scale(1.5)).add(side.scale(0.35)).add(0, 0.05, 0);
            lookAt = me;
        } else {                                                         // their close-up: in front of their face
            double dist = Mth.clamp(faceHeight * 0.85, 1.4, 3.2);
            targetPos = them.add(dir.scale(dist)).add(side.scale(-0.35)).add(0, 0.1, 0);
            lookAt = them;
        }
        Vec3 d = lookAt.subtract(targetPos);
        float ty = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90f;
        float tp = (float) -(Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG);

        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1.0e9);
        last = now;
        double a = 1 - Math.exp(-dt * 6.0);                              // glide
        pos = pos.add(targetPos.subtract(pos).scale(a));
        yaw += Mth.wrapDegrees(ty - yaw) * (float) a;
        pitch += (tp - pitch) * (float) a;
        if (returning && (pos.distanceTo(targetPos) < 0.05 || now - returnStart > 600_000_000L)) { active = false; returning = false; return null; }
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }
}
