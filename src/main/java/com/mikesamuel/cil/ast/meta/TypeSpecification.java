package com.mikesamuel.cil.ast.meta;

import java.util.Map;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A specification for a type that has not yet been checked for structural
 * consistency with {@link TypeInfo}.
 */
public final class TypeSpecification {
  /** The type specification for {@code Object}. */
  public static final TypeSpecification JAVA_LANG_OBJECT =
      new TypeSpecification(StaticType.JAVA_LANG_OBJECT);

  /**
   * The name of the raw element type.  If this specifies an
   * array of primitives, like {@code int[]} then use a field name like
   * {@code /java/lang/Integer#TYPE}.
   */
  public final Name typeName;
  /** Bindings for any type parameters. */
  public final ImmutableList<TypeBinding> bindings;
  /**
   * The number of levels of array present.
   * 3 for {@code int[][][]}.
   */
  public final int nDims;

  /**
   * @param typeName The name of the raw element type.  If this specifies an
   *   array of primitives, like {@code int[]} then use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   */
  public TypeSpecification(Name typeName) {
    this(typeName, ImmutableList.of(), 0);
  }

  /**
   * @param typeName The name of the raw element type.  If this specifies an
   *   array of primitives, like {@code int[]} then use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   * @param bindings Bindings for any type parameters.
   */
  public TypeSpecification(
      Name typeName, ImmutableList<TypeBinding> bindings) {
    this(typeName, bindings, 0);
  }

  /**
   * @param typeName The name of the raw element type.  If this specifies an
   *   array of primitives, like {@code int[]} then use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   * @param dims the number of levels of array present.
   *       3 for {@code int[][][]}.
   */
  public TypeSpecification(Name typeName, int dims) {
    this(typeName, ImmutableList.of(), dims);
  }

  /**
   * @param typeName The name of the raw element type.  If this specifies an
   *   array of primitives, like {@code int[]} then use a field name like
   *   {@code /java/lang/Integer#TYPE}.
   * @param bindings Bindings for any type parameters.
   * @param nDims the number of levels of array present.
   *       3 for {@code int[][][]}.
   */
  public TypeSpecification(
      Name typeName, ImmutableList<TypeBinding> bindings, int nDims) {
    Preconditions.checkArgument(nDims >= 0);
    Preconditions.checkArgument(
        typeName.type.isType
        ||
        typeName.type == Name.Type.FIELD
        && typeName.identifier.equals("TYPE"),
        typeName);
    this.typeName = typeName;
    this.bindings = bindings;
    this.nDims = nDims;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((bindings == null) ? 0 : bindings.hashCode());
    result = prime * result + nDims;
    result = prime * result + ((typeName == null) ? 0 : typeName.hashCode());
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
    if (typeName == null) {
      if (other.typeName != null) {
        return false;
      }
    } else if (!typeName.equals(other.typeName)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(typeName);
    if (!bindings.isEmpty()) {
      String pre = "<";
      for (TypeBinding b : bindings) {
        sb.append(pre);
        pre = ", ";
        sb.append(b);
      }
      sb.append('>');
    }
    for (int i = 0; i < nDims; ++i) {
      sb.append("[]");
    }
    return sb.toString();
  }


  /** A type to which a type parameter refers and any wildcard bounds. */
  public static final class TypeBinding {
    /** The wildcard bounds variance if any or INVARIANT if not. */
    public final Variance variance;
    /** The name of the referent type. */
    public final TypeSpecification typeSpec;

    /** */
    public TypeBinding(Variance variance, TypeSpecification typeSpec) {
      this.variance = variance;
      this.typeSpec = typeSpec;
    }

    /** */
    public TypeBinding(Name typeName) {
      this(Variance.INVARIANT, new TypeSpecification(typeName));
    }

    @Override
    public String toString() {
      switch (variance) {
        case EXTENDS:
          return "? extends " + typeSpec;
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
    return subst(substitutions.build());
  }

  /** Substitute for type parameters. */
  public TypeSpecification subst(
      Map<? super Name, ? extends TypeBinding> bindingMap) {
    TypeBinding b = bindingMap.get(typeName);
    if (b != null) {
      switch (b.variance) {
        case EXTENDS:
        case INVARIANT:
          return b.typeSpec;
        case SUPER:
          return JAVA_LANG_OBJECT;
      }
    }
    ImmutableList.Builder<TypeBinding> substs = null;
    for (int i = 0, n = bindings.size(); i < n; ++i) {
      TypeBinding binding = bindings.get(i);
      TypeSpecification bindingSpec = binding.typeSpec.subst(bindingMap);
      if (bindingSpec != binding.typeSpec) {
        binding = new TypeBinding(binding.variance, bindingSpec);
        if (substs == null) {
          substs = ImmutableList.builder();
          substs.addAll(bindings.subList(0, i));
        }
      }
      if (substs != null) {
        substs.add(binding);
      }
    }
    if (substs == null) { return this; }
    ImmutableList<TypeBinding> newBindings = substs.build();
    Preconditions.checkState(bindings.size() == newBindings.size());
    return new TypeSpecification(typeName, newBindings, nDims);
  }
}