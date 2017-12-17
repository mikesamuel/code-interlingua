package com.mikesamuel.cil.ast.meta;

import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * A specification for a type that has not yet been checked for structural
 * consistency with {@link TypeInfo}.
 */
public final class TypeSpecification extends PartialTypeSpecification {

  /** A special invalid type value. */
  public static final TypeSpecification ERROR_TYPE_SPEC = new TypeSpecification(
      new TypeSpecification(
          new PackageSpecification(
              Name.DEFAULT_PACKAGE.child("error", Name.Type.PACKAGE)),
          "ErrorType", Name.Type.CLASS),
      "TYPE", Name.Type.FIELD);

  /**
   * The type specification of any containing type, method, or package.
   */
  public final PartialTypeSpecification parent;
  /**
   * The raw name of the element type.
   * For a type like {@code int} or {@code int[]} we use a raw name like
   * {@code /java/lang/Integer#TYPE} so this can be a {@link Name.Type#FIELD}.
   */
  public final Name rawName;
  /** Bindings for any type parameters. */
  public final ImmutableList<TypeBinding> bindings;
  /**
   * The number of levels of array present.
   * 3 for {@code int[][][]}.
   */
  public final int nDims;

  /**
   * @param unqualifiedName The unqualified type name.
   * @param nameType The type of identifier.  If this specifies an
   *   array of primitives, like {@code int[]} we use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   */
  public TypeSpecification(
      PartialTypeSpecification parent,
      String unqualifiedName, Name.Type nameType) {
    this(parent, unqualifiedName, nameType, ImmutableList.of(), 0);
  }

  /**
   * @param unqualifiedName The unqualified type name.
   * @param nameType The type of identifier.  If this specifies an
   *   array of primitives, like {@code int[]} we use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   * @param dims the number of levels of array present.
   *       3 for {@code int[][][]}.
   */
  public TypeSpecification(
      PartialTypeSpecification parent,
      String unqualifiedName, Name.Type nameType, int dims) {
    this(parent, unqualifiedName, nameType, ImmutableList.of(), dims);
  }

  /**
   * @param unqualifiedName The unqualified type name.
   * @param nameType The type of identifier.  If this specifies an
   *   array of primitives, like {@code int[]} we use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   * @param bindings Bindings for any type parameters.
   * @param nDims the number of levels of array present.
   *       3 for {@code int[][][]}.
   */
  public TypeSpecification(
      PartialTypeSpecification parent,
      String unqualifiedName, Name.Type nameType,
      List<TypeBinding> bindings, int nDims) {
    this(parent, parent.getRawName().child(unqualifiedName, nameType),
        bindings, nDims);
  }

  TypeSpecification(
      PartialTypeSpecification parent, Name rawName,
      List<TypeBinding> bindings, int nDims) {
    Preconditions.checkArgument(parent != null);
    Preconditions.checkArgument(
        parent.getRawName() == rawName.parent,
        "%s != %s", parent.getRawName(), rawName.parent);
    Preconditions.checkArgument(nDims >= 0);
    Preconditions.checkArgument(
        rawName.type.isType
        ||
        rawName.type == Name.Type.FIELD
        && rawName.identifier.equals("TYPE"),
        rawName);
    this.parent = parent;
    this.rawName = rawName;
    this.bindings = ImmutableList.copyOf(bindings);
    this.nDims = nDims;
  }

  @Override
  public Name getRawName() { return rawName; }

  @Override
  public PartialTypeSpecification parent() { return parent; }

  /**
   * The specification of the enclosing type if any.
   */
  public Optional<TypeSpecification> getOuterType() {
    for (PartialTypeSpecification anc = parent;
         anc != null; anc = anc.parent()) {
      if (anc instanceof TypeSpecification) {
        return Optional.of((TypeSpecification) anc);
      }
    }
    return Optional.absent();
  }

