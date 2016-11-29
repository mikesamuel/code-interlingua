package com.mikesamuel.cil.ast.passes;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.ImportDeclarationNode;

/**
 * Looks up the fully qualified name for the given
 */
public interface TypeNameResolver {

  /**
   * Looks up the fully qualified name for the given
   */
  public Iterable<Name> lookupTypeName(String name);

  /**
   * Factories for common resolvers.
   */
  public static final class Resolvers {
    /**
     * A TypeNameResolver that resolves names based on classes available to the
     * given class loader.
     * This is used to bootstrap types based on the system class loader.
     */
    public static TypeNameResolver forClassLoader(final ClassLoader cl) {
      return new TypeNameResolver() {

        private final LoadingCache<String, ImmutableList<Name>> cache =
            CacheBuilder.newBuilder()
            .build(
                new CacheLoader<String, ImmutableList<Name>>() {

                  @Override
                  public ImmutableList<Name> load(String name) {
                    List<Class<?>> classes = new ArrayList<>();
                    findClasses(name, classes);
                    return ImmutableList.copyOf(
                        Lists.transform(
                            classes,
                            new Function<Class<?>, Name>() {
                              @SuppressWarnings("synthetic-access")
                              @Override
                              public Name apply(Class<?> c) {
                                return nameForClass(c);
                              }
                            }));
                  }

                  @SuppressWarnings("synthetic-access")
                  private void findClasses(
                      String name, List<Class<?>> classes) {
                    Class<?> clazz = null;
                    try {
                      clazz = cl.loadClass(name);
                    } catch (@SuppressWarnings("unused")
                             ClassNotFoundException ex) {
                      // Fine.  Keep looking.
                    }
                    if (clazz != null) {
                      classes.add(clazz);
                    }
                    int startOuter = classes.size();
                    int lastDot = name.lastIndexOf('.');
                    if (lastDot >= 0) {
                      // Consider the case where the name after lastDot refers
                      // to an inner class name.
                      // If name is "foo.Bar.Baz" we could convert it to a
                      // binary name "foo.Bar$Baz" and use the classloader
                      // but that will not handle the difference between
                      // fully qualified names and canonical names.
                      // Both of the following are valid
                      //     java.util.Map.Entry<K, V> e;  // NOT CANONICAL
                      //     java.util.HashMap.Entry<K, V> e;  // CANONICAL
                      findClasses(name.substring(0, lastDot), classes);
                      int endOuter = classes.size();
                      if (endOuter != startOuter) {
                        Set<Class<?>> interfacesSeen = new HashSet<>();
                        String innerName = name.substring(lastDot + 1);
                        for (int i = startOuter; i < endOuter; ++i) {
                          Class<?> outer = classes.get(i);
                          int startInner = classes.size();
                          interfacesSeen.clear();
                          findInnerClasses(
                              outer, classes, interfacesSeen);
                          int endInner = classes.size();
                          for (int j = startInner; j < endInner; ++j) {
                            Class<?> inner = classes.get(j);
                            if (innerName.equals(inner.getSimpleName())) {
                              classes.add(inner);
                            }
                          }
                          classes.subList(startInner, endInner).clear();
                        }
                        classes.subList(startOuter, endOuter).clear();
                      }
                    }
                  }
                });

        @Override
        public Iterable<Name> lookupTypeName(String name) {
          try {
            return cache.get(name);
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }
      };
    }

    public static TypeNameResolver forImports(
        Iterable<? extends ImportDeclarationNode> imports) {
      return null;
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

    private static void findInnerClasses(
        Class<?> cl, List<Class<?>> classes, Set<Class<?>> interfacesSeen) {
      // getClasses does not include classes from implemented interfaces.
      for (Class<?> c : cl.getClasses()) {
        classes.add(c);
      }
      for (Class<?> iface : cl.getInterfaces()) {
        if (interfacesSeen.add(iface)) {
          findInnerClasses(iface, classes, interfacesSeen);
        }
      }
    }
  }
}
