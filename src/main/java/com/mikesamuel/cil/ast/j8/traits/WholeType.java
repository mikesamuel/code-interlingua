package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * A part of a type expression that can be referenced from outside a larger
 * type expression.
 */
public interface WholeType extends J8Trait {

  /**
   * The static type.  Usually null until the typing pass has run.
   */
  public StaticType getStaticType();

  /**
   * Sets the type returned by {@link #getStaticType()}.
   * @return this
   */
  public WholeType setStaticType(StaticType newStaticType);
}
