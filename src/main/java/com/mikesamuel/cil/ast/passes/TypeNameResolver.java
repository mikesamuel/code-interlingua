package com.mikesamuel.cil.ast.passes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.Name.Type;
import com.mikesamuel.cil.ast.meta.TypeInfo;

/**
 * Looks up the fully qualified name for the given
 */
public interface TypeNameResolver {

  /**
   * Looks up the fully qualified name given an ambiguous or unqualified name.
   *
   * @param ambiguousName a name whose type and ancestor types are
   *     {@link com.mikesamuel.cil.ast.meta.Name.Type#PACKAGE} or
   *     {@link com.mikesamuel.cil.ast.meta.Name.Type#CLASS} or
   *     {@link com.mikesamuel.cil.ast.meta.Name.Type#AMBIGUOUS}.
   * @return names whose type and parent types transitively are either
   *     {@link com.mikesamuel.cil.ast.meta.Name.Type#PACKAGE} or
   *     {@link com.mikesamuel.cil.ast.meta.Name.Type#CLASS}
   */
  public Iterable<Name> lookupTypeName(Name ambiguousName);

  /**
   * Factories for common resolvers.
   */
  public static final class Resolvers {
    /**
     * A TypeNameResolver that resolves names based on classes available to the
     * given class loader.
     * This is used to bootstrap types based on the system class loader.
     *
     * @param typeInfoResolver a type info resolver that returns equivalent
     *     type info for equivalent inputs regardless of call order or
     *     external state changes.
     *     This name resolver caches results so assumes that typeInfoResolver is
     *     memoizable/pure.
     */
    public static TypeNameResolver canonicalizer(
        TypeInfoResolver typeInfoResolver) {
      return new TypeNameResolver() {

        private final LoadingCache<Name, ImmutableList<Name>> cache =
            CacheBuilder.newBuilder()
            .build(
                new CacheLoader<Name, ImmutableList<Name>>() {

                  @Override
                  public ImmutableList<Name> load(Name ambigName) {
                    return ImmutableList.copyOf(findCanonicalNames(ambigName));
                  }

                  private Set<Name> findCanonicalNames(Name ambigName) {
                    Set<Name> canonNames = new LinkedHashSet<>();
                    for (Name unambiguousClassName
                         : disambiguateClasses(ambigName, true, false)) {
                      Optional<TypeInfo> ti = typeInfoResolver.resolve(
                          unambiguousClassName);
                      if (ti.isPresent()) {
                        TypeInfo type = ti.get();
                        canonNames.add(type.canonName);
                      }

                      Name parentClassName = unambiguousClassName.parent;
                      if (parentClassName.type != Name.Type.CLASS) {
                        continue;
                      }

                      // Consider the case where the name after lastDot refers
                      // to an inner class name.
                      // If name is "foo.Bar.Baz" we could convert it to a
                      // binary name "foo.Bar$Baz" and use the classloader
                      // but that will not handle the difference between
                      // fully qualified names and canonical names.
                      // Both of the following are valid
                      //     java.util.Map.Entry<K, V> e;  // NOT CANONICAL
                      //     java.util.HashMap.Entry<K, V> e;  // CANONICAL
                      String innerName = unambiguousClassName.identifier;
                      for (Name parentCanonName
                           : findCanonicalNames(parentClassName)) {
                        Optional<TypeInfo> outerTypeOpt =
                            typeInfoResolver.resolve(parentCanonName);
                        if (outerTypeOpt.isPresent()) {
                          TypeInfo outerType = outerTypeOpt.get();
                          for (Name innerClassName : outerType.innerClasses) {
                            if (innerName.equals(innerClassName.identifier)) {
                              canonNames.add(innerClassName);
                            }
                          }
                        }
                      }
                    }
                    return canonNames;
                  }
                });

        @Override
        public Iterable<Name> lookupTypeName(Name ambigName) {
          try {
            return cache.get(ambigName);
          } catch (ExecutionException e) {
            throw new AssertionError(e);
          }
        }
      };
    }

