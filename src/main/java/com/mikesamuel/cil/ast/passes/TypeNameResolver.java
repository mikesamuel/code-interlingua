package com.mikesamuel.cil.ast.passes;

import java.util.concurrent.ExecutionException;

import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
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
                    ImmutableList.Builder<Name> names = ImmutableList.builder();
                    StringBuilder nameBuffer = new StringBuilder(name);
                    int outerClassLimit = name.length();
                    while (outerClassLimit >= 0) {
                      String binaryName = nameBuffer.toString();
                      Class<?> clazz = null;
                      try {
                        clazz = cl.loadClass(binaryName);
                      } catch (@SuppressWarnings("unused")
                               ClassNotFoundException ex) {
                        // Fine.  Keep looking.
                      }
                      if (clazz != null) {
                        @SuppressWarnings("synthetic-access")
                        Name className = nameForClass(clazz);
                        names.add(className);
                      }

                      int dot = name.lastIndexOf('.', outerClassLimit - 1);
                      if (dot < 0) {
                        break;
                      }
                      outerClassLimit = dot < 0 ? 0 : dot;
                      nameBuffer.setCharAt(dot, '$');
                    }
                    return names.build();
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
  }
}
