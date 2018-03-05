package com.mikesamuel.cil.reflect;

import com.google.common.collect.ImmutableList;

/** Builds a method declaration. */
public abstract class MethodBuilder
extends CallableMemberBuilder<MethodBuilder, MethodMetadata> {

  MethodBuilder(int modifiers, String name, String descriptor) {
    super(modifiers, name, descriptor);
  }

  private TypeReference returnType = TypeReference.T_VOID;

  /** */
  public TypeReferenceBuilder<MethodBuilder> returns() {
    return new TypeReferenceBuilder<MethodBuilder>() {

      @Override
      @SuppressWarnings("synthetic-access")
      protected MethodBuilder t(TypeReference t) {
        returnType = t;
        return MethodBuilder.this;
      }

    };
  }

  @Override
  protected MethodMetadata toMemberMetadata() {
    return new MethodMetadata(
        modifiers, name, returnType, descriptor,
        ImmutableList.copyOf(typeParameters),
        ImmutableList.copyOf(formals), ImmutableList.copyOf(thrown),
        getAdapter());
  }

}
