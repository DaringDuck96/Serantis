package dev.serantis.battle.mixin.client;

import dev.ahrivaea.client.CreatureRenderState;
import dev.serantis.battle.client.BattleTiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(CreatureRenderState.class)
public abstract class CreatureRenderStateMixin implements BattleTiles.Holder {
    @Unique private int serantis$tiles = 1;
    @Override public int serantis$tiles() { return serantis$tiles; }
    @Override public void serantis$setTiles(int tiles) { serantis$tiles = tiles; }
}
