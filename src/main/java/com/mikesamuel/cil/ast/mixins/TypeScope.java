package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;

/**
 * Mixin for nodes that establish a new scope for {@link TypeDeclaration}s.
 */
public interface TypeScope<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * Maps names in the scope to qualified names.
   */
  public TypeNameResolver getTypeNameResolver();

  /**
   * @see #getTypeNameResolver
   * @return this
   */
  public TypeScope<B, T, V> setTypeNameResolver(
      TypeNameResolver newNameResolver);
}
