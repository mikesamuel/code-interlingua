package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Information about a type declaration. */
public final class TypeMetadata {
  /** A bitfield of {@code java.lang.reflect.Modifier} bits. */
  public final int modifiers;
  /** The full name. */
  public final String name;
  /** Any type parameters. */
  public final ImmutableList<String> typeParameters;
  /**
   * All super types including both super class, if any, and interfaces
   * declared as implemented.
   */
  public final ImmutableList<TypeReference> superTypes;
  /** Names of the outer type if any. */
  public final Optional<String> outerType;
  /** Names of any inner types. */
  public final ImmutableList<String> innerTypes;
  /** Metadata for constructors. */
  public final ImmutableList<ConstructorMetadata> constructors;
  /** Metadata for fields. */
  public final ImmutableList<FieldMetadata> fields;
  /** Metadata for methods. */
  public final ImmutableList<MethodMetadata> methods;

  TypeMetadata(
      int modifiers, String name,
      ImmutableList<String> typeParameters, ImmutableList<TypeReference> superTypes,
      Optional<String> outerType, ImmutableList<String> innerTypes,
      ImmutableList<ConstructorMetadata> constructors,
      ImmutableList<FieldMetadata> fields, ImmutableList<MethodMetadata> methods) {
    this.modifiers = modifiers;
    this.name = name;
    this.typeParameters = typeParameters;
    this.superTypes = superTypes;
    this.outerType = outerType;
    this.innerTypes = innerTypes;
    this.constructors = constructors;
    this.fields = fields;
    this.methods = methods;
  }
}
