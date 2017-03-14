package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * A part of a type expression that can be referenced from outside a larger
 * type expression.
 */
public interface WholeType<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {

  /**
   * The static type.  Usually null until the typing pass has run.
   */
  public StaticType getStaticType();

  /**
   * Sets the type returned by {@link #getStaticType()}.
   * @return this
   */
  public WholeType<B, T, V> setStaticType(StaticType newStaticType);
}
