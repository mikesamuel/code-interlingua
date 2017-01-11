package com.mikesamuel.cil.ast.traits;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.meta.TypeInfo;

/** A reference to a type. */
public interface TypeReference extends NodeI {
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
