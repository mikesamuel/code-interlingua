package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
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

        private final LoadingCache<Name, Optional<TypeInfo>> cache =
            CacheBuilder.newBuilder()
            .build(
                new CacheLoader<Name, Optional<TypeInfo>>() {

                  @SuppressWarnings("synthetic-access")
                  @Override
                  public Optional<TypeInfo> load(Name name) {
                    if (name.type == Name.Type.CLASS) {
                      return loadClass(toBinaryName(name));
                    }

                    // If name is a type parameter name, then lookup its
                    // containing class, method, or constructor to get its
                    // bounds.
                    if (name.type == Name.Type.TYPE_PARAMETER) {
                      Optional<GenericDeclaration> containerOpt =
                          lookupGenericDeclaration(name.parent, cl);
                      if (!containerOpt.isPresent()) {
                        return Optional.absent();
                      }
                      GenericDeclaration container = containerOpt.get();
                      for (TypeVariable<?> v : container.getTypeParameters()) {
                        if (name.identifier.equals(v.getName())) {
                          Type[] bounds = v.getBounds();
                          Optional<TypeSpecification> sup =
                              bounds.length != 0
                              ? Optional.of(specForType(bounds[0]))
                              : Optional.absent();
                          ImmutableList.Builder<TypeSpecification>
                              additionalBounds = ImmutableList.builder();
                          for (int i = 1, n = bounds.length; i < n; ++i) {
                            additionalBounds.add(specForType(bounds[i]));
                          }
                          return Optional.of(TypeInfo.builder(name)
                              .superType(sup)
                              .interfaces(additionalBounds.build())
                              .build());
                        }
                      }
                    }

                    // It's possible for StaticType.ERROR_TYPE
                    // .typeSpecification.typeName
                    // to reach here which has name type FIELD.
                    return Optional.absent();
                  }

                  @SuppressWarnings("synthetic-access")
                  private Optional<TypeInfo> loadClass(String binaryName) {
                    Class<?> clazz;
                    try {
                      clazz = cl.loadClass(binaryName);
                    } catch (@SuppressWarnings("unused")
                             ClassNotFoundException ex) {
                      return Optional.absent();
                    }

                    Name className = nameForClass(clazz);

                    Type superType = clazz.getGenericSuperclass();
                    Class<?> outerClass = clazz.getEnclosingClass();

                    ImmutableList.Builder<TypeSpecification> interfaceSpecs =
                        ImmutableList.builder();
                    for (Type iface : clazz.getGenericInterfaces()) {
                      interfaceSpecs.add(specForType(iface));
                    }

                    ImmutableList.Builder<Name> innerNames =
                        ImmutableList.builder();
                    findInnerClasses(clazz, innerNames, Sets.newHashSet());

                    ImmutableList.Builder<MemberInfo> members =
                        ImmutableList.builder();
                    for (Field f : clazz.getDeclaredFields()) {
                      int mods = f.getModifiers();
                      if (!Modifier.isPrivate(mods)) {
                        FieldInfo fi = new FieldInfo(
                            mods,
                          className.child(f.getName(), Name.Type.FIELD));
                      fi.setValueType(specForType(f.getGenericType()));
                      members.add(fi);
                    }
                  }
                  Method[] methods = clazz.getDeclaredMethods();
                  for (Method m : methods) {
                    String mname = m.getName();
                    int mods = m.getModifiers();
                    if (!Modifier.isPrivate(mods)) {
                      int index = 1;
                      for (Method om : methods) {
                        if (m.equals(om)) { break; }
                        if (mname.equals(om.getName())) {
                          ++index;
                        }
                      }
                      Name canonName = className.method(mname, index);
                      CallableInfo ci = new CallableInfo(
                          mods, canonName,
                          typeVars(canonName, m.getTypeParameters()));
                      ImmutableList.Builder<TypeSpecification> formalTypes =
                          ImmutableList.builder();
                      for (Type t : m.getGenericParameterTypes()) {
                        formalTypes.add(specForType(t));
                      }
                      ci.setReturnType(specForType(m.getGenericReturnType()));
                      ci.setVariadic(m.isVarArgs());
                      ci.setFormalTypes(formalTypes.build());
                      ci.setDescriptor(
                          methodDescriptorFor(
                              m.getParameterTypes(), m.getReturnType()));
                      members.add(ci);
                    }
                  }
                  Constructor<?>[] ctors = clazz.getDeclaredConstructors();
                  for (Constructor<?> c : ctors) {
                    int mods = c.getModifiers();
                    if (!Modifier.isPrivate(mods)) {
                      int index = Arrays.asList(ctors).indexOf(c) + 1;
                      Name canonName = className.method("<init>", index);
                      CallableInfo ci = new CallableInfo(
                          mods, canonName,
                          typeVars(canonName, c.getTypeParameters()));
                      ImmutableList.Builder<TypeSpecification> formalTypes =
                          ImmutableList.builder();
                      for (Type t : c.getGenericParameterTypes()) {
                        formalTypes.add(specForType(t));
                      }
                      ci.setReturnType(StaticType.T_VOID.typeSpecification);
                      ci.setVariadic(c.isVarArgs());
                      ci.setFormalTypes(formalTypes.build());
                      ci.setDescriptor(methodDescriptorFor(
                          c.getParameterTypes(), Void.TYPE));
                      members.add(ci);
                    }
                  }
                  ImmutableList<Name> parameters = typeVars(
                      className, clazz.getTypeParameters());

                  TypeInfo.Builder b = TypeInfo.builder(className)
                      .modifiers(clazz.getModifiers())
                      .isAnonymous(clazz.isAnonymousClass())
                      .superType(superType != null
                          ? Optional.of(specForType(superType))
                          : Optional.<TypeSpecification>absent())
                        .interfaces(interfaceSpecs.build())
                        .parameters(parameters)
                        .outerClass(outerClass != null
                            ? Optional.of(nameForClass(outerClass))
                            : Optional.<Name>absent())
                        .innerClasses(innerNames.build())
                        .declaredMembers(members.build());

                    return Optional.of(b.build());
                  }
                });

        @Override
        public Optional<TypeInfo> resolve(Name name) {
          try {
            return cache.get(name);
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }
      };
    }

    private static String toBinaryName(Name name) {
      Preconditions.checkArgument(name.type == Name.Type.CLASS, name);
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
          // TODO: skip over METHOD?
          throw new AssertionError(name.parent.type);
      }
      if (separator != 0) {
        appendBinaryName(name.parent, sb);
        sb.append(separator);
      }
      Preconditions.checkNotNull(name.identifier);
      sb.append(name.identifier);
    }

    private static ImmutableList<Name> typeVars(
        Name containerName, TypeVariable<?>[] vars) {
      ImmutableList.Builder<Name> names = ImmutableList.builder();
      for (TypeVariable<?> v : vars) {
        names.add(containerName.child(v.getName(), Name.Type.TYPE_PARAMETER));
      }
      return names.build();
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

    private static final ImmutableMap<Class<?>, Name> PRIMITIVE_CLASS_TO_NAME =
        ImmutableMap.<Class<?>, Name>builder()
        .put(void.class, StaticType.T_VOID.typeSpecification.typeName)
        .put(boolean.class, StaticType.T_BOOLEAN.typeSpecification.typeName)
        .put(byte.class, StaticType.T_BYTE.typeSpecification.typeName)
        .put(char.class, StaticType.T_CHAR.typeSpecification.typeName)
        .put(short.class, StaticType.T_SHORT.typeSpecification.typeName)
        .put(int.class, StaticType.T_INT.typeSpecification.typeName)
        .put(float.class, StaticType.T_FLOAT.typeSpecification.typeName)
        .put(long.class, StaticType.T_LONG.typeSpecification.typeName)
        .put(double.class, StaticType.T_DOUBLE.typeSpecification.typeName)
        .build();

    private static TypeSpecification specForType(Type t) {
      if (t instanceof Class) {
        Class<?> cl = (Class<?>) t;
        int nDims = 0;
        while (cl.isArray()) {
          ++nDims;
          cl = cl.getComponentType();
        }

        Name nm;
        if (cl.isPrimitive()) {
          nm = PRIMITIVE_CLASS_TO_NAME.get(cl);
        } else {
          nm = nameForClass(cl);
        }
        return new TypeSpecification(nm, nDims);
      } else if (t instanceof ParameterizedType) {
        ParameterizedType pt = (ParameterizedType) t;
        ImmutableList.Builder<TypeSpecification.TypeBinding> bindings =
            ImmutableList.builder();
        for (Type ta : pt.getActualTypeArguments()) {
          bindings.add(bindingForType(ta));
        }
        return new TypeSpecification(
            nameForClass((Class<?>) pt.getRawType()), bindings.build());
      } else if (t instanceof TypeVariable) {
        TypeVariable<?> tv = (TypeVariable<?>) t;
        GenericDeclaration d = tv.getGenericDeclaration();
        Name parentName;
        if (d instanceof Class) {
          parentName = nameForClass((Class<?>) d);
        } else if (d instanceof Method) {
          Method m = (Method) d;
          String mname = m.getName();
          Class<?> dc = m.getDeclaringClass();
          int index = 1;
          for (Method om : dc.getDeclaredMethods()) {
            if (m.equals(om)) { break; }
            if (mname.equals(om.getName())) {
              ++index;
            }
          }
          parentName = nameForClass(dc).method(mname, index);
        } else if (d instanceof Constructor) {
          Constructor<?> c = (Constructor<?>) d;
          Class<?> dc = c.getDeclaringClass();
          int index = Arrays.asList(dc.getDeclaredConstructors())
              .indexOf(c) + 1;
          parentName = nameForClass(dc).method("<init>", index);
        } else {
          throw new AssertionError(d + " : " + d.getClass().getName());
        }
        return new TypeSpecification(
            parentName.child(tv.getName(), Name.Type.TYPE_PARAMETER));
      } else if (t instanceof GenericArrayType) {
        GenericArrayType at = (GenericArrayType) t;
        TypeSpecification ts = specForType(at.getGenericComponentType());
        return ts.withNDims(ts.nDims + 1);
      } else {
        throw new AssertionError(t.getClass());
      }
    }

    private static TypeSpecification.TypeBinding bindingForType(Type t) {
      if (t instanceof WildcardType) {
        WildcardType wt = (WildcardType) t;
        Type[] lb = wt.getLowerBounds();
        if (lb.length != 0) {
          Preconditions.checkState(lb.length == 1, t);
          return new TypeSpecification.TypeBinding(
              TypeSpecification.Variance.SUPER,
              specForType(lb[0]));
        }
        Type[] ub = wt.getUpperBounds();
        Preconditions.checkState(ub.length == 1, t);
        return new TypeSpecification.TypeBinding(
            TypeSpecification.Variance.EXTENDS,
            specForType(ub[0]));
      } else {
        return new TypeSpecification.TypeBinding(
            TypeSpecification.Variance.INVARIANT,
            specForType(t));
      }
    }

    private static Optional<GenericDeclaration> lookupGenericDeclaration(
        Name nm, ClassLoader cl) {
      switch (nm.type) {
        case CLASS:
          try {
            return Optional.of(cl.loadClass(toBinaryName(nm)));
          } catch (@SuppressWarnings("unused") ClassNotFoundException ex) {
            return Optional.absent();
          }
        case METHOD:
          Class<?> containingClass;
          try {
            containingClass = cl.loadClass(toBinaryName(nm.parent));
          } catch (@SuppressWarnings("unused") ClassNotFoundException ex) {
            return Optional.absent();
          }
          int ordinal = nm.variant;
          for (Method m : containingClass.getDeclaredMethods()) {
            if (nm.identifier.equals(m.getName())) {
              --ordinal;
              if (ordinal == 0) {
                return Optional.of(m);
              }
            }
          }
          return Optional.absent();
        default:
          throw new AssertionError(nm);
      }
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
        sb.append(t.getName().replace('.', '/'));
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

  /**
   * The super-types (non-transitive) of the given type.
   * <p>
   * For example, the super-type of {@code List<String>} is
   * {@code Collection<String>} because {@code List<T> extends Collection<T>}.
   */
  public default Iterable<TypeSpecification> superTypesOf(
      TypeSpecification ts) {
    Optional<TypeInfo> infoOpt = resolve(ts.typeName);
    if (!infoOpt.isPresent()) {
      return ImmutableList.of();
    }
    TypeInfo info = infoOpt.get();
    Optional<TypeSpecification> superTypeOpt = info.superType;
    if (!superTypeOpt.isPresent()
        && Modifier.isInterface(info.modifiers)) {
      // Interfaces implicitly have Object as the super type.
      superTypeOpt = Optional.of(TypeSpecification.JAVA_LANG_OBJECT);
    }
    return Iterables.transform(
        Iterables.concat(superTypeOpt.asSet(), info.interfaces),
        new Function<TypeSpecification, TypeSpecification>() {

          @Override
          public TypeSpecification apply(TypeSpecification sub) {
            if (ts.bindings.isEmpty()) { return sub; }
            // Not a raw type
            return sub.subst(info.parameters, ts.bindings);
          }
        });
  }
}
