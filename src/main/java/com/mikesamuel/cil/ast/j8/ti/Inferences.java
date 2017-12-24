package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ClassOrInterfaceType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.util.LogUtils;

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

  /** Resolutions that do not mention unmentionable types. */
  public ImmutableMap<Name, StaticType> deanonymized(
      TypePool p, @Nullable Positioned pos, @Nullable Logger logger) {
    // Our inferred type arguments need to be mentionable types where possible.
    // Otherwise later passes need to take a lot of care not to synthesize
    // TypeNameNodes that are unmentionable.
    ImmutableMap.Builder<Name, StaticType> b = ImmutableMap.builder();
    for (Map.Entry<Name, StaticType> e : resolutions.entrySet()) {
      b.put(e.getKey(), deanonymize(e.getValue(), p, pos, logger));
    }
    return b.build();
  }

  private static StaticType deanonymize(
      StaticType t, TypePool p,
      @Nullable Positioned pos, @Nullable Logger logger) {
    TypeSpecification ts = deanonymize(t.typeSpecification, p, pos, logger);
    if (t.typeSpecification == ts) { return t; }
    return p.type(ts, pos, logger);
  }

  private static TypeSpecification deanonymize(
      TypeSpecification t, TypePool p,
      @Nullable Positioned pos, @Nullable Logger logger) {
    return new TypeSpecification.Mapper() {
      @Override
      public TypeSpecification map(TypeSpecification s) {
        // TODO: should this happen first or last?
        // What happens if an anonymous class nests an anonymous class?
        TypeSpecification ts = super.map(s);

        if (ts.rawName.isMentionable()) {
          return ts;
        }
        int nDims = ts.nDims;
        StaticType st = p.type(ts.withNDims(0), pos, logger);
        Preconditions.checkState(st instanceof ClassOrInterfaceType, st);
        ClassOrInterfaceType ct = (ClassOrInterfaceType) st;
        Preconditions.checkState(ct.info.isAnonymous, ct);
        // An anonymous class that is compiled from Java code either has:
        // 1. A single interface type and a redundant super-type of Object
        // 2. A super-type that may be Object
        // Pick the one appropriate for this case.
        int nInterfaces = ct.info.interfaces.size();
        Preconditions.checkState(nInterfaces < 2, ct);
        Name mentionableSuperTypeName;
        if (nInterfaces == 1) {
          mentionableSuperTypeName = ct.info.interfaces.get(0).rawName;
          Preconditions.checkState(
              JavaLang.JAVA_LANG_OBJECT.equals(ct.info.superType.orNull()),
              ct);
        } else {
          mentionableSuperTypeName = ct.info.superType.get().rawName;
        }
        // Get the appropriately parameterized super type.
        Optional<ClassOrInterfaceType> supOpt = ct.superTypeWithRawName(
            mentionableSuperTypeName);
        if (supOpt.isPresent()) {
          ClassOrInterfaceType sup = supOpt.get();
          Preconditions.checkState(!sup.info.isAnonymous, st);
          return sup.typeSpecification.withNDims(nDims);
        }
        LogUtils.log(
            logger, Level.SEVERE, pos,
            "Cannot compute mentionable super type of type argument"
            + " that bound to anonymous type " + ct, null);
        return TypeSpecification.ERROR_TYPE_SPEC;
      }
    }.map(t);
  }
}
