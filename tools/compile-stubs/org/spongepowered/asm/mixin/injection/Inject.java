package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Target({ElementType.METHOD}) @Retention(RetentionPolicy.RUNTIME)
public @interface Inject { String id() default ""; String[] method() default {}; At[] at(); boolean cancellable() default false; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default ""; }
