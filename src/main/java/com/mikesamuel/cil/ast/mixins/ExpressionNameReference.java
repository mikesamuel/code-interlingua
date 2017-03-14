package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * A name node that references a value holding cell like a field or local
 * variable.
 */
public interface ExpressionNameReference<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {

  /**
   * The canonical name of the referent.
   */
  Name getReferencedExpressionName();

  /**
   * Sets the canonical name of the referent.
   * @return this
   */
  ExpressionNameReference<B, T, V> setReferencedExpressionName(
      Name newReferencedExpressionName);
}
