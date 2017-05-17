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
  public static String descriptorFor(Executable e) {
    return descriptorFor(
        e instanceof Method ? ((Method) e).getReturnType() : void.class,
        Arrays.asList(e.getParameterTypes()));
  }

  /**
   * A JVM method descriptor for the given return type and formal types.
   */
  public static String descriptorFor(
      Class<?> returnType, Iterable<? extends Class<?>> formalTypes) {
    StringBuilder sb = new StringBuilder();
    sb.append('(');
    for (Class<?> formalType : formalTypes) {
      appendTypeDescriptor(sb, formalType);
    }
    sb.append(')');
    appendTypeDescriptor(sb, returnType);
    return sb.toString();
  }

  private static final ImmutableMap<Class<?>, Character> PRIMITIVE_FIELD_TYPES
      = ImmutableMap.<Class<?>, Character>builder()
      .put(Void.TYPE, 'V')
      .put(Boolean.TYPE, 'Z')
      .put(Byte.TYPE, 'B')
      .put(Character.TYPE, 'C')
      .put(Double.TYPE, 'D')
      .put(Float.TYPE, 'F')
      .put(Integer.TYPE, 'I')
      .put(Long.TYPE, 'J')
      .put(Short.TYPE, 'S')
      .build();

  private static void appendTypeDescriptor(StringBuilder sb, Class<?> t) {
    Preconditions.checkArgument(!t.isAnnotation());
    if (t.isPrimitive()) {
      sb.append(PRIMITIVE_FIELD_TYPES.get(t).charValue());
    } else {
      Class<?> bareType = t;
      while (bareType.isArray()) {
        sb.append('[');
        bareType = bareType.getComponentType();
      }
      sb.append('L');
      sb.append(t.getName().replace('.', '/'));
      sb.append(';');
    }
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
}
