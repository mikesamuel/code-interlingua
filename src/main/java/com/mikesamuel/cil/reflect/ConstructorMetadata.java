package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Metadata for a constructor declaration. */
public final class ConstructorMetadata extends CallableMemberMetadata {

  ConstructorMetadata(
      int modifiers, String name, String descriptor,
      ImmutableList<String> typeParameters,
      ImmutableList<TypeReference> formals, ImmutableList<TypeReference> thrown,
      Optional<InvocationAdapter> adapter) {
    super(modifiers, name, descriptor, typeParameters, formals, thrown, adapter);
  }

}
