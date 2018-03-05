package com.mikesamuel.cil.reflect;

/** Metadata for a member declaration. */
public abstract class MemberMetadata {
  /** A bit field of {@code java.lang.reflect.Modifier} bits. */
  public final int modifiers;
  /** The full name of the declared member. */
  public final String name;

  MemberMetadata(int modifiers, String name) {
    this.modifiers = modifiers;
    this.name = name;
  }
}