  @Override
  public ImmutableList<TypeSpecification.TypeBinding> bindings() {
    return bindings;
  }


  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + bindings.hashCode();
    result = prime * result + nDims;
    result = prime * result + parent.hashCode();
    result = prime * result + rawName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TypeSpecification other = (TypeSpecification) obj;
    if (bindings == null) {
      if (other.bindings != null) {
        return false;
      }
    } else if (!bindings.equals(other.bindings)) {
      return false;
    }
    if (nDims != other.nDims) {
      return false;
    }
    if (parent == null) {
      if (other.parent != null) {
        return false;
      }
    } else if (!parent.equals(other.parent)) {
      return false;
    }
    if (rawName == null) {
      if (other.rawName != null) {
        return false;
      }
    } else if (!rawName.equals(other.rawName)) {
      return false;
    }
    return true;
  }

  @Override
  public void appendToStringBuilder(StringBuilder sb) {
    super.appendToStringBuilder(sb);
    for (int i = 0; i < nDims; ++i) {
      sb.append("[]");
    }
  }


  /** A type to which a type parameter refers and any wildcard bounds. */
  public static final class TypeBinding {
    /** The wildcard bounds variance if any or INVARIANT if not. */
    public final Variance variance;
    /**
     * The name of the referent type or null to assume the base type from the
     * type parameter declaration.
     */
    public final @Nullable TypeSpecification typeSpec;

    /**
     * The non-canonical {@code ?} binding.
     */
    public static final TypeBinding WILDCARD = new TypeBinding(
        Variance.EXTENDS, null);

    /** */
    public TypeBinding(
        Variance variance, @Nullable TypeSpecification typeSpec) {
      Preconditions.checkArgument(
          typeSpec != null || variance == Variance.EXTENDS, variance);
      this.variance = variance;
      this.typeSpec = typeSpec;
    }

    /** */
    public TypeBinding(TypeSpecification typeSpec) {
      this(Variance.INVARIANT, typeSpec);
    }

    /** Substitutes type parameters in the binding. */
    public TypeBinding subst(
        Function<? super Name, ? extends TypeBinding> bindings) {
      Variance v = this.variance;
      TypeSpecification ts = this.typeSpec;
      if (typeSpec != null && typeSpec.bindings.isEmpty()
          && ts.rawName.type == Name.Type.TYPE_PARAMETER) {
        TypeBinding b = bindings.apply(ts.rawName);
        if (b != null) {
          if (ts.nDims != 0) {
            ts = b.typeSpec.withNDims(b.typeSpec.nDims + ts.nDims);
          } else {
            ts = b.typeSpec;
          }

          if (variance == b.variance
              || variance == Variance.INVARIANT) {
            v = b.variance;
          } else if (b.variance == Variance.INVARIANT) {
            // Use v from this.
          } else {
            // Can't reconcile super & extends.
            return new TypeBinding(
                Variance.INVARIANT, StaticType.ERROR_TYPE.typeSpecification);
          }
        }
      }

      ts = ts != null ? ts.subst(bindings) : null;

      if ((ts == null ? typeSpec == null : ts.equals(typeSpec))
          && v == this.variance) {
        return this;
      } else if (ts == null) {
        return WILDCARD;
      } else {
        return new TypeBinding(v, ts);
      }
    }

    @Override
    public String toString() {
      switch (variance) {
        case EXTENDS:
          return typeSpec != null ? "? extends " + typeSpec : "?";
        case INVARIANT:
          return typeSpec.toString();
        case SUPER:
          return "? super " + typeSpec;
      }
      throw new AssertionError(variance);
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((typeSpec == null) ? 0 : typeSpec.hashCode());
      result = prime * result + ((variance == null) ? 0 : variance.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      TypeBinding other = (TypeBinding) obj;
      if (typeSpec == null) {
        if (other.typeSpec != null) {
          return false;
        }
      } else if (!typeSpec.equals(other.typeSpec)) {
        return false;
      }
      if (variance != other.variance) {
        return false;
      }
      return true;
    }
  }

  /** The variance for a wildcard type binding. */
  public enum Variance {
    /** */
    EXTENDS,
    /** Non-wildcard bindings are invariant. */
    INVARIANT,
    /** */
    SUPER,
  }

  /**
   * A type specification for the given type which is bound properly for the
   * body of the declared type.
   * <p>
   * For example, for {@code class C<X, Y, Z>} this returns
   * the type specification {@code C<X, Y, Z>} by binding each type parameter
   * to itself.
   */
  public static TypeSpecification autoScoped(
      Name typeName, TypeInfoResolver r) {
    Preconditions.checkArgument(
        typeName.type.isType || typeName.type == Name.Type.FIELD);
    return (TypeSpecification) autoScopedPartial(typeName, r);
  }

  private static TypeBinding selfBinding(Name typeParameterName) {
    Preconditions.checkArgument(
        typeParameterName.type == Name.Type.TYPE_PARAMETER);
    return new TypeBinding(
        (TypeSpecification)
        autoScopedPartial(
            typeParameterName,
            TypeInfoResolver.Resolvers.nullResolver()));
  }

  static PartialTypeSpecification autoScopedPartial(
      Name partialTypeName, TypeInfoResolver r) {
    return PartialTypeSpecification.fromName(partialTypeName,
        new Function<Name, ImmutableList<TypeBinding>>() {

          @SuppressWarnings("synthetic-access")
          @Override
          public ImmutableList<TypeBinding> apply(Name typeName) {
            Optional<TypeInfo> tiOpt = r.resolve(typeName);
            if (tiOpt.isPresent()) {
              TypeInfo ti = tiOpt.get();
              for (MemberInfo mi : ti.getDeclaredMembers()) {
                if (typeName.equals(mi.canonName)) {
                  ImmutableList.Builder<TypeBinding> bindings =
                      ImmutableList.builder();
                  for (Name p : ((CallableInfo) mi).typeParameters) {
                    bindings.add(selfBinding(p));
                  }
                  return bindings.build();
                }
              }
            }
            return ImmutableList.of();
          }

        });
  }

  /** Substitute for type parameters. */
  public TypeSpecification subst(
      ImmutableList<Name> parameters,
      ImmutableList<TypeBinding> typeParameterBindings) {
    Preconditions.checkArgument(
        parameters.size() == typeParameterBindings.size());
    ImmutableMap.Builder<Name, TypeBinding> substitutions =
        ImmutableMap.builder();
    // TODO: If an input contains a duplicate definition like
    //    <T, T> void f(T x)
    // might we get duplicate key errors?
    // TODO: Which pass should check for that?
    for (int i = 0, n = parameters.size(); i < n; ++i) {
      substitutions.put(parameters.get(i), typeParameterBindings.get(i));
    }
    return subst(Functions.forMap(substitutions.build(), null));
  }

  /** Substitute for type parameters. */
  public TypeSpecification subst(
      Function<? super Name, ? extends TypeBinding> bindingFn) {
    TypeBinding b = rawName.type == Name.Type.TYPE_PARAMETER
        ? bindingFn.apply(rawName)
        : null;
    if (b != null) {
      switch (b.variance) {
        case EXTENDS:
        case INVARIANT:
          return b.typeSpec.withNDims(nDims);
        case SUPER:
          return JavaLang.JAVA_LANG_OBJECT.withNDims(nDims);
      }
    }
    ImmutableList.Builder<TypeBinding> substs = null;
    for (int i = 0, n = bindings.size(); i < n; ++i) {
      TypeBinding binding = bindings.get(i);
      TypeBinding substBinding = binding.subst(bindingFn);
      if (substs == null && !binding.equals(substBinding)) {
        substs = ImmutableList.builder();
        substs.addAll(bindings.subList(0, i));
      }
      if (substs != null) {
        substs.add(substBinding);
      }
    }
    if (substs == null) { return this; }
    ImmutableList<TypeBinding> newBindings = substs.build();
    Preconditions.checkState(bindings.size() == newBindings.size());
    return new TypeSpecification(parent, rawName, newBindings, nDims);
  }

  /**
   * A partial type specification that contains (transitively) no type bindings
   * with a null specification.
   * <p>
   * Effectively, this means that {@code List<?>} has been rewritten to
   * {@code List<? extends Object>}.
   */
  public TypeSpecification canon(TypeInfoResolver r) {
    return canon(r, Sets.newHashSet());
  }

  @Override
  protected TypeSpecification canon(TypeInfoResolver r, Set<Name> resolving) {
    PartialTypeSpecification canonParent = parent.canon(r, resolving);
    ImmutableList<TypeBinding> canonBindings = getCanonBindings(r, resolving);

    return canonBindings != bindings || parent != canonParent
        ? new TypeSpecification(
            canonParent, rawName, canonBindings, nDims)
        : this;
  }

  /**
   * Type specification of an array of elements whose type is specified by this.
   */
  public TypeSpecification arrayOf() {
    return withNDims(nDims + 1);
  }

  /**
   * This but with the given bindings.
   */
  public TypeSpecification withBindings(
      ImmutableList<TypeBinding> newBindings) {
    if (bindings.equals(newBindings)) {
      return this;
    }
    return new TypeSpecification(parent, rawName, newBindings, nDims);
  }

  @Override
  public TypeSpecification derive(
      Iterable<? extends TypeSpecification.TypeBinding> newBindings,
      PartialTypeSpecification newParent) {
    return new TypeSpecification(
        newParent,
        parent.getRawName().equals(newParent.getRawName())
        ? rawName
        : newParent.getRawName().child(rawName.identifier, rawName.type),
        ImmutableList.copyOf(newBindings), nDims);
  }

  /**
   * This but if {@code bindings.get(rawName)} is non-null,
   * the returned bindings, and with a parent similarly constructed.
   */
  @Override
  public TypeSpecification withBindings(
      Function<? super PartialTypeSpecification,
               ? extends Iterable<TypeBinding>> newBindings) {
    Iterable<TypeSpecification.TypeBinding> newBindingList =
        newBindings.apply(this);
    PartialTypeSpecification newParent = parent.withBindings(newBindings);
    if (newBindingList != null || newParent != parent) {
      return new TypeSpecification(
          newParent, rawName,
          newBindingList != null
          ? ImmutableList.copyOf(newBindingList) : bindings,
          nDims);
    }
    return this;
  }

  /**
   * This but with the given number of dimensions.
   */
  public TypeSpecification withNDims(int n) {
    if (n == this.nDims) { return this; }
    return new TypeSpecification(parent, rawName, bindings, n);
  }

  /**
   * The type specification for the unparameterized type with the given name.
   *
   * @param canonName a canonical java type name.
   */
  public static TypeSpecification unparameterized(Name canonName) {
    return (TypeSpecification) PartialTypeSpecification.fromName(
        canonName, Functions.constant(ImmutableList.of()));
  }
}