package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;

/**
 * Introduces a scope for expression names.
 */
public interface ExpressionNameScope extends NodeI {
  /** The expression name resolver for this scope. */
  public ExpressionNameResolver getExpressionNameResolver();

  /**
   * Sets the expression name resolver for this scope.
   * @return this
   */
  public ExpressionNameScope setExpressionNameResolver(
      ExpressionNameResolver newResolver);
}
