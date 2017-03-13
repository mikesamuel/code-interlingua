package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * Trait for a node that produces values of a type.
 */
public interface Typed extends J8Trait {

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
