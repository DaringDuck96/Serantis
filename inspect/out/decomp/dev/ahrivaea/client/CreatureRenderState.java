package dev.ahrivaea.client;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

public class CreatureRenderState extends LivingEntityRenderState {
   public Identifier texture;
   public boolean sitting;
   public int tint = -1;
   public GeoCreatureModel geo;
   public float modelScale = 1.0F;
   public float extent = 1.0F;
   public boolean realSize;
   public boolean villager;
   public String outfit = "";
   public boolean flying;
}
