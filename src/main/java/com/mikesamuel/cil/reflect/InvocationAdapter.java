package com.mikesamuel.cil.reflect;

import java.lang.reflect.InvocationTargetException;

import javax.annotation.Nullable;

/**
 * Allows reflective invocation of a callable member via its
 * {@linkplain CallableMemberMetadata metadata}.
 */
public interface InvocationAdapter {
  /** Applies the callable reflectively. */
  @Nullable Object apply(@Nullable Object instance, Object... args)
      throws NullPointerException, InvocationTargetException;
}
