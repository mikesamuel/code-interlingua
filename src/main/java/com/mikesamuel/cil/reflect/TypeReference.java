package com.mikesamuel.cil.reflect;

import com.google.common.collect.ImmutableList;

/** Enough metadata to identify a (possibly generic) Java type. */
public final class TypeReference {
  /** Constant type reference to {@code void}. */
  public static final TypeReference T_VOID = new TypeReference(
      "/java/lang/Void.TYPE");
  /** Constant type reference to {@code boolean}. */
  public static final TypeReference T_BOOLEAN = new TypeReference(
      "/java/lang/Boolean.TYPE");
  /** Constant type reference to {@code byte}. */
  public static final TypeReference T_BYTE = new TypeReference(
      "/java/lang/Byte.TYPE");
  /** Constant type reference to {@code char}. */
  public static final TypeReference T_CHAR = new TypeReference(
      "/java/lang/Character.TYPE");
  /** Constant type reference to {@code double}. */
  public static final TypeReference T_DOUBLE = new TypeReference(
      "/java/lang/Double.TYPE");
  /** Constant type reference to {@code float}. */
  public static final TypeReference T_FLOAT = new TypeReference(
      "/java/lang/Float.TYPE");
  /** Constant type reference to {@code int}. */
  public static final TypeReference T_INT = new TypeReference(
      "/java/lang/Integer.TYPE");
  /** Constant type reference to {@code long}. */
  public static final TypeReference T_LONG = new TypeReference(
      "/java/lang/Long.TYPE");
  /** Constant type reference to {@code short}. */
  public static final TypeReference T_SHORT = new TypeReference(
      "/java/lang/Short.TYPE");

  /** Constant type reference to {@code String}. */
  public static final TypeReference T_STRING = new TypeReference(
      "/java/lang/String");
  /** Constant type reference to {@code Object}. */
  public static final TypeReference T_OBJECT = new TypeReference(
      "/java/lang/Object");

  /** The name of the raw type.  May be a field name for primitive types. */
  public final String name;
  /** Any type bounds. */
  public final ImmutableList<TypeBound> bounds;
  /** The depth of the array.  Zero for non-array types. */
  public final int arrayDepth;

  TypeReference(String name) {
    this(name, ImmutableList.of(), 0);
  }

  TypeReference(String name, Iterable<? extends TypeBound> bounds) {
    this(name, bounds, 0);
  }

  TypeReference(String name, Iterable<? extends TypeBound> bounds, int arrayDepth) {
    this.name = name;
    this.bounds = ImmutableList.copyOf(bounds);
    this.arrayDepth = arrayDepth;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + arrayDepth;
    result = prime * result + ((bounds == null) ? 0 : bounds.hashCode());
    result = prime * result + ((name == null) ? 0 : name.hashCode());
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
    TypeReference other = (TypeReference) obj;
    if (arrayDepth != other.arrayDepth) {
      return false;
    }
    if (bounds == null) {
      if (other.bounds != null) {
        return false;
      }
    } else if (!bounds.equals(other.bounds)) {
      return false;
    }
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    if (arrayDepth == 0 && bounds.isEmpty()) {
      return name;
    }
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    if (!bounds.isEmpty()) {
      sb.append('<');
      String sep = "";
      for (TypeBound bound : bounds) {
        sb.append(sep).append(bound);
        sep = ", ";
      }
      sb.append('>');
    }
    for (int i = 0; i < arrayDepth; ++i) {
      sb.append("[]");
    }
    return sb.toString();
  }
}
