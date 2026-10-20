package net.fabricmc.api;
/** Compile-only stand-in for Fabric Loader's interface (the real one is supplied at runtime). */
@FunctionalInterface
public interface ModInitializer { void onInitialize(); }
