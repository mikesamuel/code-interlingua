package com.mikesamuel.cil.ast.passes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.mikesamuel.cil.ast.j8.AdditionalBoundNode;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeListNode;
import com.mikesamuel.cil.ast.j8.InterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8Chapter;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.TemplatePseudoRootNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeVariableNode;
import com.mikesamuel.cil.ast.j8.traits.FileNode;
import com.mikesamuel.cil.ast.j8.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.j8.traits.TypeScope;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;

/**
 * Looks at all type declarations disambiguates names in {@code extends} and
 * {@code implements} clauses so that we can know, within a given class
 * body, which field, method, and type parameter names are available, and
 * in which class they are declared.
 */
class DeclarationPass extends AbstractPass<TypeInfoResolver> {

  static final boolean DEBUG = false;

  DeclarationPass(Logger logger) {
    super(logger);
  }

  /**
   * A resolver for types not defined in the compilation units being processed.
   */
  protected TypeInfoResolver getFallbackTypeInfoResolver() {
    ClassLoader cl = getClass().getClassLoader();
    if (cl == null) { cl = ClassLoader.getSystemClassLoader(); }
    return TypeInfoResolver.Resolvers.forClassLoader(cl);
  }

  /**
   * @return a TypeInfoResolver that resolves canonical names.
   */
  @Override
  public TypeInfoResolver run(Iterable<? extends FileNode> fileNodes) {
    // To properly map type names to canonical type names, we need four things:
    // 1. The set of internally defined types.
    // 2. The set of external types.
    // 3. The set of imports that map unqualified names to qualified names.
    // 4. Enough meta-data for both (1) and (2) to map non-canonical type names
    //    to canonical type names.
    // (3) requires (1, 2) so before wildcard imports can be resolved.
    // (4) requires (1, 2, 3) so we can resolve super-type relationships.

    ImmutableList<FileNode> files = ImmutableList.copyOf(fileNodes);
    // (1)
    ClassNamingPass.DeclarationsAndScopes declarationsAndScopes =
        new ClassNamingPass(logger).run(files);
    // (2)
    TypeInfoResolver externalTypeInfoResolver = getFallbackTypeInfoResolver();

    ScopeResolver sr = new ScopeResolver(
        declarationsAndScopes,
        externalTypeInfoResolver);
    // (3) on demand for (4)
    sr.resolveAll();

    return sr.typeInfoResolver;
  }

  final class ScopeResolver {
    /** TypeInfoResolver for canonical type names. */
    final TypeInfoResolver typeInfoResolver;
    /**
     * TypeInfoResolver for qualified and unqualified names in scope.
     * Built progressively as types are resolved.
     */
    final Map<TypeScope, TypeNameResolver> resolvedScopes =
        new IdentityHashMap<>();
    /**
     * Converts external type names
     */
    final TypeNameResolver canonicalizer;
    final ImmutableMap<Name, UnresolvedTypeDeclaration> internallyDefinedTypes;
    final Map<TypeScope, TypeScope> scopeToParent;
    final Multimap<TypeScope, UnresolvedTypeDeclaration> scopeToDecls =
        Multimaps.newMultimap(
            new IdentityHashMap<>(),
            new Supplier<Collection<UnresolvedTypeDeclaration>>() {
              @Override
              public Collection<UnresolvedTypeDeclaration> get() {
                return new LinkedHashSet<>();
              }
            });

    ScopeResolver(
        ClassNamingPass.DeclarationsAndScopes declarationsAndScopes,
        TypeInfoResolver externalTypeInfoResolver) {
      this.internallyDefinedTypes = declarationsAndScopes.declarations;
      this.scopeToParent = declarationsAndScopes.scopeToParent;
      this.typeInfoResolver = TypeInfoResolver.Resolvers.eitherOr(
          new TypeInfoResolver() {
            @Override
            public Optional<TypeInfo> resolve(Name typeName) {
              UnresolvedTypeDeclaration d =
                  internallyDefinedTypes.get(typeName);
              return d != null
                  ? Optional.of(d.decl.getDeclaredTypeInfo())
                  : Optional.absent();
            }
          },
          externalTypeInfoResolver);
      this.canonicalizer = TypeNameResolver.Resolvers
          .canonicalizer(typeInfoResolver);

      for (UnresolvedTypeDeclaration d : internallyDefinedTypes.values()) {
        scopeToDecls.put(d.scope, d);
      }
    }

