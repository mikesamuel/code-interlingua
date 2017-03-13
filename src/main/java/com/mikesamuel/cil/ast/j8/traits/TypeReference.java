package com.mikesamuel.cil.ast.j8.traits;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.meta.TypeInfo;

/** A reference to a type. */
public interface TypeReference extends J8Trait {
  /**
   * Details about the referenced type.
   */
  public @Nullable TypeInfo getReferencedTypeInfo();
  /**
   * @see #getReferencedTypeInfo
   * @return this
   */
  public TypeReference setReferencedTypeInfo(
      @Nullable TypeInfo newTypeReferenced);
}
