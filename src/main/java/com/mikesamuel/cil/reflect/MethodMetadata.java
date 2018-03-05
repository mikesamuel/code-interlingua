package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Metadata for a constructor declaration. */
public class MethodMetadata extends CallableMemberMetadata {
  /** The return type. */
  public final TypeReference returnType;

  MethodMetadata(
      int modifiers, String name, TypeReference returnType, String descriptor,
      ImmutableList<String> typeParameters, ImmutableList<TypeReference> formals,
      ImmutableList<TypeReference> thrown,
      Optional<InvocationAdapter> adapter) {
    super(modifiers, name, descriptor, typeParameters, formals, thrown, adapter);
    this.returnType = returnType;
  }
}
