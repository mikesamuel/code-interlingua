package com.mikesamuel.cil.ast.mixins;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * Corresponds to the declaration of a name within an
 * {@link ExpressionNameScope}.
 */
public interface ExpressionNameDeclaration<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * The name declared.
   */
  @Nullable Name getDeclaredExpressionName();

  /**
   * Sets the name declared.
   * @return this
   */
  ExpressionNameDeclaration<B, T, V> setDeclaredExpressionName(
      Name newDeclaredExpressionName);
}
