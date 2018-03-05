package com.mikesamuel.cil.reflect;

import com.google.common.base.Optional;

/** Metadata for a field. */
public final class FieldMetadata extends MemberMetadata {
  /** The type of value the field can store. */
  public final TypeReference type;
  /** Allows reflective access to the field's storage. */
  public final Optional<FieldAdapter> adapter;

  FieldMetadata(
      int modifiers, String name, TypeReference type,
      Optional<FieldAdapter> adapter) {
    super(modifiers, name);
    this.type = type;
    this.adapter = adapter;
  }
}