    void resolveAll() {
      Set<Name> loop = new LinkedHashSet<>();
      for (UnresolvedTypeDeclaration d : this.internallyDefinedTypes.values()) {
        loop.clear();
        resolve(d, loop);
      }
      for (TypeScope scope : scopeToParent.keySet()) {
        resolverForScope(scope, new HashSet<Name>());
      }
    }

    private void resolve(UnresolvedTypeDeclaration d, Set<Name> loop) {
      switch (d.stage) {
        case UNRESOLVED:
          Preconditions.checkState(
              loop.add(d.decl.getDeclaredTypeInfo().canonName));
          d.stage = Stage.IN_PROGRESS;
          doResolveDeclaration(d, loop);
          return;
        case IN_PROGRESS:
          d.stage = Stage.UNRESOLVABLE;
          error(
              d.decl,
              "Dependency loop for type declaration "
              + d.decl.getDeclaredTypeInfo().canonName
              + " : " + loop);
          return;
        case UNRESOLVABLE:  // :(
        case RESOLVED:      // :)
          return;
      }
      throw new AssertionError(d.stage);
    }

    private TypeNameResolver resolverForScope(TypeScope scope, Set<Name> loop) {
      TypeNameResolver resolver = resolvedScopes.get(scope);
      if (resolver != null) {
        return resolver;
      }
      if (DEBUG) {
        System.err.println(
            "Resolving scope " + ((J8BaseNode) scope).getSourcePosition()
            + " : " + scope.getClass().getSimpleName());
      }

      if (scope instanceof CompilationUnitNode) {
        resolver = resolverFor((CompilationUnitNode) scope);
      } else if (scope instanceof TemplatePseudoRootNode) {
        resolver = TypeNameResolver.Resolvers.nullResolver();
      } else {
        TypeNameResolver parentResolver;
        {
          TypeScope parentScope = scopeToParent.get(scope);
          // Compilation units handled above.
          Preconditions.checkNotNull(
              parentScope, "%s has no parent scope", scope);
          parentResolver = resolverForScope(parentScope, loop);
        }
        // To resolve a scope, we need to resolve all the outer scopes, because
        // an inner scope inherits inner types from super types.
        Set<Name> interfacesToInheritDeclarationsFrom = new LinkedHashSet<>();
        Set<Name> superTypesToInheritDeclarationsFrom = new LinkedHashSet<>();
        for (UnresolvedTypeDeclaration typeInScope : scopeToDecls.get(scope)) {
          TypeInfo typeInfo = typeInScope.decl.getDeclaredTypeInfo();
          if (typeInfo.outerClass.isPresent()) {
            Name outerClassName = typeInfo.outerClass.get();
            UnresolvedTypeDeclaration outerDecl =
                internallyDefinedTypes.get(outerClassName);
            TypeInfo outerTypeInfo = outerDecl.decl.getDeclaredTypeInfo();
            resolve(outerDecl, loop);
            for (TypeSpecification iface : outerTypeInfo.interfaces) {
              interfacesToInheritDeclarationsFrom.add(iface.typeName);
            }
            if (outerTypeInfo.superType.isPresent()) {
              superTypesToInheritDeclarationsFrom.add(
                  outerTypeInfo.superType.get().typeName);
            }
          }
        }
        // Inherit symbols from outer scopes.
        Map<String, Name> identifierToCanonName = new LinkedHashMap<>();
        for (Name superTypeName
            : Iterables.concat(
                interfacesToInheritDeclarationsFrom,
                superTypesToInheritDeclarationsFrom)) {
          TypeInfo ti;
          UnresolvedTypeDeclaration td =
              internallyDefinedTypes.get(superTypeName);
          if (td != null) {
            resolve(td, loop);
            ti = td.decl.getDeclaredTypeInfo();
          } else {
            Optional<TypeInfo> tiOpt = typeInfoResolver.resolve(superTypeName);
            if (tiOpt.isPresent()) {
              ti = tiOpt.get();
            } else {
              error(scope, "Could not resolve super type " + superTypeName);
              continue;
            }
          }
          for (Name innerName : ti.innerClasses) {
            identifierToCanonName.put(innerName.identifier, innerName);
          }
        }

        // Mask any outer scope symbols with ones from this scope including
        // type parameters.
        for (UnresolvedTypeDeclaration localDecl : scopeToDecls.get(scope)) {
          TypeInfo localTypeInfo = localDecl.decl.getDeclaredTypeInfo();
          identifierToCanonName.put(
              localTypeInfo.canonName.identifier,
              localTypeInfo.canonName);
        }

        resolver = TypeNameResolver.Resolvers.eitherOr(
            TypeNameResolver.Resolvers.unqualifiedNameToQualifiedTypeResolver(
                identifierToCanonName.values(), logger),
            parentResolver);
      }

      TypeNameResolver dupe = resolvedScopes.put(scope, resolver);
      Preconditions.checkState(dupe == null);
      if (DEBUG) {
        System.err.println(
            "Resolved scope " + ((J8BaseNode) scope).getSourcePosition() + " : "
            + scope.getClass().getSimpleName());
      }
      scope.setTypeNameResolver(resolver);
      return resolver;
    }

