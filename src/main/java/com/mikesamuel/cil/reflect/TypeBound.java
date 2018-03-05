package com.mikesamuel.cil.reflect;

/** A type bound in a type actual list. */
public final class TypeBound {
  /** The kind of bound. */
  public final Variance variance;
  /** The limit of the bound. */
  public final TypeReference typeReference;

  TypeBound(Variance variance, TypeReference typeReference) {
    this.variance = variance;
    this.typeReference = typeReference;
  }

  @Override
  public String toString() {
    switch (variance) {
      case IS:
        return typeReference.toString();
      case EXTENDS:
        return "? extends " + typeReference;
      case SUPER:
        return "? super " + typeReference;
    }
    throw new AssertionError(variance);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((typeReference == null) ? 0 : typeReference.hashCode());
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
    TypeBound other = (TypeBound) obj;
    if (typeReference == null) {
      if (other.typeReference != null) {
        return false;
      }
    } else if (!typeReference.equals(other.typeReference)) {
      return false;
    }
    if (variance != other.variance) {
      return false;
    }
    return true;
  }
}