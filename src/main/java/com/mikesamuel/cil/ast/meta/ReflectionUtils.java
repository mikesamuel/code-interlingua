package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

/**
 * Utilities for dealing with {@code java.lang.reflect.*}.
 */
public final class ReflectionUtils {

  private ReflectionUtils() {
    // Static API
  }

  /**
   * A JVM method descriptor for the given executable.
   * <p>
   * Constructors are treated as equivalent to the special method {@code <init>}
   * with {@code void} return type.
   */
  public static MethodDescriptor descriptorFor(Executable e) {
    return descriptorFor(
        e instanceof Method ? ((Method) e).getReturnType() : void.class,
        Arrays.asList(e.getParameterTypes()));
  }

  /**
   * A JVM method descriptor for the given return type and formal types.
   */
  public static MethodDescriptor descriptorFor(
      Class<?> returnType, Iterable<? extends Class<?>> formalTypes) {
    MethodDescriptor.Builder b = MethodDescriptor.builder();
    for (Class<?> formalType : formalTypes) {
      Class<?> ft = formalType;
      int nDims = 0;
      while (ft.isArray()) {
        ft = ft.getComponentType();
        ++nDims;
      }
      b.addFormalParameter(nameForType(ft), nDims);
    }

    Class<?> rt = returnType;
    int nDims = 0;
    while (rt.isArray()) {
      rt = rt.getComponentType();
      ++nDims;
    }
    b.withReturnType(nameForType(rt), nDims);
    return b.build();
  }

  private static Name nameForType(Class<?> t) {
    Name nm = PRIMITIVE_FIELD_TYPES.get(t);
    if (nm != null) { return nm; }
    return nameForClass(t);
  }

  static Name nameForClass(Class<?> cl) {
    Preconditions.checkArgument(!cl.isPrimitive() && !cl.isArray(), cl);
    String simpleName;
    if (cl.isAnonymousClass()) {
      String binaryName = cl.getName();
      // The ordinal name like $1
      simpleName = binaryName.substring(binaryName.lastIndexOf('$') + 1);
    } else {
      simpleName = cl.getSimpleName();
    }
    Name parent;
    Class<?> outer = cl.getEnclosingClass();
    if (outer != null) {
      parent = nameForClass(outer);
    } else {
      String cn = cl.getCanonicalName();
      int lastDot = cn.lastIndexOf('.');
      Name pkg = Name.DEFAULT_PACKAGE;
      int pos = 0;
      while (pos <= lastDot) {
        int nextDot = cn.indexOf('.', pos);
        pkg = pkg.child(cn.substring(pos, nextDot), Name.Type.PACKAGE);
        pos = nextDot + 1;
      }
      parent = pkg;
    }
    return parent.child(simpleName, Name.Type.CLASS);
  }

  private static final ImmutableMap<Class<?>, Name> PRIMITIVE_FIELD_TYPES
      = ImmutableMap.<Class<?>, Name>builder()
      .put(Void.TYPE, StaticType.T_VOID.typeSpecification.rawName)
      .put(Boolean.TYPE, StaticType.T_BOOLEAN.typeSpecification.rawName)
      .put(Byte.TYPE, StaticType.T_BYTE.typeSpecification.rawName)
      .put(Character.TYPE, StaticType.T_CHAR.typeSpecification.rawName)
      .put(Double.TYPE, StaticType.T_DOUBLE.typeSpecification.rawName)
      .put(Float.TYPE, StaticType.T_FLOAT.typeSpecification.rawName)
      .put(Integer.TYPE, StaticType.T_INT.typeSpecification.rawName)
      .put(Long.TYPE, StaticType.T_LONG.typeSpecification.rawName)
      .put(Short.TYPE, StaticType.T_SHORT.typeSpecification.rawName)
      .build();

}
