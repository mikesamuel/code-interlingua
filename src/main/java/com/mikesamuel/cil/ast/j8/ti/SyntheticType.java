package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

abstract class SyntheticType {
  @Override
  public abstract boolean equals(Object o);
  @Override
  public abstract int hashCode();

  /** The inference variables mentioned by this type. */
  abstract ImmutableSet<InferenceVariable> mentioned();

  /** Substitutes proper types for inference variables. */
  abstract SyntheticType subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m);
}