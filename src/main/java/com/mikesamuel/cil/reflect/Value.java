package com.mikesamuel.cil.reflect;

/** Encapsulates an enum parameter value. */
public abstract class Value {
  @Override public abstract String toString();
  @Override public abstract boolean equals(Object that);
  @Override public abstract int hashCode();
}