    @VisibleForTesting
    static ImmutableList<Name> disambiguateClasses(
        Name ambigName, boolean includeInnerClasses, boolean ancestors) {
      boolean isAmbiguous = false;
      for (Name n = ambigName; n != null; n = n.parent) {
        if (n.type == Name.Type.AMBIGUOUS) {
          isAmbiguous = true;
          break;
        }
      }

      if (!isAmbiguous) {
        // Fast path.
        if (ambigName.type == Name.Type.CLASS) {
          return ImmutableList.of(ambigName);
        }
        Preconditions.checkArgument(ambigName.type == Name.Type.PACKAGE);
        return ImmutableList.of();
      }

      Disambiguator d = new Disambiguator(includeInnerClasses);
      d.disambiguate(ambigName);
      List<Name> unambiguous =
          ancestors
          ? d.unambiguous
          : d.unambiguous.subList(
              d.getParentStartIndex(), d.unambiguous.size());
      return ImmutableList.copyOf(
          Iterables.filter(
              unambiguous,
              new Predicate<Name>() {
                @Override
                public boolean apply(Name name) {
                  return name.type == Name.Type.CLASS;
                }
              }));
    }

    private static final class Disambiguator {
      final boolean includeInnerClasses;
      final List<Name> unambiguous = Lists.newArrayList();
      private int parentStartIndex;

      Disambiguator(boolean includeInnerClasses) {
        this.includeInnerClasses = includeInnerClasses;
      }

      void disambiguate(Name ambigName) {
        if (Name.DEFAULT_PACKAGE.equals(ambigName)) {
          unambiguous.add(Name.DEFAULT_PACKAGE);
          return;
        }
        if (ambigName.parent == null) {
          unambiguous.add(Name.DEFAULT_PACKAGE);
        } else {
          disambiguate(ambigName.parent);
        }
        String ident = ambigName.identifier;
        int parentStart = this.getParentStartIndex();
        int parentEnd = unambiguous.size();
        this.parentStartIndex = parentEnd;
        for (int i = parentStart; i < parentEnd; ++i) {
          Name parent = unambiguous.get(i);
          switch (ambigName.type) {
            case PACKAGE:
              if (parent.type == Name.Type.PACKAGE) {
                unambiguous.add(parent.child(ident, Name.Type.PACKAGE));
              }
              continue;
            case CLASS:
              if (includeInnerClasses || parent.type != Name.Type.CLASS) {
                unambiguous.add(parent.child(ident, Name.Type.CLASS));
              }
              continue;
            case AMBIGUOUS:
              Name className =
                  (includeInnerClasses || parent.type != Name.Type.CLASS)
                  ? parent.child(ident, Name.Type.CLASS) : null;
              if (parent.type == Name.Type.CLASS) {
                if (className != null) {
                  unambiguous.add(className);
                }
              } else {
                Name packageName = parent.child(ident, Name.Type.PACKAGE);

                if (className == null) {
                  unambiguous.add(packageName);
                // Order based on naming conventions to make the common case
                // match correctly early.
                } else if (Character.isLowerCase(ident.codePointAt(0))) {
                  unambiguous.add(packageName);
                  unambiguous.add(className);
                } else {
                  unambiguous.add(className);
                  unambiguous.add(packageName);
                }
              }
              continue;
            case FIELD:
            case LOCAL:
            case METHOD:
            case TYPE_PARAMETER:
              break;
          }
          throw new IllegalArgumentException(ambigName.toString());
        }
      }

      int getParentStartIndex() {
        return parentStartIndex;
      }
    }

