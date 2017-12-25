package com.mikesamuel.cil.ast.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
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
  private final List<MemberInfo> declaredMembers;

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
    this.declaredMembers = new ArrayList<>(declaredMembers);
  }

  /**
   * The declared members excluding inner classes, but including synthetics.
   */
  public Iterable<MemberInfo> getDeclaredMembers() {
    return Collections.unmodifiableList(declaredMembers);
  }

  /**
   * Adds a synthetic member to the class.
   * It is the responsibility of the pass that defines the synthetic member
   * to ensure that its name/signature does not introduce a name-space conflict.
   */
  public void addSyntheticMember(MemberInfo mi) {
    Preconditions.checkArgument(canonName.equals(mi.canonName.parent));
    this.declaredMembers.add(mi);
  }

  /**
   * Iterates through all members of this type info and super types.
   */
  public Iterable<MemberInfo> transitiveMembers(
      TypeInfoResolver superTypeResolver) {
    return new Iterable<MemberInfo>() {
      @Override
      public Iterator<MemberInfo> iterator() {
        return new Iterator<MemberInfo>() {

          private final Iterator<MemberInfo> declared =
              getDeclaredMembers().iterator();
          private final Iterator<TypeSpecification> superTypes =
              Iterables.concat(superType.asSet(), interfaces).iterator();
          private Iterator<MemberInfo> superTypeMembers =
              ImmutableList.<MemberInfo>of().iterator();

          private void getOne() {
            if (pending == null) {
              if (declared.hasNext()) {
                pending = Preconditions.checkNotNull(declared.next());
              }
            }

            if (pending == null) {
              while (!superTypeMembers.hasNext() && superTypes.hasNext()) {
                TypeSpecification oneSuperType = superTypes.next();
                Optional<TypeInfo> superTypeInfo =
                    superTypeResolver.resolve(oneSuperType.rawName);
                // We may end up visiting some interfaces multiple times, but
                // this is ok as long as there are no inheritance cycles.
                // TODO: Might this be called before inheritance cycles have
                // been ruled out?
                if (superTypeInfo.isPresent()) {
                  superTypeMembers = superTypeInfo.get()
                      .transitiveMembers(superTypeResolver).iterator();
                }
              }
              if (superTypeMembers.hasNext()) {
                pending = Preconditions.checkNotNull(superTypeMembers.next());
              }
            }
          }

          private MemberInfo pending;

          @Override
          public boolean hasNext() {
            getOne();
            return pending != null;
          }

          @Override
          public MemberInfo next() {
            getOne();
            MemberInfo mi = Preconditions.checkNotNull(pending);
            pending = null;
            return mi;
          }

        };
      }
    };
  }

  /**
   * The first member matching the given predicate if any.
   * Searches super-types.
   */
  public Optional<MemberInfo> memberMatching(
      TypeInfoResolver superTypeResolver, Predicate<MemberInfo> p) {
    for (MemberInfo mi : transitiveMembers(superTypeResolver)) {
      if (p.apply(mi)) { return Optional.of(mi); }
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
   * The method with the given name.  Does not search super-types.
   */
  public Optional<FieldInfo> declaredFieldNamed(Name nm) {
    Preconditions.checkArgument(nm.type == Name.Type.FIELD);
    for (MemberInfo mi : declaredMembers) {
      if (mi.canonName.equals(nm)) {
        return Optional.of((FieldInfo) mi);
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
    public Builder interfaces(Iterable<TypeSpecification> newInterfaces) {
      this.interfaces = ImmutableList.copyOf(newInterfaces);
      return this;
    }
    public Builder parameters(Iterable<? extends Name> newParameters) {
      this.parameters = ImmutableList.copyOf(newParameters);
      return this;
    }
    public Builder outerClass(Optional<Name> newOuterClass) {
      this.outerClass = newOuterClass;
      return this;
    }
    public Builder innerClasses(Iterable<? extends Name> newInnerClasses) {
      this.innerClasses = ImmutableList.copyOf(newInnerClasses);
      return this;
    }
    public Builder declaredMembers(
        Iterable<? extends MemberInfo> newDeclaredMembers) {
      this.declaredMembers = ImmutableList.copyOf(newDeclaredMembers);
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
  public TypeInfo.Builder map(MetadataBridge metadataBridge) {
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

    return TypeInfo.builder(bridgeTypeName.apply(canonName))
        .modifiers(modifiers)
        .isAnonymous(isAnonymous)
        .superType(
            superType.isPresent()
            ? Optional.of(bridgeType.apply(superType.get()))
            : Optional.absent())
        .interfaces(Lists.transform(interfaces, bridgeType))
        .outerClass(
            outerClass.isPresent()
            ? Optional.of(bridgeTypeName.apply(outerClass.get()))
            : Optional.absent())
        .parameters(Lists.transform(parameters, bridgeTypeName))
        .innerClasses(Lists.transform(innerClasses, bridgeTypeName))
        .declaredMembers(
            Lists.transform(
                declaredMembers,
                new Function<MemberInfo, MemberInfo>() {

                  @Override
                  public MemberInfo apply(MemberInfo x) {
                    return metadataBridge.bridgeMemberInfo(x);
                  }

                }));
  }
}