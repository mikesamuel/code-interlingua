package com.mikesamuel.cil.reflect;

/** A simple value. */
public final class SimpleValue extends Value {
  /**
   * The type of the value.
   */
  public final TypeReference type;
  /**
   * For primitive values, a literal token that parses to the value
   * using the wrapper class's {@code .valueOf(String)} method.
   * For strings, the text value, not escaped.
   * For enum values, the enum name.
   */
  public final String valueString;

  SimpleValue(TypeReference type, String valueString) {
    this.type = type;
    this.valueString = valueString;
  }

  @Override
  public String toString() {
    return valueString;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof SimpleValue)) { return false; }
    SimpleValue that = (SimpleValue) o;
    return this.valueString.equals(that.valueString)
        && this.type.equals(that.type);
  }

  @Override
  public int hashCode() {
    return type.hashCode() + 31 * valueString.hashCode();
  }
}