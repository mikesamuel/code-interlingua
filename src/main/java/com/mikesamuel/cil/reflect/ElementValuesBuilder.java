package com.mikesamuel.cil.reflect;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** A builder for a list of element values. */
public abstract class ElementValuesBuilder<T> {

  protected final ImmutableList.Builder<Value> values =
      ImmutableList.builder();

  /** Stores the values. */
  public abstract T valuesComplete();

  private void addValue(Value v) {
    this.values.add(Preconditions.checkNotNull(v));
  }

  /** Adds a boolean value. */
  public ElementValuesBuilder<T> booleanValue(boolean b) {
    addValue(new SimpleValue(
        TypeReference.T_BOOLEAN, Boolean.toString(b)));
    return this;
  }

  /** Adds a byte value. */
  public ElementValuesBuilder<T> byteValue(byte b) {
    addValue(new SimpleValue(
        TypeReference.T_BYTE, Byte.toString(b)));
    return this;
  }

  /** Adds a char value. */
  public ElementValuesBuilder<T> charValue(char b) {
    addValue(new SimpleValue(
        TypeReference.T_CHAR, Character.toString(b)));
    return this;
  }

  /** Adds a double value. */
  public ElementValuesBuilder<T> doubleValue(double b) {
    addValue(new SimpleValue(
        TypeReference.T_DOUBLE, Double.toString(b)));
    return this;
  }

  /** Adds a float value. */
  public ElementValuesBuilder<T> floatValue(float b) {
    addValue(new SimpleValue(
        TypeReference.T_FLOAT, Float.toString(b)));
    return this;
  }

  /** Adds a int value. */
  public ElementValuesBuilder<T> intValue(int b) {
    addValue(new SimpleValue(
        TypeReference.T_INT, Integer.toString(b)));
    return this;
  }

  /** Adds a long value. */
  public ElementValuesBuilder<T> longValue(long b) {
    addValue(new SimpleValue(
        TypeReference.T_LONG, Long.toString(b)));
    return this;
  }

  /** Adds a short value. */
  public ElementValuesBuilder<T> shortValue(short b) {
    addValue(new SimpleValue(
        TypeReference.T_SHORT, Short.toString(b)));
    return this;
  }

  /** Adds a string value. */
  public ElementValuesBuilder<T> stringValue(String b) {
    addValue(new SimpleValue(
        TypeReference.T_STRING, b));
    return this;
  }

  /** Adds an enum value. */
  public ElementValuesBuilder<T> enumValue(
      String enumName, String constantName) {
    addValue(new SimpleValue(
        new TypeReference(enumName),
        enumName + "." + constantName));
    return this;
  }

  /**
   * Returns a builder for an array value which will be added on
   * a call to its {@link #valuesComplete} method.
   */
  public ElementValuesBuilder<ElementValuesBuilder<T>> arrayValues() {
    final ElementValuesBuilder<T> outer = this;
    return new ElementValuesBuilder<ElementValuesBuilder<T>>() {
      @SuppressWarnings("synthetic-access")
      @Override
      public ElementValuesBuilder<T> valuesComplete() {
        outer.addValue(new ArrayValue(values.build()));
        return outer;
      }
    };
  }
}
