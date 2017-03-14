package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * Mixin for a node that produces values of a type.
 */
public interface Typed<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {

  /**
   * The static type for this expression which is usually set by the typing
   * pass.
   */
  public StaticType getStaticType();

  /**
   * Sets the static type for this expression.
   * @return this
   */
  public Typed<B, T, V> setStaticType(StaticType newStaticType);
}
