package com.mikesamuel.cil.reflect;

import com.google.common.collect.ImmutableList;

/** A value that is an array of values. */
public final class ArrayValue extends Value {
  /** The values of individual elements in order. */
  public final ImmutableList<Value> values;

  ArrayValue(Iterable<? extends Value> values) {
    this.values = ImmutableList.copyOf(values);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('{');
    String sep = " ";
    for (Value v : values) {
      sb.append(sep).append(v);
      sep = ", ";
    }
    if (!" ".equals(sep)) {
      sb.append(' ');
    }
    sb.append('}');
    return sb.toString();
  }

  @Override
  public boolean equals(Object that) {
    if (!(that instanceof ArrayValue)) { return false; }
    return this.values.equals(((ArrayValue) that).values);
  }

  @Override
  public int hashCode() {
    return 0x6e3301ab ^ values.hashCode();
  }
}