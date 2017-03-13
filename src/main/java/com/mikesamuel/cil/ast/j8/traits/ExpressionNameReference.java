package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.Name;

/**
 * A name node that references a value holding cell like a field or local
 * variable.
 */
public interface ExpressionNameReference extends J8Trait {

  /**
   * The canonical name of the referent.
   */
  Name getReferencedExpressionName();

  /**
   * Sets the canonical name of the referent.
   * @return this
   */
  ExpressionNameReference setReferencedExpressionName(
      Name newReferencedExpressionName);
}
