package com.mikesamuel.cil.ast.mixins;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.TypeInfo;

/** A reference to a type. */
public interface TypeReference<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * Details about the referenced type.
   */
  public @Nullable TypeInfo getReferencedTypeInfo();
  /**
   * @see #getReferencedTypeInfo
   * @return this
   */
  public TypeReference<B, T, V> setReferencedTypeInfo(
      @Nullable TypeInfo newTypeReferenced);
}
