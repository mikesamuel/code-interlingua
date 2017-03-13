package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;

/**
 * Introduces a scope for expression names.
 */
public interface ExpressionNameScope extends J8Trait {
  /** The expression name resolver for this scope. */
  public ExpressionNameResolver getExpressionNameResolver();

  /**
   * Sets the expression name resolver for this scope.
   * @return this
   */
  public ExpressionNameScope setExpressionNameResolver(
      ExpressionNameResolver newResolver);
}
