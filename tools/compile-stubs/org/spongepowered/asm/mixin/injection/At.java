package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Target({}) @Retention(RetentionPolicy.RUNTIME)
public @interface At { String id() default ""; String value(); String slice() default ""; String target() default ""; int ordinal() default -1; int opcode() default -1; boolean remap() default true; }
