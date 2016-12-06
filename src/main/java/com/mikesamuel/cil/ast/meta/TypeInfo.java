package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

/** Describes a type. */
public final class TypeInfo {
  /**
   * The canonical name, or in the case of anonymous classes, the ordinal
   * following the canonical name of the containing class.
   */
  public final Name canonName;
  /**
   * @see java.lang.reflect.Modifier
   */
  public final int modifiers;
  /**
   * True for anonymous classes.
   */
  public final boolean isAnonymous;
  /** The name of the super-type. */
  public final Optional<Name> superType;
  /** The name of implemented interfaces. */
  public final ImmutableList<Name> interfaces;
  /** The name of the outer class if any. */
  public final Optional<Name> outerClass;
  /**
   * The names of inner classes including those from any super-type and
   * interfaces.
   */
  public final ImmutableList<Name> innerClasses;

  /** */
  public TypeInfo(
      Name canonName,
      int modifiers,
      boolean isAnonymous,
      Optional<Name> superType,
      ImmutableList<Name> interfaces,
      Optional<Name> outerClass,
      ImmutableList<Name> innerClasses) {
    this.canonName = canonName;
    this.modifiers = modifiers;
    this.isAnonymous = isAnonymous;
    this.superType = superType;
    this.interfaces = interfaces;
    this.outerClass = outerClass;
    this.innerClasses = innerClasses;
  }
}