package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

interface Bound {
  /**
   * Per <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.4">18.4</a>
   * this bound must be computed before or at the same time as any that follow
   * the returned inference variables.
   * <p>
   * This method adds edges to the resolution order graph.
   */
  void buildResolutionOrderGraph(ResolutionOrder.Builder b);

  ImmutableSet<InferenceVariable> mentioned();

  /** Substitutes proper types for inference variables. */
  Bound subst(Map<? super InferenceVariable, ? extends ReferenceType> m);
}
