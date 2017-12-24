package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

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
    TypeSpecification substed = new TypeSpecification.Mapper() {
      @Override
      public TypeSpecification map(TypeSpecification s) {
        Name rawName = s.getRawName();
        if (InferenceVariable.ALPHA_CONTAINER.equals(rawName.parent)) {
          ReferenceType rt = m.get(new InferenceVariable(rawName));
          if (rt != null) {
            return rt.typeSpecification.withNDims(
                rt.typeSpecification.nDims + s.nDims);
          }
        }
        return super.map(s);
      }
    }.map(t.typeSpecification);
    if (ts.equals(substed)) { return this; }
    return (NominalType) NominalType.from(
        m.values().iterator().next().getPool().type(substed, null, null));
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