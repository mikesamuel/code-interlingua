package com.mikesamuel.cil.ast.traits;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.meta.TypeInfo;

/** A reference to a type. */
public interface TypeReference {
  /**
   * Details about the referenced type.
   */
  public @Nullable TypeInfo getReferencedTypeInfo();
  /**
   * @see #getReferencedTypeInfo
   */
  public void setReferencedTypeInfo(@Nullable TypeInfo newTypeReferenced);
}