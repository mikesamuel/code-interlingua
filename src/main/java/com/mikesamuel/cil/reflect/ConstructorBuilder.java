package com.mikesamuel.cil.reflect;

import com.google.common.collect.ImmutableList;

/** A builder for a constructor declaration. */
public abstract class ConstructorBuilder
extends CallableMemberBuilder<ConstructorBuilder, ConstructorMetadata> {

  ConstructorBuilder(int modifiers, String name, String descriptor) {
    super(modifiers, name, descriptor);
  }

  @Override
  protected ConstructorMetadata toMemberMetadata() {
    return new ConstructorMetadata(
        modifiers, name, descriptor,
        ImmutableList.copyOf(typeParameters),
        ImmutableList.copyOf(formals), ImmutableList.copyOf(thrown),
        getAdapter());
  }

}
