package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * Trait for a node that produces values of a type.
 */
public interface Typed extends NodeI {

  /**
   * The static type for this expression which is usually set by the typing
   * pass.
   */
  public StaticType getStaticType();

  /**
   * Sets the static type for this expression.
   * @return this
   */
  public Typed setStaticType(StaticType newStaticType);
}
