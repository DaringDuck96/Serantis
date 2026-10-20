package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.CONSTRUCTOR}) @Retention(RetentionPolicy.RUNTIME)
public @interface Shadow { String prefix() default "shadow$"; boolean remap() default true; String[] aliases() default {}; }
