package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/** Describes a type. */
public final class TypeInfo extends AccessibleInfo {
  /**
   * True for anonymous classes.
   */
  public final boolean isAnonymous;
  /** The name of the super-type. */
  public final Optional<TypeSpecification> superType;
  /** The name of implemented interfaces. */
  public final ImmutableList<TypeSpecification> interfaces;
  /** Any type parameters for the type. */
  public final ImmutableList<Name> parameters;
  /** The name of the outer class if any. */
  public final Optional<Name> outerClass;
  /**
   * The names of inner classes including those from any super-type and
   * interfaces.
   */
  public final ImmutableList<Name> innerClasses;
  // TODO: split declaredMembers in two.  There's nothing gained by mixing
  // methods and callables.
  /**
   * The declared members excluding inner classes.
   */
  public final ImmutableList<MemberInfo> declaredMembers;

  private TypeInfo(
      int modifiers,
      Name canonName,
      boolean isAnonymous,
      Optional<TypeSpecification> superType,
      ImmutableList<TypeSpecification> interfaces,
      ImmutableList<Name> parameters,
      Optional<Name> outerClass,
      ImmutableList<Name> innerClasses,
      ImmutableList<MemberInfo> declaredMembers) {
    super(modifiers, canonName);
    this.isAnonymous = isAnonymous;
    this.superType = superType;
    this.interfaces = interfaces;
    this.parameters = parameters;
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
          superTypeResolver.resolve(superType.get().rawName);
      if (superTypeInfo.isPresent()) {
        Optional<MemberInfo> miOpt =
            superTypeInfo.get().memberMatching(superTypeResolver, p);
        if (miOpt.isPresent()) { return miOpt; }
      }
    }
    // We may end up visiting some interfaces multiple times, but this is ok
    // as long as there are no inheritance cycles.
    // TODO: Might this be called before inheritance cycles have been ruled out?
    for (TypeSpecification iface : interfaces) {
      Optional<TypeInfo> ifaceTypeInfoOpt =
          superTypeResolver.resolve(iface.rawName);
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
   * The method with the given name.  Does not search super-types.
   */
  public Optional<CallableInfo> declaredCallableNamed(Name nm) {
    Preconditions.checkArgument(nm.type == Name.Type.METHOD);
    for (MemberInfo mi : declaredMembers) {
      if (mi.canonName.equals(nm)) {
        return Optional.of((CallableInfo) mi);
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

  /** A builder for TypeInfo with the given name. */
  public static Builder builder(Name canonName) {
    @SuppressWarnings("synthetic-access")
    Builder b = new Builder(canonName);
    return b;
  }

  /** A builder whose state is initialized to that of this. */
  public Builder builder() {
    return builder(canonName)
        .modifiers(modifiers)
        .isAnonymous(isAnonymous)
        .superType(superType)
        .interfaces(interfaces)
        .parameters(parameters)
        .outerClass(outerClass)
        .innerClasses(innerClasses)
        .declaredMembers(declaredMembers);
  }

  @SuppressWarnings("javadoc")
  public static final class Builder {
    private Name canonName;
    private int modifiers;
    private boolean isAnonymous;
    private Optional<TypeSpecification> superType = Optional.absent();
    private ImmutableList<TypeSpecification> interfaces = ImmutableList.of();
    private ImmutableList<Name> parameters = ImmutableList.of();
    private Optional<Name> outerClass = Optional.absent();
    private ImmutableList<Name> innerClasses = ImmutableList.of();
    private ImmutableList<MemberInfo> declaredMembers = ImmutableList.of();

    private Builder(Name canonName) {
      this.canonName = canonName;
    }

    public Builder modifiers(int newModifiers) {
      this.modifiers = newModifiers;
      return this;
    }
    public Builder isAnonymous(boolean newIsAnonymous) {
      this.isAnonymous = newIsAnonymous;
      return this;
    }

    public Builder superType(Optional<TypeSpecification> newSuperType) {
      this.superType = newSuperType;
      return this;
    }
    public Builder interfaces(ImmutableList<TypeSpecification> newInterfaces) {
      this.interfaces = newInterfaces;
      return this;
    }
    public Builder parameters(ImmutableList<Name> newParameters) {
      this.parameters = newParameters;
      return this;
    }
    public Builder outerClass(Optional<Name> newOuterClass) {
      this.outerClass = newOuterClass;
      return this;
    }
    public Builder innerClasses(ImmutableList<Name> newInnerClasses) {
      this.innerClasses = newInnerClasses;
      return this;
    }
    public Builder declaredMembers(
        ImmutableList<MemberInfo> newDeclaredMembers) {
      this.declaredMembers = newDeclaredMembers;
      return this;
    }

    public TypeInfo build() {
      @SuppressWarnings("synthetic-access")
      TypeInfo ti = new TypeInfo(
          modifiers, canonName, isAnonymous, superType,
          interfaces, parameters, outerClass, innerClasses,
          declaredMembers);
      return ti;
    }
  }

  /**
   * Translate type info based on the given bridge.
   */
  public TypeInfo map(MetadataBridge metadataBridge) {
    Function<TypeSpecification, TypeSpecification> bridgeType =
        new Function<TypeSpecification, TypeSpecification>() {
      @Override
      public TypeSpecification apply(TypeSpecification x) {
        return metadataBridge.bridgeTypeSpecification(x);
      }
    };

    Function<Name, Name> bridgeTypeName = new Function<Name, Name>() {
      @Override
      public Name apply(Name typeName) {
        return bridgeType.apply(TypeSpecification.unparameterized(typeName))
            .rawName;
      }
    };

    return new TypeInfo(
        modifiers,
        bridgeTypeName.apply(canonName),
        isAnonymous,

        superType.isPresent()
        ? Optional.of(bridgeType.apply(superType.get()))
        : Optional.absent(),

        ImmutableList.copyOf(
            Lists.transform(interfaces, bridgeType)),

        ImmutableList.copyOf(
            Lists.transform(parameters, bridgeTypeName)),

        outerClass.isPresent()
        ? Optional.of(bridgeTypeName.apply(outerClass.get()))
        : Optional.absent(),

        ImmutableList.copyOf(
            Lists.transform(innerClasses, bridgeTypeName)),

        ImmutableList.copyOf(
            Lists.transform(
                declaredMembers,
                new Function<MemberInfo, MemberInfo>() {

                  @Override
                  public MemberInfo apply(MemberInfo x) {
                    return metadataBridge.bridgeMemberInfo(x);
                  }

                }))
        );
  }
}