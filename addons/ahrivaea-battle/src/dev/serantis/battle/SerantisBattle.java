package dev.serantis.battle;

import net.fabricmc.api.ModInitializer;

/** Registers the add-on's items (Gym Master Stones and the Eternita Stone). */
public final class SerantisBattle implements ModInitializer {
    @Override
    public void onInitialize() {
        Masters.registerItems();
        Staffs.register();
        Backpacks.register();
        WingedBoots.register();
        InfluenceArmor.register();
    }
}
