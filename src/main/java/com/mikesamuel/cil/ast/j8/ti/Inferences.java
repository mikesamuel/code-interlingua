package com.mikesamuel.cil.ast.j8.ti;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

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

  Inferences(
      boolean dependsOnUncheckedConversion,
      ImmutableMap<Name, StaticType> resolutions,
      StaticType normalResultType,
      ImmutableList<StaticType> thrownTypes) {
    this.dependsOnUncheckedConversion = dependsOnUncheckedConversion;
    this.resolutions = resolutions;
    this.normalResultType = normalResultType;
    this.thrownTypes = thrownTypes;
  }

  /** Substitutes resolutions into t. */
  public StaticType subst(StaticType t) {
    if (t instanceof ReferenceType) {
      ReferenceType rt = (ReferenceType) t;
      TypeSpecification ts = subst(rt.typeSpecification);
      if (ts != rt.typeSpecification) {
        return rt.getPool().type(ts, null, null);
      }
    }
    return t;
  }

  /** Substitutes resolutions into t. */
  public TypeSpecification subst(TypeSpecification t) {
    return t.subst(
        new Function<Name, TypeBinding>() {

          @Override
          public TypeBinding apply(Name nm) {
            StaticType resolution = resolutions.get(nm);
            if (resolution != null) {
              return new TypeBinding(resolution.typeSpecification);
            }
            return null;
          }

        });
  }
}
