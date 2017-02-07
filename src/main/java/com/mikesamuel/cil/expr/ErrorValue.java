package com.mikesamuel.cil.expr;

/**
 * A singleton value of type
 * {@link com.mikesamuel.cil.ast.meta.StaticType#ERROR_TYPE}.
 */
final class ErrorValue {

  private ErrorValue() {
  }

  /** Singleton */
  static final ErrorValue INSTANCE = new ErrorValue();

  @Override
  public String toString() {
    return "[Error]";
  }
}
