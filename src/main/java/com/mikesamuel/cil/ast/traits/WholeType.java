package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.meta.StaticType;

/**
 * A part of a type expression that can be referenced from outside a larger
 * type expression.
 */
public interface WholeType {

  /**
   * The static type.  Usually null until the typing pass has run.
   */
  public StaticType getStaticType();

  /**
   * Sets the type returned by {@link #getStaticType()}.
   */
  public void setStaticType(StaticType newStaticType);
}
