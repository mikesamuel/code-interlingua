package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * A name node that references a value holding cell like a field or local
 * variable.
 */
public interface ExpressionNameReference extends NodeI {

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