    TypeNameResolver resolverFor(CompilationUnitNode cu) {
      ImportCollector ic = new ImportCollector(canonicalizer);
      ic.collectImports(cu);
      TypeNameResolver typeNameResolver =
          TypeNameResolver.Resolvers.eitherOr(
              // Full names trump all others and any ambiguities among them need
              // to be reported.
              TypeNameResolver.Resolvers
              .unqualifiedNameToQualifiedTypeResolver(
                  ic.getStaticTypeImports(), logger),

              // Stuff in the same package trumps wildcard imports.
              TypeNameResolver.Resolvers.wildcardLookup(
                  ImmutableList.of(ic.getCurrentPackage()), canonicalizer),

              // Explicit wildcard imports trump implicit imports.
              TypeNameResolver.Resolvers.wildcardLookup(
                  ic.getWildcardTypeImports(), canonicalizer),

              // java.lang.* is implicitly visible.
              TypeNameResolver.Resolvers.wildcardLookup(
                  ImmutableList.of(JavaLang.JAVA_LANG), canonicalizer),

              // Fully qualified names should resolve to themselves.
              canonicalizer);
      return typeNameResolver;
    }

    private void doResolveDeclaration(
        UnresolvedTypeDeclaration d, Set<Name> loop) {
      if (DEBUG) {
        System.err.println("Resolving " + d.decl.getDeclaredTypeInfo().canonName
            + " with loop=" + loop);
      }
      TypeNameResolver nameResolver = resolverForScope(d.scope, loop);

      TypeDeclaration decl = d.decl;
      J8BaseNode node = (J8BaseNode) decl;
      List<J8BaseNode> children = node.getChildren();
      // Collect the declared type after resolving its super-types.
      Name typeName = decl.getDeclaredTypeInfo().canonName;

      TypeSpecification superTypeSpec = JavaLang.JAVA_LANG_OBJECT;

      ImmutableList.Builder<TypeSpecification> interfaceNames =
          ImmutableList.builder();
      int modifiers = 0;
      for (J8BaseNode child : children) {
        switch (child.getNodeType()) {
          case JavaDocComment:
            break;
          case Modifier:
            ModifierNode.Variant modVariant =
                ((ModifierNode) child).getVariant();
            modifiers |= ModifierNodes.modifierBits(modVariant);
            break;
          case ArgumentList:
            // Process arguments to enum and anon-class constructors outside
            // the body scope.
            break;
          case SimpleTypeName: case FieldName:
            break;
          case Superclass:
          case ClassOrInterfaceTypeToInstantiate: {
            superTypeSpec = AmbiguousNames.typeSpecificationOf(
                child, nameResolver, logger);
            break;
          }
          case TypeBound:
            for (J8BaseNode grandChild : child.getChildren()) {
              if (grandChild instanceof ClassOrInterfaceTypeNode
                  || grandChild instanceof TypeVariableNode) {
                superTypeSpec = AmbiguousNames.typeSpecificationOf(
                    grandChild, nameResolver, logger);
              } else if (grandChild instanceof AdditionalBoundNode) {
                TypeSpecification typeSpec = AmbiguousNames.typeSpecificationOf(
                    grandChild, nameResolver, logger);
                interfaceNames.add(typeSpec);
              }
            }
            break;
          case Superinterfaces:
          case ExtendsInterfaces:
            InterfaceTypeListNode interfacesNode =
                (InterfaceTypeListNode) child.getChildren().get(0);
            for (J8BaseNode interfacesChild : interfacesNode.getChildren()) {
              InterfaceTypeNode interfaceType =
                  (InterfaceTypeNode) interfacesChild;
              TypeSpecification typeSpec = AmbiguousNames.typeSpecificationOf(
                  interfaceType, nameResolver, logger);
              interfaceNames.add(typeSpec);
            }
            break;
          case TypeParameters:
            break;
          default:
            Preconditions.checkState(
                child instanceof TypeScope,
                "%s in %s", child, node);
        }
      }

      boolean isAnonymous = false;
      switch (node.getNodeType()) {
        case NormalClassDeclaration:
        case NormalInterfaceDeclaration:
        case TypeParameter:
          break;
        case AnnotationTypeDeclaration:
          interfaceNames.add(JavaLang.JAVA_LANG_ANNOTATION_ANNOTATION);
          break;
        case EnumDeclaration:
          // Would be Enum<typeName> if Name captured generic parameters.
          superTypeSpec = JavaLang.JAVA_LANG_ENUM.withBindings(
              ImmutableList.of(new TypeBinding(typeName)));
          break;
        case UnqualifiedClassInstanceCreationExpression:
        case EnumConstant:
          isAnonymous = true;
          break;
        default:
          throw new AssertionError(node.getNodeType());
      }

      if (isAnonymous) {
        modifiers |= Modifier.FINAL;
      }

      TypeInfo partialTypeInfo = typeInfoResolver.resolve(typeName).get();
      Preconditions.checkState(typeName.equals(partialTypeInfo.canonName));

      TypeInfo typeInfo = partialTypeInfo.builder()
          .modifiers(modifiers)
          .isAnonymous(isAnonymous)
          .superType(Optional.of(superTypeSpec))
          .interfaces(interfaceNames.build())
          .build();
      d.decl.setDeclaredTypeInfo(typeInfo);
      d.stage = Stage.RESOLVED;

      if (DEBUG) {
        System.err.println("Resolved " + typeName);
      }
    }
  }

