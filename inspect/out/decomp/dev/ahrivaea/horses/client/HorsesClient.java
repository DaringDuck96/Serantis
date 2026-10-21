package dev.ahrivaea.horses.client;

import dev.ahrivaea.horses.HorseSpeed;
import dev.ahrivaea.horses.HorsesMod;
import dev.ahrivaea.horses.Present;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public class HorsesClient implements ClientModInitializer {
   public static final double LOOK_RANGE = 16.0;
   static double currentSpeed;
   private static Vec3 lastPos;

   public void onInitializeClient() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)mc -> {
         if (mc.player != null && mc.player.getVehicle() instanceof AbstractHorse h) {
            Vec3 now = h.position();
            double step = lastPos == null ? 0.0 : Math.hypot(now.x - lastPos.x, now.z - lastPos.z) * 20.0;
            currentSpeed = currentSpeed + (Math.min(step, 100.0) - currentSpeed) * 0.2;
            lastPos = now;
         } else {
            lastPos = null;
            currentSpeed = 0.0;
         }
      });
      HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, HorsesMod.id("horse_stats"), (g, delta) -> {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.level != null && !mc.gui.hud.isHidden() && serverHasMod()) {
            if (mc.player.getVehicle() instanceof AbstractHorse h && HorseSpeed.covers(h)) {
               HorseStats.draw(g, h, true, 6, mc.getDebugOverlay().showDebugScreen() ? g.guiHeight() / 4 : 6);
               return;
            }

            AbstractHorse looked = lookedAt(mc);
            if (looked != null) {
               HorseStats.draw(g, looked, false, g.guiWidth() / 2 + 14, g.guiHeight() / 2 - 8);
            }
         }
      });
   }

   public static boolean serverHasMod() {
      try {
         return Minecraft.getInstance().getConnection() != null && ClientPlayNetworking.canSend(Present.TYPE);
      } catch (IllegalStateException var1) {
         return false;
      }
   }

   static AbstractHorse lookedAt(Minecraft mc) {
      LocalPlayer player = mc.player;
      Vec3 eye = player.getEyePosition();
      Vec3 end = eye.add(player.getViewVector(1.0F).scale(16.0));
      EntityHitResult hit = ProjectileUtil.getEntityHitResult(
         player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0), e -> HorseSpeed.covers(e) && !e.isSpectator(), 256.0
      );
      if (hit == null) {
         return null;
      } else {
         BlockHitResult block = mc.level.clip(new ClipContext(eye, hit.getLocation(), Block.COLLIDER, Fluid.NONE, player));
         return block.getType() != Type.MISS && block.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye)
            ? null
            : (AbstractHorse)hit.getEntity();
      }
   }
}
