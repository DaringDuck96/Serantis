package serantis.smoke;

/** CI only: forces every server-side mixin target to load now, so a broken injector fails loudly at boot. */
public class SmokeInit implements net.fabricmc.api.ModInitializer {
    @Override
    public void onInitialize() {
        try {
            Class<?> env = Class.forName("org.spongepowered.asm.mixin.MixinEnvironment");
            Object cur = env.getMethod("getCurrentEnvironment").invoke(null);
            env.getMethod("audit").invoke(cur);
            System.out.println("SMOKE: mixin audit finished");
        } catch (Throwable t) {
            System.out.println("SMOKE: mixin audit FAILED");
            t.printStackTrace(System.out);
        }
        for (String c : new String[]{"dev.ahrivaea.battle.Battle", "dev.ahrivaea.battle.Battles", "dev.ahrivaea.base.Bases",
                "dev.ahrivaea.base.BaseCoreBlockEntity", "dev.ahrivaea.battle.Unit", "dev.ahrivaea.party.PartySync"}) {
            try { Class.forName(c); System.out.println("SMOKE: loaded " + c); }
            catch (Throwable t) { System.out.println("SMOKE: FAILED loading " + c); t.printStackTrace(System.out); }
        }
    }
}
