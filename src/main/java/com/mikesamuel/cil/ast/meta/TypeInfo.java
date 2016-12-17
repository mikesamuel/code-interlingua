package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
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
  /**
   * The declared members excluding inner classes.
   */
  public final ImmutableList<MemberInfo> declaredMembers;

  /** */
  public TypeInfo(
      Name canonName,
      int modifiers,
      boolean isAnonymous,
      Optional<Name> superType,
      ImmutableList<Name> interfaces,
      Optional<Name> outerClass,
      ImmutableList<Name> innerClasses,
      ImmutableList<MemberInfo> declaredMembers) {
    this.canonName = canonName;
    this.modifiers = modifiers;
    this.isAnonymous = isAnonymous;
    this.superType = superType;
    this.interfaces = interfaces;
    this.outerClass = outerClass;
    this.innerClasses = innerClasses;
    this.declaredMembers = declaredMembers;
  }

  /**
   * The first member matching the given predicate if any.
   * Searches super-types.
   */
  public Optional<MemberInfo> memberMatching(
      TypeInfoResolver superTypeResolver, Predicate<MemberInfo> p) {
    for (MemberInfo m : declaredMembers) {
      if (p.apply(m)) {
        return Optional.of(m);
      }
    }

    if (superType.isPresent()) {
      Optional<TypeInfo> superTypeInfo =
          superTypeResolver.resolve(superType.get());
      if (superTypeInfo.isPresent()) {
        Optional<MemberInfo> miOpt =
            superTypeInfo.get().memberMatching(superTypeResolver, p);
        if (miOpt.isPresent()) { return miOpt; }
      }
    }
    // We may end up visiting some interfaces multiple times, but this is ok
    // as long as there are no inheritance cycles.
    // TODO: Might this be called before inheritance cycles have been ruled out?
    for (Name iface : interfaces) {
      Optional<TypeInfo> ifaceTypeInfoOpt = superTypeResolver.resolve(iface);
      if (ifaceTypeInfoOpt.isPresent()) {
        Optional<MemberInfo> miOpt = ifaceTypeInfoOpt.get().memberMatching(
            superTypeResolver, p);
        if (miOpt.isPresent()) {
          return miOpt;
        }
      }
    }
    return Optional.absent();
  }

  /**
   * The type's package.
   */
  public Name getPackage() {
    Name nm = canonName;
    while (nm.type != Name.Type.PACKAGE) {
      nm = nm.parent;
    }
    return nm;
  }
}