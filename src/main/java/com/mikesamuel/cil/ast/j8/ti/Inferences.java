package com.mikesamuel.cil.ast.j8.ti;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;

/** The result of inferring type parameters for a call. */
public final class Inferences {
  /** True if inference depended on an unchecked conversion. */
  public final boolean dependsOnUncheckedConversion;
  /** A map from type parameters to proper types. */
  public final ImmutableMap<Name, StaticType> resolutions;
  /** The result type of the call which may be {@code void}. */
  public final StaticType normalResultType;
  /** The types of any thrown exceptions. */
  public final ImmutableList<StaticType> thrownTypes;

  Inferences(boolean dependsOnUncheckedConversion,
      ImmutableMap<Name, StaticType> resolutions,
      StaticType normalResultType,
      ImmutableList<StaticType> thrownTypes) {
    this.dependsOnUncheckedConversion = dependsOnUncheckedConversion;
    this.resolutions = resolutions;
    this.normalResultType = normalResultType;
    this.thrownTypes = thrownTypes;
  }
}
