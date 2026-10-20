package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Target({ElementType.METHOD}) @Retention(RetentionPolicy.RUNTIME)
public @interface ModifyConstant { String[] method() default {}; Constant[] constant() default {}; boolean remap() default true; int require() default -1; int expect() default 1; int allow() default -1; String constraints() default ""; int order() default 10000; }
