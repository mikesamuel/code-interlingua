package com.mikesamuel.cil.expr;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableTable;
import com.google.common.collect.Table;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.FieldInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.ReflectionUtils;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;
import com.mikesamuel.cil.util.TriState;

/**
 * An interpolation context that uses
 */
public class InterpretationContextImpl
implements InterpretationContext<Object> {

  final Logger logger;
  final ClassLoader loader;
  final TypePool typePool;
  private TypeInfo thisType;
  private final Map<Name, Object> thisValues = new LinkedHashMap<>();
  private Supplier<SourcePosition> sourcePositionSupplier =
      Suppliers.ofInstance(null);

  @Override
  public TypePool getTypePool() {
    return typePool;
  }

  /** */
  public InterpretationContextImpl(
      Logger logger, ClassLoader loader, TypePool typePool) {
    this.logger = logger;
    this.loader = loader;
    this.typePool = typePool;
  }

  private void log(Level level, String msg, Throwable th) {
    LogUtils.log(logger, level, sourcePositionSupplier.get(), msg, th);
  }

  private Object error(String msg) {
    return error(msg, null);
  }

  private Object error(String msg, Throwable th) {
    LogUtils.log(logger, Level.SEVERE, sourcePositionSupplier.get(), msg, th);
    return ErrorValue.INSTANCE;
  }

  @Override
  public Logger getLogger() {
    return logger;
  }

  @Override
  public void setSourcePositionSupplier(
      Supplier<SourcePosition> newPositionSupplier) {
    this.sourcePositionSupplier =
        Preconditions.checkNotNull(newPositionSupplier);
  }

  @Override
  public Supplier<SourcePosition> getSourcePositionSupplier() {
    return this.sourcePositionSupplier;
  }

  @Override
  public Object errorValue() {
    return ErrorValue.INSTANCE;
  }

  @Override
  public boolean isErrorValue(@Nullable Object v) {
    return v instanceof ErrorValue;
  }

  @Override
  public @Nullable Object nullValue() {
    return null;
  }

  @Override
  public boolean isNullValue(@Nullable Object v) {
    return v == null;
  }

  @Override
  public TypeInfo getThisType() {
    return this.thisType;
  }

  @Override
  public @Nullable Object getThisValue(@Nullable Name className) {
    Object result = this.thisValues.get(className);
    if (result == null) {
      return ErrorValue.INSTANCE;
    } else {
      return result;
    }
  }

  @Override
  public void setThisType(TypeInfo newThisType) {
    this.thisType = newThisType;
  }

  @Override
  public void setThisValue(@Nullable Name className, Object thisValue) {
    thisValues.put(className, thisValue);
  }

  @Override
  public TriState toBoolean(@Nullable Object v) {
    if (v instanceof Boolean) {
      return ((Boolean) v).booleanValue() ? TriState.TRUE : TriState.FALSE;
    }
    return TriState.OTHER;
  }

  @Override
  public Optional<Integer> toInt(@Nullable Object v) {
    if (v instanceof Integer) {
      return Optional.of((Integer) v);
    }
    return Optional.absent();
  }

  @Override
  public Boolean from(boolean b) {
    return Boolean.valueOf(b);
  }

  @Override
  public Byte from(byte b) {
    return Byte.valueOf(b);
  }

  @Override
  public Short from(short s) {
    return Short.valueOf(s);
  }

  @Override
  public Character from(char c) {
    return Character.valueOf(c);
  }

  @Override
  public Integer from(int i) {
    return Integer.valueOf(i);
  }

  @Override
  public Long from(long j) {
    return Long.valueOf(j);
  }

  @Override
  public Float from(float f) {
    return Float.valueOf(f);
  }

  @Override
  public Double from(double d) {
    return Double.valueOf(d);
  }

  @Override
  public String from(String s) {
    return s;
  }

  @Override
  public Object sameReference(@Nullable Object u, @Nullable Object v) {
    if (isErrorValue(u) || isErrorValue(v)) { return ErrorValue.INSTANCE; }
    return u == v;
  }

  @Override
  public Object primitiveEquals(Object u, Object v) {
    if (isErrorValue(u) || isErrorValue(v)) { return ErrorValue.INSTANCE; }
    return Objects.equal(u, v);
  }

  enum NumericOperandType {
    INT,
    LONG,
    FLOAT,
    DOUBLE,;

    @SuppressWarnings("synthetic-access")
    public static NumericOperandType of(Number n) {
      return CL_TO_OP.get(n.getClass());
    }
  }

  enum PrimitiveType {
    VOID,
    BOOLEAN,
    BYTE,
    CHAR,
    SHORT,
    INT,
    LONG,
    FLOAT,
    DOUBLE,
  }

  private static @Nullable Number asNumber(@Nullable Object v) {
    if (v instanceof Number) { return (Number) v; }
    if (v instanceof Character) {
      return Short.valueOf((short) ((Character) v).charValue());
    }
    return null;
  }

  private static final ImmutableMap<Class<?>, NumericOperandType> CL_TO_OP =
      ImmutableMap.<Class<?>, NumericOperandType>builder()
      .put(Byte.class, NumericOperandType.INT)  // operands promoted to int
      .put(Character.class, NumericOperandType.INT)
      .put(Short.class, NumericOperandType.INT)
      .put(Integer.class, NumericOperandType.INT)
      .put(Long.class, NumericOperandType.LONG)
      .put(Float.class, NumericOperandType.FLOAT)
      .put(Double.class, NumericOperandType.DOUBLE)
      .build();

  private static final ImmutableMap<Class<?>, PrimitiveType> CL_TO_PRIM =
      ImmutableMap.<Class<?>, PrimitiveType>builder()
      .put(void.class, PrimitiveType.VOID)
      .put(boolean.class, PrimitiveType.BOOLEAN)
      .put(byte.class, PrimitiveType.BYTE)
      .put(char.class, PrimitiveType.CHAR)
      .put(short.class, PrimitiveType.SHORT)
      .put(int.class, PrimitiveType.INT)
      .put(long.class, PrimitiveType.LONG)
      .put(float.class, PrimitiveType.FLOAT)
      .put(double.class, PrimitiveType.DOUBLE)
      .build();

  private static NumericOperandType promotedType(Number m, Number n) {
    NumericOperandType mt = NumericOperandType.of(m);
    NumericOperandType nt = NumericOperandType.of(n);
    if (mt == null || nt == null) {
      return null;
    }
    int delta = mt.compareTo(nt);
    return delta < 0 ? nt : mt;
  }

  private Object convertForAssignment(@Nullable Object v, Class<?> primType) {
    PrimitiveType pt = Preconditions.checkNotNull(CL_TO_PRIM.get(primType));
    Number n;
    switch (pt) {
      case BOOLEAN:
        if (v instanceof Boolean) { return v; }
        break;
      case BYTE:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Byte ? v : n.byteValue();
        }
        break;
      case CHAR:
        if (v instanceof Character) { return v; }
        n = asNumber(v);
        if (n != null) {
          return (char) n.shortValue();
        }
        break;
      case DOUBLE:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Double ? v : n.doubleValue();
        }
        break;
      case FLOAT:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Float ? v : n.floatValue();
        }
        break;
      case INT:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Integer ? v : n.intValue();
        }
        break;
      case LONG:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Long ? v : n.longValue();
        }
        break;
      case SHORT:
        n = asNumber(v);
        if (n != null) {
          return n instanceof Short ? v : n.shortValue();
        }
        break;
      case VOID:
        break;
    }
    if (!isErrorValue(v)) {
      error("Cannot convert " + v + " to " + primType.getSimpleName());
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveLessThan(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:    return m.intValue()    < n.intValue();
        case LONG:   return m.longValue()   < n.longValue();
        case DOUBLE: return m.doubleValue() < n.doubleValue();
        case FLOAT:  return m.floatValue()  < n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " < " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveAddition(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:    return m.intValue()    + n.intValue();
        case LONG:   return m.longValue()   + n.longValue();
        case DOUBLE: return m.doubleValue() + n.doubleValue();
        case FLOAT:  return m.floatValue()  + n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " + " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveSubtraction(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:    return m.intValue()    - n.intValue();
        case LONG:   return m.longValue()   - n.longValue();
        case DOUBLE: return m.doubleValue() - n.doubleValue();
        case FLOAT:  return m.floatValue()  - n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " - " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object stringConcatenation(@Nullable Object u, @Nullable Object v) {
    if (isErrorValue(u) || isErrorValue(v)) {
      return ErrorValue.INSTANCE;
    }
    return new StringBuilder().append(u).append(v).toString();
  }

  @Override
  public Object primitiveModulus(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT: {
          int denominator = n.intValue();
          if (denominator != 0) {
            return m.intValue() % denominator;
          }
          break;
        }
        case LONG: {
          long denominator = n.longValue();
          if (denominator != 0) {
            return m.longValue() % denominator;
          }
          break;
        }
        case DOUBLE: return m.doubleValue() % n.doubleValue();
        case FLOAT:  return m.floatValue()  % n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " % " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveDivision(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT: {
          int denominator = n.intValue();
          if (denominator != 0) {
            return m.intValue() / denominator;
          }
          break;
        }
        case LONG: {
          long denominator = n.longValue();
          if (denominator != 0) {
            return m.longValue() / denominator;
          }
          break;
        }
        case DOUBLE: return m.doubleValue() / n.doubleValue();
        case FLOAT:  return m.floatValue()  / n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " / " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveOr(@Nullable Object u, @Nullable Object v) {
    if (u instanceof Boolean && v instanceof Boolean) {
      return ((Boolean) u) | ((Boolean) v);
    }
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:  return m.intValue()  | n.intValue();
        case LONG: return m.longValue() | n.longValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " | " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveAnd(@Nullable Object u, @Nullable Object v) {
    if (u instanceof Boolean && v instanceof Boolean) {
      return ((Boolean) u) & ((Boolean) v);
    }
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:  return m.intValue()  & n.intValue();
        case LONG: return m.longValue() & n.longValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " & " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveXor(@Nullable Object u, @Nullable Object v) {
    if (u instanceof Boolean && v instanceof Boolean) {
      return ((Boolean) u) ^ ((Boolean) v);
    }
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:  return m.intValue()  ^ n.intValue();
        case LONG: return m.longValue() ^ n.longValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " ^ " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveLshift(Object u, Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, m)) {
        case INT:  return m.intValue()  << n.intValue();
        case LONG: return m.longValue() << n.intValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " << " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveRshift(@Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, m)) {
        case INT:  return m.intValue()  >> n.intValue();
        case LONG: return m.longValue() >> n.intValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " >> " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveRushift(Object u, Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, m)) {
        case INT:  return m.intValue()  >>> n.intValue();
        case LONG: return m.longValue() >>> n.longValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " >>> " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveMultiplication(
      @Nullable Object u, @Nullable Object v) {
    Number m = asNumber(u);
    Number n = asNumber(v);
    if (m != null && n != null) {
      switch (promotedType(m, n)) {
        case INT:    return m.intValue()    * n.intValue();
        case LONG:   return m.longValue()   * n.longValue();
        case DOUBLE: return m.doubleValue() * n.doubleValue();
        case FLOAT:  return m.floatValue()  * n.floatValue();
      }
    }
    if (!(isErrorValue(u) || isErrorValue(v))) {
      error("Failed to compute " + u + " * " + v);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveNegation(@Nullable Object u) {
    Number n = asNumber(u);
    if (u != null) {
      switch (NumericOperandType.of(n)) {
        case INT:    return -n.intValue();
        case LONG:   return -n.longValue();
        case DOUBLE: return -n.doubleValue();
        case FLOAT:  return -n.floatValue();
      }
    }
    if (!isErrorValue(u)) {
      error("Failed to compute -" + u);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveLogicalNot(@Nullable Object u) {
    if (u instanceof Boolean) {
      return !((Boolean) u);
    }
    if (!isErrorValue(u)) {
      error("Failed to compute !" + u);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object primitiveBitwiseInvert(@Nullable Object u) {
    Number n = asNumber(u);
    if (u != null) {
      switch (NumericOperandType.of(n)) {
        case INT:  return ~n.intValue();
        case LONG: return ~n.longValue();
        case DOUBLE:
        case FLOAT:
          break;
      }
    }
    if (!isErrorValue(u)) {
      error("Failed to compute ~" + u);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public int arrayLength(Object v) {
    if (v != null && v.getClass().isArray()) {
      return Array.getLength(v);
    }
    return 0;
  }

  @Override
  public Object arrayGet(@Nullable Object arr, int index) {
    // Interpreter is responsible for checking bounds.
    if (arr != null && arr.getClass().isArray()) {
      return Array.get(arr, index);
    }
    if (!isErrorValue(arr)) {
      error("Failed to read arr[" + index + "]");
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object arraySet(
      @Nullable Object arr, int index, @Nullable Object newElement) {
    if (arr != null) {
      Class<?> arrType = arr.getClass();
      if (arrType.isArray()) {
        Class<?> componentType = arrType.getComponentType();
        if (componentType.isPrimitive()) {
          Object converted = convertForAssignment(newElement, componentType);
          if (!isErrorValue(converted)) {
            Array.set(arr, index, converted);
          }
          return converted;
        } else if (newElement == null || componentType.isInstance(newElement)) {
          Array.set(arr, index, isNullValue(newElement) ? null : newElement);
          return newElement;
        }
      }
    }
    if (!isErrorValue(arr)) {
      error("Failed to assign arr[" + index + "] = ...");
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object newArray(StaticType elementType, int length) {
    Optional<Class<?>> elementClass = classFor(elementType);
    if (elementClass.isPresent()) {
      return Array.newInstance(elementClass.get(), length);
    }
    return error("Failed to resolve class for " + elementType);
  }

  @Override
  public Object setField(
      FieldInfo field, @Nullable Object container, @Nullable Object newValue) {
    Optional<Field> fOpt = fieldFor(field);
    Throwable th = null;
    if (fOpt.isPresent()) {
      Field f = fOpt.get();
      try {
        f.set(container, newValue);
        return f.get(container);
      } catch (IllegalAccessException | IllegalArgumentException
               | NullPointerException ex) {
        // return error below
        th = ex;
      }
    }
    return error("Failed to set field " + field.canonName, th);
  }

  @Override
  public Object setStaticField(FieldInfo field, @Nullable Object newValue) {
    Optional<Field> fOpt = fieldFor(field);
    Throwable th = null;
    if (fOpt.isPresent()) {
      Field f = fOpt.get();
      try {
        f.set(null, newValue);
        return f.get(null);
      } catch (IllegalAccessException | NullPointerException ex) {
        // return error below
        th = ex;
      }
    }
    return error("Failed to set field " + field.canonName, th);
  }

  @Override
  public Object getField(FieldInfo field, @Nullable Object container) {
    Optional<Field> fOpt = fieldFor(field);
    Throwable th = null;
    if (fOpt.isPresent()) {
      Field f = fOpt.get();
      try {
        return f.get(container);
      } catch (IllegalAccessException | IllegalArgumentException
               | NullPointerException ex) {
        // return error below
        th = ex;
      }
    }
    return error("Failed to read field " + field.canonName, th);
  }

  @Override
  public Object getFieldDynamic(String key, @Nullable Object container) {
    if (container == null) {
      return error("Null dereferenced via (null)." + key);
    }
    Class<?> cl = container.getClass();
    if ("length".equals(key) && cl.isArray()) {
      return arrayLength(container);
    }
    Field f;
    try {
      f = cl.getField(key);
    } catch (NoSuchFieldException ex) {
      return error(
          "Missing field " + key + " on " + cl.getSimpleName(),
          ex);
    }
    try {
      return f.get(container);
    } catch (IllegalArgumentException ex) {
      throw new AssertionError(null, ex);
    } catch (IllegalAccessException ex) {
      return error(
          "Access denied to field " + key + " on " + cl.getSimpleName(),
          ex);
    }
  }

  @Override
  public Object getStaticField(FieldInfo field) {
    Optional<Field> fOpt = fieldFor(field);
    Throwable th = null;
    if (fOpt.isPresent()) {
      Field f = fOpt.get();
      try {
        return f.get(null);
      } catch (IllegalAccessException | NullPointerException ex) {
        // return error below
        th = ex;
      }
    }
    return error("Failed to read field " + field.canonName, th);
  }

  @Override
  public Object newInstance(
      CallableInfo constructor, List<? extends Object> constructorActuals) {
    Optional<Executable> ctorOpt = executableFor(constructor);
    if (ctorOpt.isPresent()) {
      Executable ctor = ctorOpt.get();
      if (ctor instanceof Constructor) {
        Object[] actualsArray = constructorActuals.toArray(
            new Object[constructorActuals.size()]);
        try {
          return ((Constructor<?>) ctor).newInstance(actualsArray);
        } catch (@SuppressWarnings("unused")
                 InstantiationException | IllegalAccessException
                 | IllegalArgumentException e) {
          // return error below
        } catch (InvocationTargetException ex) {
          log(Level.SEVERE, "Failure in constructor", ex.getTargetException());
        }
      }
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object invokeVirtual(
      CallableInfo method,
      @Nullable Object receiver, List<? extends Object> actuals) {
    Optional<Executable> execOpt = executableFor(method);
    if (execOpt.isPresent()) {
      Executable exec = execOpt.get();
      if (exec instanceof Method) {
        Object[] actualsArray = actuals.toArray(new Object[actuals.size()]);
        try {
          return ((Method) exec).invoke(receiver, actualsArray);
        } catch (IllegalAccessException | IllegalArgumentException
                 | NullPointerException ex) {
          // return error below
          log(Level.SEVERE, "Failure in method", ex);
        } catch (InvocationTargetException ex) {
          log(Level.SEVERE, "Failure in method", ex.getTargetException());
        }
      } else {
        log(Level.SEVERE, "Executable " + exec + " is not a method", null);
      }
    } else {
      log(Level.SEVERE, "Failed to find executable for " + method.canonName,
          null);
    }
    return ErrorValue.INSTANCE;
  }

  @Override
  public Object invokeStatic(
      CallableInfo method, List<? extends Object> actuals) {
    Optional<Executable> execOpt = executableFor(method);
    if (execOpt.isPresent()) {
      Executable exec = execOpt.get();
      if (exec instanceof Method) {
        Object[] actualsArray = actuals.toArray(new Object[actuals.size()]);
        try {
          return ((Method) exec).invoke(null, actualsArray);
        } catch (@SuppressWarnings("unused")
                 IllegalAccessException | IllegalArgumentException
                 | NullPointerException ex) {
          // return error below
        } catch (InvocationTargetException ex) {
          log(Level.SEVERE, "Failure in static method",
              ex.getTargetException());
        }
      }
    }
    return ErrorValue.INSTANCE;
  }


  @Override
  public Object invokeDynamic(
      String methodName, Object receiver, List<? extends Object> actuals) {
    return this.error(
        "Could not invoke " + methodName + "(...) on " + receiver + " with "
        + actuals);
  }



  private static final ImmutableMap<Class<?>, Name> PRIM_NAMES;
  private static final ImmutableMap<Name, Class<?>> PRIM_CLASSES;
  static {
    ImmutableMap.Builder<Class<?>, Name> a = ImmutableMap.builder();
    ImmutableMap.Builder<Name, Class<?>> b = ImmutableMap.builder();
    for (StaticType.PrimitiveType pt : StaticType.PRIMITIVE_TYPES) {
      a.put(pt.primitiveClass, pt.typeSpecification.rawName);
      b.put(pt.typeSpecification.rawName, pt.primitiveClass);
    }
    PRIM_NAMES = a.build();
    PRIM_CLASSES = b.build();
  }


  private final LoadingCache<TypeSpecification, Optional<Class<?>>> classes =
      CacheBuilder.newBuilder().build(
          new CacheLoader<TypeSpecification, Optional<Class<?>>>() {

            @SuppressWarnings("synthetic-access")
            @Override
            public Optional<Class<?>> load(TypeSpecification type) {
              Class<?> cl = PRIM_CLASSES.get(type.rawName);
              if (cl == null) {
                Preconditions.checkState(type.rawName.type == Name.Type.CLASS);
                try {
                  cl = loader.loadClass(type.rawName.toBinaryName());
                } catch (ClassNotFoundException ex) {
                  log(Level.SEVERE, "Failed to find class for " + type, ex);
                  return Optional.absent();
                }
              }
              for (int nDims = type.nDims; --nDims >= 0;) {
                cl = Array.newInstance(cl, 0).getClass();
              }
              return Optional.of(cl);
            }

          });

  private Optional<Class<?>> classFor(StaticType type) {
    return classFor(type.typeSpecification);
  }

  private Optional<Class<?>> classFor(TypeSpecification type) {
    try {
      return classes.get(type);
    } catch (ExecutionException ex) {
      log(Level.SEVERE, "Failed to find class for " + type, ex);
      return Optional.absent();
    }
  }

  private final LoadingCache<Name, Optional<Field>> fields =
      CacheBuilder.newBuilder().build(
          new CacheLoader<Name, Optional<Field>>() {

            @SuppressWarnings("synthetic-access")
            @Override
            public Optional<Field> load(Name fn) {
              Preconditions.checkState(
                  fn.type == Name.Type.FIELD
                  && fn.parent.type == Name.Type.CLASS);
              Optional<Class<?>> declaringClass = classFor(
                  TypeSpecification.unparameterized(fn.parent));
              if (declaringClass.isPresent()) {
                try {
                  return Optional.of(
                      declaringClass.get().getDeclaredField(fn.identifier));
                } catch (NoSuchFieldException ex) {
                  log(Level.SEVERE, "Failed to find field " + fn, ex);
                  return Optional.absent();
                }
              }
              return Optional.absent();
            }

          });

  private Optional<Field> fieldFor(FieldInfo info) {
    try {
      return fields.get(info.canonName);
    } catch (ExecutionException ex) {
      log(Level.SEVERE, "Failed to find field " + info.canonName, ex);
      return Optional.absent();
    }
  }

  /** class name -> method name -> descriptor -> executable */
  private final
  LoadingCache<Name, Table<String, MethodDescriptor, Executable>> executables =
      CacheBuilder.newBuilder().build(
          new CacheLoader<Name, Table<String, MethodDescriptor, Executable>>() {

            @SuppressWarnings("synthetic-access")
            @Override
            public Table<String, MethodDescriptor, Executable> load(Name nm) {
              Preconditions.checkState(
                  nm.type == Name.Type.METHOD
                  && nm.parent.type == Name.Type.CLASS);
              Optional<Class<?>> declaringClass = classFor(
                  TypeSpecification.unparameterized(nm.parent));
              ImmutableTable.Builder<String, MethodDescriptor, Executable> b =
                  ImmutableTable.builder();
              if (declaringClass.isPresent()) {
                for (Method m : declaringClass.get().getDeclaredMethods()) {
                  b.put(m.getName(), ReflectionUtils.descriptorFor(m), m);
                }
                for (Constructor<?> c
                     : declaringClass.get().getDeclaredConstructors()) {
                  b.put("<init>", ReflectionUtils.descriptorFor(c), c);
                }
              }
              return b.build();
            }

          });

  private Optional<Executable> executableFor(CallableInfo info) {
    try {
      return Optional.fromNullable(
          executables.get(info.canonName).get(
              info.canonName.identifier, info.getDescriptor()));
    } catch (ExecutionException ex) {
      log(Level.SEVERE, "Failed to find field " + info.canonName, ex);
      return Optional.absent();
    }

  }

  private static final
  ImmutableMap<StaticType, Function<Object, Object>> COERCIONS =
      ImmutableMap.<StaticType, Function<Object, Object>>builder()
      .put(StaticType.T_BOOLEAN, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Boolean) { return x; }
          return Boolean.FALSE;  // Zero
        }

      })
      .put(StaticType.T_BYTE, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).byteValue(); }
          if (x instanceof Character) {
            return (byte) ((Character) x).charValue();
          }
          return (byte) 0;
        }

      })
      .put(StaticType.T_SHORT, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).shortValue(); }
          if (x instanceof Character) {
            return (short) ((Character) x).charValue();
          }
          return (short) 0;
        }

      })
      .put(StaticType.T_CHAR, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Character) { return x; }
          if (x instanceof Number) { return (char) ((Number) x).shortValue(); }
          return (char) 0;
        }

      })
      .put(StaticType.T_INT, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).intValue(); }
          if (x instanceof Character) {
            return (int) ((Character) x).charValue();
          }
          return (int) 0;
        }

      })
      .put(StaticType.T_LONG, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).longValue(); }
          if (x instanceof Character) {
            return (long) ((Character) x).charValue();
          }
          return (long) 0;
        }

      })
      .put(StaticType.T_FLOAT, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).floatValue(); }
          if (x instanceof Character) {
            return (float) ((Character) x).charValue();
          }
          return (float) 0;
        }

      })
      .put(StaticType.T_DOUBLE, new Function<Object, Object>() {

        @Override
        public Object apply(Object x) {
          if (x instanceof Number) { return ((Number) x).doubleValue(); }
          if (x instanceof Character) {
            return (double) ((Character) x).charValue();
          }
          return (double) 0;
        }

      })
      .build();

  @Override
  public Function<Object, Object> coercion(StaticType targetType) {
    Function<Object, Object> coercion = COERCIONS.get(targetType);
    if (typePool.T_NULL.equals(targetType)) {
      return Functions.constant(null);
    }
    return coercion != null ? coercion : Functions.identity();
  }

  @Override
  public StaticType runtimeType(@Nullable Object v) {
    if (isNullValue(v)) {
      return typePool.T_NULL;
    }
    if (isErrorValue(v)) {
      return StaticType.ERROR_TYPE;
    }
    int nDims = 0;
    Class<?> cl = v.getClass();
    while (cl.isArray()) {
      ++nDims;
      cl = cl.getComponentType();
    }
    Name typeName = toTypeName(cl);
    TypeSpecification spec = TypeSpecification.unparameterized(typeName)
        .withNDims(nDims);
    return typePool.type(spec, null, logger);
  }

  private static Name toTypeName(Class<?> cl) {
    if (cl.isPrimitive()) {
      return PRIM_NAMES.get(cl);
    }
    Class<?> outer = cl.getEnclosingClass();
    if (outer != null) {
      return toTypeName(outer).child(cl.getSimpleName(), Name.Type.CLASS);
    }
    String className = cl.getName();
    Name typeName = Name.DEFAULT_PACKAGE;
    int start = 0;
    for (int i = 0, limit = className.length(); i < limit; ++i) {
      char ch = className.charAt(i);
      if (ch == '.') {
        typeName = typeName.child(
            className.substring(start, i), Name.Type.PACKAGE);
        start = i + 1;
      }
    }
    return typeName.child(className.substring(start), Name.Type.CLASS);
  }
}
