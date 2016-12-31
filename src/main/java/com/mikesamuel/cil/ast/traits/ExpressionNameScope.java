package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeOrBuilder;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;

/**
 * Introduces a scope for expression names.
 */
public interface ExpressionNameScope extends NodeOrBuilder {
  /** The expression name resolver for this scope. */
  public ExpressionNameResolver getExpressionNameResolver();

  /** Sets the expression name resolver for this scope. */
  public void setExpressionNameResolver(ExpressionNameResolver newResolver);
}
