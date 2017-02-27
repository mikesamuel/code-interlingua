package com.mikesamuel.cil.expr;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.util.TriState;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * @param <VALUE> the type of a value instance.
 */
public interface InterpretationContext<VALUE> {

  /** The type pool used to resolve runtime types. */
  TypePool getTypePool();

  /**
   * A logger to which any operation that returns an error value given non-error
   * inputs should report.
   */
  Logger getLogger();

  /**
   * A position supplier that may be used to associate source positions
   * with log messages when logging interpretation failures.
   */
  Supplier<SourcePosition> getSourcePositionSupplier();

  /**
   * Sets a position supplier that may be used to associate source positions
   * with log messages when logging interpretation failures.
   */
  void setSourcePositionSupplier(Supplier<SourcePosition> newPositionSupplier);

  /** An error value of the given kind. */
  VALUE errorValue();

  /** True iff the given value is an {@linkplain #errorValue error value}. */
  boolean isErrorValue(VALUE v);

  /** A null reference value. */
  VALUE nullValue();

  /** True iff the given value is an {@linkplain #nullValue null value}. */
  boolean isNullValue(VALUE v);

  /** The type of {@code this}. */
  TypeInfo getThisType();

  /** If className is {@code Foo} then the value of {@code Foo.this}. */
  VALUE getThisValue(@Nullable Name className);

  /** @see #getThisValue */
  void setThisValue(@Nullable Name className, VALUE thisValue);

  /** @see #getThisType */
  void setThisType(TypeInfo info);

  /**
   * @return other when v is not convertible to boolean.
   */
  TriState toBoolean(VALUE v);

  /**
   * The corresponding int
   */
  Optional<Integer> toInt(VALUE v);

  /**
   * Wraps a primitive.
   */
  VALUE from(boolean b);

  /**
   * Wraps a primitive.
   */
  VALUE from(byte s);

  /**
   * Wraps a primitive.
   */
  VALUE from(short s);

  /**
   * Wraps a primitive.
   */
  VALUE from(char c);

  /**
   * Wraps a primitive.
   */
  VALUE from(int i);

  /**
   * Wraps a primitive.
   */
  VALUE from(long j);

  /**
   * Wraps a primitive.
   */
  VALUE from(float f);

  /**
   * Wraps a primitive.
   */
  VALUE from(double d);

  /**
   * Value corresponding to a string literal.
   */
  VALUE from(String s);

  /**
   * A function that makes a best effort to coerce to values of the given type.
   * A null input should always coerce to the zero value for the type.
   */
  Function<VALUE, VALUE> coercion(StaticType targetType);

  /** Reference {@code ==} */
  VALUE sameReference(VALUE u, VALUE v);

  /** Primitive {@code ==} that performs implicit conversions. */
  VALUE primitiveEquals(VALUE u, VALUE v);

  /** Primitive {@code <} that performs implicit conversions. */
  VALUE primitiveLessThan(VALUE u, VALUE v);

  /** Numeric binary {@code +} that performs implicit conversions. */
  VALUE primitiveAddition(VALUE u, VALUE v);

  /** Numeric binary {@code -} that performs implicit conversions. */
  VALUE primitiveSubtraction(VALUE u, VALUE v);

  /** {@code +} that performs implicit {@link String#valueOf} coercion. */
  VALUE stringConcatenation(VALUE u, VALUE v);

  /** Primitive {@code %} that performs implicit conversions. */
  VALUE primitiveModulus(VALUE u, VALUE v);

  /**
   * Primitive {@code /} that yields error value on division by zero.
   *
   * @return on division by zero may return any of NaN, +-Infinity, or
   *     {@link #errorValue}.
   */
  VALUE primitiveDivision(VALUE u, VALUE v);

  /** Primitive {@code |} that performs implicit conversions. */
  VALUE primitiveOr(VALUE u, VALUE v);

  /** Primitive {@code &} that performs implicit conversions. */
  VALUE primitiveAnd(VALUE u, VALUE v);

  /** Primitive {@code ^} that performs implicit conversions. */
  VALUE primitiveXor(VALUE u, VALUE v);

  /** Primitive {@code <<} that performs implicit conversions. */
  VALUE primitiveLshift(VALUE u, VALUE v);

  /** Primitive {@code >>} that performs implicit conversions. */
  VALUE primitiveRshift(VALUE u, VALUE v);

  /** Primitive {@code >>>} that performs implicit conversions. */
  VALUE primitiveRushift(VALUE u, VALUE v);

  /** Primitive {@code *} that performs implicit conversions. */
  VALUE primitiveMultiplication(VALUE u, VALUE v);

  /** Primitive {@code -} that performs implicit conversions. */
  VALUE primitiveNegation(VALUE u);

  /** Primitive {@code !} that performs implicit conversions. */
  VALUE primitiveLogicalNot(VALUE u);

  /** Primitive {@code ~} that performs implicit conversions. */
  VALUE primitiveBitwiseInvert(VALUE u);

  /** Array {@code .length}. */
  int arrayLength(VALUE v);

  /** Array {@code [...]}. */
  VALUE arrayGet(VALUE arr, int index);

  /** Array element assignment. */
  VALUE arraySet(VALUE arr, int index, VALUE newElement);

  /** Allocates a new array filled with its element type's zero values. */
  VALUE newArray(StaticType elementType, int length);

  /**
   * Sets an instance field.
   *
   * @return the value after coercion to the field's type.
   */
  VALUE setField(FieldInfo field, VALUE container, VALUE newValue);

  /**
   * Sets a static field.
   *
   * @return the value after coercion to the field's type.
   */
  VALUE setStaticField(FieldInfo field, VALUE newValue);

  /**
   * Gets an instance field.
   */
  VALUE getField(FieldInfo field, VALUE container);

  /**
   * Gets a field value given the field's name.
   */
  VALUE getFieldDynamic(String key, VALUE container);

  /**
   * Gets a static field.
   */
  VALUE getStaticField(FieldInfo field);

  /**
   * Allocates and constructs an instance of a concrete class using the given
   * constructor.
   */
  VALUE newInstance(
      CallableInfo constructor, List<? extends VALUE> constructorActuals);

  /**
   * Invokes the given method with receiver as this,
   * passing the actual arguments.
   *
   * @return on failure to invoke, an error value is returned.
   */
  VALUE invokeVirtual(
      CallableInfo method, VALUE receiver, List<? extends VALUE> actuals);

  /**
   * Invokes the given method with receiver as this,
   * passing the actual arguments.
   *
   * @return on failure to invoke, an error value is returned.
   */
  VALUE invokeStatic(CallableInfo method, List<? extends VALUE> actuals);

  /**
   * Looks up a method with the given name using receiver's runtime type,
   * and passes the actual arguments.
   * <p>
   * The name is similar to a JVM bytecode, but this is only tangentially
   * related to that bytecode.
   *
   * @return on failure to invoke, an error value is returned.
   */
  VALUE invokeDynamic(
      String methodName, VALUE receiver, List<? extends VALUE> actuals);

  /**
   * The runtime type of the given value.
   */
  StaticType runtimeType(VALUE v);

  /** True iff the completion is a normal, non-error result. */
  default boolean completedNormallyWithoutError(Completion<VALUE> c) {
    return c.kind == Completion.Kind.NORMAL && !isErrorValue(c.value);
  }

}