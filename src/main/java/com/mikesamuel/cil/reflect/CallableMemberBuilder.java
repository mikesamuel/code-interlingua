package com.mikesamuel.cil.reflect;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Optional;

abstract class CallableMemberBuilder<
    TT extends CallableMemberBuilder<TT, MD>,
    MD extends CallableMemberMetadata>
extends MemberBuilder<MD> {

  final String descriptor;
  final List<String> typeParameters = new ArrayList<>();
  final List<TypeReference> formals = new ArrayList<>();
  final List<TypeReference> thrown = new ArrayList<>();
  private Optional<InvocationAdapter> adapter = Optional.absent();

  CallableMemberBuilder(int modifiers, String name, String descriptor) {
    super(modifiers, name);
    this.descriptor = descriptor;
  }

  @SuppressWarnings("unchecked")
  TT thisAsTt() { return (TT) this; }

  /** */
  public TT param(String paramName) {
    typeParameters.add(paramName);
    return thisAsTt();
  }

  /** */
  public TypeReferenceBuilder<TT> formal() {
    return new TypeReferenceBuilder<TT>() {

      @Override
      protected TT t(TypeReference t) {
        formals.add(t);
        return thisAsTt();
      }

    };
  }

  /** */
  public TypeReferenceBuilder<TT> thrown() {
    return new TypeReferenceBuilder<TT>() {

      @Override
      protected TT t(TypeReference t) {
        thrown.add(t);
        return thisAsTt();
      }

    };
  }

  /** */
  public TT adapter(InvocationAdapter newAdapter) {
    this.adapter = Optional.of(newAdapter);
    return thisAsTt();
  }

  Optional<InvocationAdapter> getAdapter() {
    return adapter;
  }
}
