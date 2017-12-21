package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

final class InferenceVariable extends SyntheticType {
  final Name name;

  static final Name ALPHA_CONTAINER = Name.DEFAULT_PACKAGE
      .child("__syn__", Name.Type.PACKAGE)
      .child("__ITI__", Name.Type.CLASS);

  InferenceVariable(int index) {
    this(ALPHA_CONTAINER.child(
        "\u03b1" + index, Name.Type.TYPE_PARAMETER));
  }

  InferenceVariable(Name name) {
    Preconditions.checkArgument(
        ALPHA_CONTAINER.equals(name.parent)
        && name.type == Name.Type.TYPE_PARAMETER);
    this.name = name;
  }

  @Override
  ImmutableSet<InferenceVariable> mentioned() {
    return ImmutableSet.of(this);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof InferenceVariable)) {
      return false;
    }
    InferenceVariable that = (InferenceVariable) o;
    return this.name.equals(that.name);
  }

  @Override
  public int hashCode() {
    return name.hashCode() ^ 0x408aa7fe;
  }

  @Override public String toString() {
    return name.identifier;
  }

  static boolean isProperType(StaticType t) {
    return isProperType(t.typeSpecification);
  }

  static boolean isProperType(PartialTypeSpecification t) {
    // https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.1.1
    // The term proper type excludes such "types" that mention inference
    // variables.
    PartialTypeSpecification p = t.parent();
    if (p != null) {
      if (ALPHA_CONTAINER.equals(p.getRawName()) || !isProperType(p)) {
        return false;
      }
    }
    if (mentionsInferenceVariable(t.bindings())) {
      return false;
    }
    return true;
  }

  private static boolean mentionsInferenceVariable(
      Iterable<? extends TypeSpecification.TypeBinding> bindings) {
    for (TypeSpecification.TypeBinding b : bindings) {
      if (!isProperType(b.typeSpec)) { return true; }
    }
    return false;
  }

  @Override
  SyntheticType subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    ReferenceType rt = m.get(this);
    return rt != null ? NominalType.from(rt) : this;
  }
}