  final class ImportCollector {
    final TypeNameResolver typeNameResolver;
    final ImmutableList.Builder<Name> staticTypeImports =
        ImmutableList.builder();
    final ImmutableList.Builder<Name> wildcardImports = ImmutableList.builder();
    private Name currentPackage = Name.DEFAULT_PACKAGE;

    ImportCollector(TypeNameResolver typeNameResolver) {
      this.typeNameResolver = typeNameResolver;
    }

    Name getCurrentPackage() {
      return currentPackage;
    }

    ImmutableList<Name> getStaticTypeImports() {
      return staticTypeImports.build();
    }

    ImmutableList<Name> getWildcardTypeImports() {
      return wildcardImports.build();
    }

    void collectImports(J8BaseNode node) {
      switch (node.getVariant().getNodeType()) {
        case PackageDeclaration:
          this.currentPackage = toName(node, Name.Type.PACKAGE);
          break;
        case SingleTypeImportDeclaration:
          Name ambigName = AmbiguousNames.ambiguousNameOf(node);
          ImmutableList<Name> importedTypes =
              typeNameResolver.lookupTypeName(ambigName);
          switch (importedTypes.size()) {
            case 0:
              error(
                  node,
                  "import of " + ambigName.toDottedString()
                  + " does not resolve to a declared type");
              break;
            case 1:
              this.staticTypeImports.add(importedTypes.get(0));
              break;
            default:
              error(
                  node,
                  "import of " + ambigName.toDottedString()
                  + " is ambiguous: " + importedTypes);
          }
          break;
        case TypeImportOnDemandDeclaration:
          Name importedPackageOrType = AmbiguousNames.ambiguousNameOf(node);
          ImmutableList<Name> outerTypes = typeNameResolver.lookupTypeName(
              importedPackageOrType);
          switch (outerTypes.size()) {
            case 0:
              // Assume anything not a type is a package.
              this.wildcardImports.add(toName(node, Name.Type.PACKAGE));
              break;
            case 1:
              this.wildcardImports.add(outerTypes.get(0));
              break;
            default:
              error(
                  node,
                  "import of " + node.getTextContent(".")
                  + ".* is ambiguous: " + outerTypes);
          }
          break;
        default:
          for (J8BaseNode child : node.getChildren()) {
            if (child.getNodeType().getChapter() == J8Chapter.Package) {
              collectImports(child);
            }
          }
          break;
      }
    }
  }

  static Name toName(J8BaseNode node, Name.Type type) {
    List<String> identifiers = new ArrayList<>();
    findIdentifiers(node, identifiers);
    Name nm = null;
    if (type == Name.Type.PACKAGE) {
      nm = Name.DEFAULT_PACKAGE;
    }
    for (String ident : identifiers) {
      if (nm == null) {
        nm = Name.root(ident, type);
      } else {
        nm = nm.child(ident, type);
      }
    }
    return nm;
  }

  static void findIdentifiers(J8BaseNode node, List<? super String> out) {
    if (node instanceof TypeArgumentNode || node instanceof AnnotationNode) {
      return;
    }
    if (node instanceof IdentifierNode) {
      out.add(node.getValue());
    } else {
      for (J8BaseNode child : node.getChildren()) {
        findIdentifiers(child, out);
      }
    }
  }
}
