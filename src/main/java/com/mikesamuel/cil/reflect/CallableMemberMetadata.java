package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Metadata for a constructor or method declaration. */
public abstract class CallableMemberMetadata extends MemberMetadata {
  /** The JVM method descriptor. */
  public final String descriptor;
  /** The list of type parameters. */
  public final ImmutableList<String> typeParameters;
  /** The list of formal arguments. */
  public final ImmutableList<TypeReference> formals;
  /** The list of types declared as thrown. */
  public final ImmutableList<TypeReference> thrown;
  /** If it can be invoked reflectively, the adapter is present. */
  public final Optional<InvocationAdapter> adapter;

  CallableMemberMetadata(
      int modifiers, String name, String descriptor,
      ImmutableList<String> typeParameters, ImmutableList<TypeReference> formals,
      ImmutableList<TypeReference> thrown,
      Optional<InvocationAdapter> adapter) {
    super(modifiers, name);
    this.descriptor = descriptor;
    this.typeParameters = typeParameters;
    this.formals = formals;
    this.thrown = thrown;
    this.adapter = adapter;
  }
}
