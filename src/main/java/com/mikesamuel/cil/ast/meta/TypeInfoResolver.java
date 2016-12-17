package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

/**
 * Fetches meta-data for an ambiguous type name.
 */
public interface TypeInfoResolver {


  /**
   * Meta-data for an ambiguous type name.
   *
   * @param className a name whose type and ancestor types are either
   *    {@link com.mikesamuel.cil.ast.meta.Name.Type#PACKAGE} or
   *    {@link com.mikesamuel.cil.ast.meta.Name.Type#CLASS}
   */
  Optional<TypeInfo> resolve(Name className);

  /**
   * Factories for common resolvers.
   */
  public static final class Resolvers {
    /**
     * A TypeNameResolver that resolves names based on classes available to the
     * given class loader.
     * This is used to bootstrap types based on the system class loader.
     */
    public static TypeInfoResolver forClassLoader(final ClassLoader cl) {
      return new TypeInfoResolver() {

        private final LoadingCache<String, Optional<TypeInfo>> cache =
            CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, Optional<TypeInfo>>() {

                  @SuppressWarnings("synthetic-access")
                  @Override
                  public Optional<TypeInfo> load(String name) {
                    Class<?> clazz;
                    try {
                      clazz = cl.loadClass(name);
                    } catch (@SuppressWarnings("unused")
                             ClassNotFoundException ex) {
                      return Optional.absent();
                    }

                    Name className = nameForClass(clazz);

                    Class<?> superClass = clazz.getSuperclass();
                    Class<?> outerClass = clazz.getEnclosingClass();

                    ImmutableList.Builder<Name> interfaceNames =
                        ImmutableList.builder();
                    for (Class<?> iface : clazz.getInterfaces()) {
                      interfaceNames.add(nameForClass(iface));
                    }

                    ImmutableList.Builder<Name> innerNames =
                        ImmutableList.builder();
                    findInnerClasses(clazz, innerNames, Sets.newHashSet());

                    ImmutableList.Builder<MemberInfo> members =
                        ImmutableList.builder();
                    for (Field f : clazz.getDeclaredFields()) {
                      int mods = f.getModifiers();
                      if (!Modifier.isPrivate(mods)) {
                        members.add(new FieldInfo(
                            mods,
                            className.child(f.getName(), Name.Type.FIELD)));
                      }
                    }
                    for (Method m : clazz.getDeclaredMethods()) {
                      int mods = m.getModifiers();
                      if (!Modifier.isPrivate(mods)) {
                        members.add(new CallableInfo(
                            mods,
                            className.method(
                                m.getName(),
                                methodDescriptorFor(
                                    m.getParameterTypes(), m.getReturnType()))
                            ));
                      }
                    }
                    for (Constructor<?> c : clazz.getDeclaredConstructors()) {
                      int mods = c.getModifiers();
                      if (!Modifier.isPrivate(mods)) {
                        members.add(new CallableInfo(
                            mods,
                            className.method(
                                "<init>",
                                methodDescriptorFor(
                                    c.getParameterTypes(), Void.TYPE))));
                      }
                    }

                    return Optional.of(new TypeInfo(
                        className,
                        clazz.getModifiers(),
                        clazz.isAnonymousClass(),
                        superClass != null
                        ? Optional.of(nameForClass(superClass))
                        : Optional.<Name>absent(),
                        interfaceNames.build(),
                        outerClass != null
                        ? Optional.of(nameForClass(outerClass))
                        : Optional.<Name>absent(),
                        innerNames.build(),
                        members.build()
                        ));
                  }
                });

        @Override
        public Optional<TypeInfo> resolve(Name name) {
          @SuppressWarnings("synthetic-access")
          String binaryName = toBinaryName(name);
          try {
            return cache.get(binaryName);
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }
      };
    }

    private static String toBinaryName(Name name) {
      Preconditions.checkArgument(name.type == Name.Type.CLASS);
      StringBuilder sb = new StringBuilder();
      appendBinaryName(name, sb);
      return sb.toString();
    }

    private static void appendBinaryName(Name name, StringBuilder sb) {
      char separator;
      switch (name.parent.type) {
        case PACKAGE:
          if (name.parent.equals(Name.DEFAULT_PACKAGE)) {
            separator = 0;
          } else {
            separator = '.';
          }
          break;
        case CLASS:
          separator = '$';
          break;
        default:
          throw new AssertionError(name.parent.type);
      }
      if (separator != 0) {
        appendBinaryName(name.parent, sb);
        sb.append(separator);
      }
      Preconditions.checkNotNull(name.identifier);
      sb.append(name.identifier);
    }

    private static Name nameForClass(Class<?> cl) {
      Preconditions.checkArgument(!cl.isPrimitive() && !cl.isArray());
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

    private static String methodDescriptorFor(
        Class<?>[] parameterTypes, Class<?> returnType) {
      StringBuilder sb = new StringBuilder();
      sb.append('(');
      for (Class<?> parameterType : parameterTypes) {
        appendTypeDescriptor(sb, parameterType);
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
        sb.append(t.getName());
        sb.append(';');
      }
    }

    private static void findInnerClasses(
        Class<?> cl, ImmutableList.Builder<Name> names,
        Set<Class<?>> interfacesSeen) {
      // getClasses does not include classes from implemented interfaces.
      for (Class<?> c : cl.getClasses()) {
        names.add(nameForClass(c));
      }
      for (Class<?> iface : cl.getInterfaces()) {
        if (interfacesSeen.add(iface)) {
          findInnerClasses(iface, names, interfacesSeen);
        }
      }
    }

    /**
     * Delegates to a when it has a much, otherwise falls-back to b.
     */
    public static TypeInfoResolver eitherOr(
        TypeInfoResolver a, TypeInfoResolver b) {
      return new TypeInfoResolver() {

        @Override
        public Optional<TypeInfo> resolve(Name typeName) {
          Optional<TypeInfo> ti = a.resolve(typeName);
          return ti.isPresent() ? ti : b.resolve(typeName);
        }

      };
    }
  }
}