    /**
     * Maps unqualified names like {@code ?Map} to qualified
     * (but not necessarily canonical ones) like {@code java.util.Map}.
     *
     * @param names that have been declared within, inherited into, or
     *     imported into a scope to qualified names.
     *     These names should not be ambiguous but may be type parameters.
     */
    public static TypeNameResolver unqualifiedNameToQualifiedTypeResolver(
        Iterable<? extends Name> names, Logger logger) {
      ImmutableMap<String, Name> identifierToName;
      {
        Map<String, Name> m = new LinkedHashMap<>();
        for (Name name : names) {
          Preconditions.checkArgument(
              name.type == Type.CLASS
              || name.type == Type.TYPE_PARAMETER);
          Name redundant = m.put(name.identifier, name);
          if (redundant != null && !redundant.equals(name)) {
            // TODO: thread through location.
            logger.severe(
                "Conflicting types imported into same scope: "
                + redundant + " and " + name
                );
          }
        }
        identifierToName = ImmutableMap.copyOf(m);
      }

      return new TypeNameResolver() {

        @Override
        public Iterable<Name> lookupTypeName(Name ambiguousName) {
          if (ambiguousName.type == Type.TYPE_PARAMETER) {
            return ImmutableList.of(ambiguousName);
          }
          if (ambiguousName.type == Type.CLASS
              || ambiguousName.type == Type.AMBIGUOUS) {
            if (ambiguousName.parent == null) {
              Name qualifiedName = identifierToName.get(
                  ambiguousName.identifier);
              if (qualifiedName != null) {
                return ImmutableList.of(qualifiedName);
              }
            } else {
              ImmutableList.Builder<Name> innerNames = ImmutableList.builder();
              for (Name outerName : lookupTypeName(ambiguousName.parent)) {
                switch (outerName.type) {
                  case CLASS:
                    innerNames.add(outerName.child(
                        ambiguousName.identifier, Type.CLASS));
                    continue;
                  case TYPE_PARAMETER:
                    // This can be validly reached by code like
                    // class C<T> {
                    //   T.I x;
                    // }
                    // and "no such type T.I" is an appropriate response.
                    // TODO: should we spike fallback somehow?
                    continue;
                  case AMBIGUOUS:  // Should have been resolved.
                  case PACKAGE:  // Packages contain types but are not types.
                  case FIELD: case LOCAL: case METHOD:  // Not type parts
                    break;
                }
                // If we get here, either the identifierToName map is borken
                // or there is an unhandled case above.
                throw new AssertionError(outerName.type);
              }
              return innerNames.build();
            }
          }
          return ImmutableList.of();
        }
      };
    }

    /**
     * Tries a variety of package combinations.
     *
     * @param packagesAndOuterTypes packages and canonical class names from
     *     which to import.
     * @param canonicalizer maps qualified names to canonical names.
     */
    public static TypeNameResolver wildcardLookup(
        Iterable<Name> packagesAndOuterTypes, TypeNameResolver canonicalizer) {
      Preconditions.checkArgument(
          Iterables.all(packagesAndOuterTypes, new Predicate<Name>() {
            @Override
            public boolean apply(Name nm) {
              return nm.type == Type.PACKAGE || nm.type == Type.CLASS;
            }
          }));
      return new TypeNameResolver() {

        @Override
        public Iterable<Name> lookupTypeName(Name ambiguousName) {
          ImmutableList<String> typeNameIdents;
          {
            ImmutableList.Builder<String> b = ImmutableList.builder();
            for (Name an = ambiguousName; an != null; an = an.parent) {
              switch (an.type) {
                case AMBIGUOUS:
                case CLASS:
                  b.add(an.identifier);
                  continue;
                case PACKAGE:
                case TYPE_PARAMETER:
                  return canonicalizer.lookupTypeName(ambiguousName);
                case FIELD: case METHOD: case LOCAL:
                  throw new IllegalArgumentException(ambiguousName.toString());
              }
              throw new AssertionError(an.type);
            }
            typeNameIdents = b.build().reverse();
          }

          Set<Name> resolved = Sets.newLinkedHashSet();
          for (Name oneContainer : packagesAndOuterTypes) {
            Name importedClass = oneContainer;
            for (String typeNameIdent : typeNameIdents) {
              importedClass = importedClass.child(typeNameIdent, Type.CLASS);
            }
            for (Name canonicalImportedClass
                 : canonicalizer.lookupTypeName(importedClass)) {
              resolved.add(canonicalImportedClass);
            }
          }
          return ImmutableList.copyOf(resolved);
        }

      };
    }

    /**
     * Handles type name masking by falling back to the second resolver only
     * if no results are found for the first.
     */
    public static TypeNameResolver eitherOr(
        TypeNameResolver a, TypeNameResolver b) {
      return new TypeNameResolver() {

        @Override
        public Iterable<Name> lookupTypeName(Name ambiguousName) {
          Iterable<Name> aNames = a.lookupTypeName(ambiguousName);
          return Iterables.isEmpty(aNames)
              ? b.lookupTypeName(ambiguousName)
              : aNames;
        }

      };
    }
  }

}
