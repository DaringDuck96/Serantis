package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfoReturnable<R> extends CallbackInfo {
  public CallbackInfoReturnable(String name, boolean cancellable) { super(name, cancellable); }
  public void setReturnValue(R returnValue) {}
  public R getReturnValue() { return null; }
}
