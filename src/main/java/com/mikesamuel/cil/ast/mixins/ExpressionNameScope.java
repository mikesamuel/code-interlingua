package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;

/**
 * Introduces a scope for expression names.
 */
public interface ExpressionNameScope<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /** The expression name resolver for this scope. */
  public ExpressionNameResolver getExpressionNameResolver();

  /**
   * Sets the expression name resolver for this scope.
   * @return this
   */
  public ExpressionNameScope<B, T, V> setExpressionNameResolver(
      ExpressionNameResolver newResolver);
}
