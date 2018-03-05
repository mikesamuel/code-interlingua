package com.mikesamuel.cil.reflect;

import javax.annotation.Nullable;

/** Allows reflectively manipulating an object field. */
public interface FieldAdapter {
  /**
   * Gets the value for the given instance.
   * @throws NullPointerException if the field is not static but instance is.
   */
  public @Nullable Object get(@Nullable Object instance)
      throws NullPointerException;
  /**
   * Sets the value for the given instance.
   * @throws UnsupportedOperationException if the field cannot be
   *    set reflectively.  This is the case if the field is final.
   * @throws NullPointerException if the field is not static but instance is.
   */
  public @Nullable Object set(@Nullable Object instance, @Nullable Object newValue)
      throws NullPointerException, UnsupportedOperationException;
}
