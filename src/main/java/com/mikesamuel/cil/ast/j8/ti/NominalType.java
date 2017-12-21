package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

/** A type that can be mentioned via a name in the source language. */
final class NominalType extends SyntheticType {
  final StaticType t;

  private NominalType(StaticType t) {
    this.t = t;
  }

  static SyntheticType from(StaticType t) {
    if (InferenceVariable.ALPHA_CONTAINER.equals(
        t.typeSpecification.rawName.parent)) {
      return new InferenceVariable(t.typeSpecification.rawName);
    }
    return new NominalType(t);
  }

  @Override
  ImmutableSet<InferenceVariable> mentioned() {
    ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
    findMentioned(t.typeSpecification, b);
    return b.build();
  }

  private static void findMentioned(
      PartialTypeSpecification t, ImmutableSet.Builder<InferenceVariable> b) {
    Name rawName = t.getRawName();
    if (InferenceVariable.ALPHA_CONTAINER.equals(rawName.parent)) {
      b.add(new InferenceVariable(rawName));
    } else {
      for (TypeSpecification.TypeBinding binding : t.bindings()) {
        findMentioned(binding.typeSpec, b);
      }
      PartialTypeSpecification p = t.parent();
      if (p != null) {
        findMentioned(p, b);
      }
    }
  }

  @Override
  NominalType subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    TypeSpecification ts = t.typeSpecification;
    TypeSpecification substed = subst(ts, m);
    if (ts == substed) { return this; }
    return (NominalType) NominalType.from(
        m.values().iterator().next().getPool().type(substed, null, null));
  }

  private static TypeSpecification subst(
      TypeSpecification ts,
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    return (TypeSpecification) subst((PartialTypeSpecification) ts, m);
  }

  private static PartialTypeSpecification subst(
      PartialTypeSpecification ts,
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    Name rawName = ts.getRawName();
    if (ts instanceof TypeSpecification
        && InferenceVariable.ALPHA_CONTAINER.equals(rawName.parent)) {
      ReferenceType rt = m.get(new InferenceVariable(rawName));
      if (rt != null) {
        return rt.typeSpecification;
      }
    }
    boolean bindingsChanged = false;
    ImmutableList.Builder<TypeBinding> newBindings =
        ImmutableList.builder();
    for (TypeBinding binding : ts.bindings()) {
      TypeSpecification bts = binding.typeSpec;
      TypeSpecification newBts = subst(bts, m);
      bindingsChanged |= bts != newBts;
      newBindings.add(new TypeBinding(binding.variance, newBts));
    }
    PartialTypeSpecification p = ts.parent();
    PartialTypeSpecification newP = p != null ? subst(p, m) : null;
    return (bindingsChanged || p != newP)
        ? ts.derive(newBindings.build(), newP)
        : ts;
  }


  @Override
  public boolean equals(Object o) {
    if (!(o instanceof NominalType)) { return false; }
    NominalType that = (NominalType) o;
    return this.t.equals(that.t);
  }

  @Override
  public int hashCode() {
    return t.hashCode() ^ 0x8aa7fe40;
  }

  @Override
  public String toString() {
    return t.toString();
  }
}