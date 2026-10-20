Compile-only stand-ins for the Mixin annotations, `CallbackInfo`, and Fabric Loader's `ModInitializer`, used by the add-ons with Java code.

They copy the real annotations' names, packages, targets and retention from
[FabricMC/Mixin](https://github.com/FabricMC/Mixin) (MIT), with only the elements the add-on uses.
At runtime Fabric Loader supplies the real Mixin library; these are never packaged into a jar.
