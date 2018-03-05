package com.mikesamuel.cil.reflect;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;

/** Builds {@link TypeReference}s. */
public abstract class TypeReferenceBuilder<CHAIN_BACK> {
  private int arrayDepth;
  private final List<TypeBound> bounds = new ArrayList<>();

  protected abstract CHAIN_BACK t(TypeReference t);

  private CHAIN_BACK tBase(TypeReference base) {
    TypeReference t = (arrayDepth == 0 && bounds.isEmpty())
        ? base
        : new TypeReference(base.name, bounds, arrayDepth);
    return t(t);
  }

  /** */
  public CHAIN_BACK tVoid() {
    return t(TypeReference.T_VOID);
  }

  /** */
  public CHAIN_BACK tBoolean() {
    return tBase(TypeReference.T_BOOLEAN);
  }

  /** */
  public CHAIN_BACK tByte() {
    return tBase(TypeReference.T_BYTE);
  }

  /** */
  public CHAIN_BACK tChar() {
    return tBase(TypeReference.T_CHAR);
  }

  /** */
  public CHAIN_BACK tDouble() {
    return tBase(TypeReference.T_DOUBLE);
  }

  /** */
  public CHAIN_BACK tFloat() {
    return tBase(TypeReference.T_FLOAT);
  }

  /** */
  public CHAIN_BACK tInt() {
    return tBase(TypeReference.T_INT);
  }

  /** */
  public CHAIN_BACK tLong() {
    return tBase(TypeReference.T_LONG);
  }

  /** */
  public CHAIN_BACK tShort() {
    return tBase(TypeReference.T_SHORT);
  }

  /** */
  public CHAIN_BACK tObject(String name) {
    if (name.equals(TypeReference.T_OBJECT.name)) {
      return tBase(TypeReference.T_OBJECT);
    } else if (name.equals(TypeReference.T_STRING.name)) {
      return tBase(TypeReference.T_STRING);
    } else {
      return t(new TypeReference(name, bounds, arrayDepth));
    }
  }

  /**
   * Adds the given array depth count to the type eventually returned by the
   * {@code t}* call.
   */
  public TypeReferenceBuilder<CHAIN_BACK> array(int depth) {
    Preconditions.checkArgument(
        depth >= 0 && arrayDepth + depth >= arrayDepth);
    arrayDepth += depth;
    return this;
  }

  /**
   * A builder for a wildcard <tt>? extends</tt> parameter that
   * chains back to this.
   */
  public TypeReferenceBuilder<TypeReferenceBuilder<CHAIN_BACK>> pExtends() {
    return p(Variance.EXTENDS);
  }

  /**
   * A builder for a wildcard <tt>? super</tt> parameter that
   * chains back to this.
   */
  public TypeReferenceBuilder<TypeReferenceBuilder<CHAIN_BACK>> pSuper() {
    return p(Variance.SUPER);
  }

  /**
   * A builder for a non-wildcard parameter that chains back to this.
   */
  public TypeReferenceBuilder<TypeReferenceBuilder<CHAIN_BACK>> p() {
    return p(Variance.IS);
  }

  private TypeReferenceBuilder<TypeReferenceBuilder<CHAIN_BACK>> p(Variance v) {
    TypeReferenceBuilder<CHAIN_BACK> outer = this;
    return new TypeReferenceBuilder<TypeReferenceBuilder<CHAIN_BACK>>() {

      @SuppressWarnings("synthetic-access")
      @Override
      protected TypeReferenceBuilder<CHAIN_BACK> t(TypeReference t) {
        outer.bounds.add(new TypeBound(v, t));
        return outer;
      }

    };
  }
}
