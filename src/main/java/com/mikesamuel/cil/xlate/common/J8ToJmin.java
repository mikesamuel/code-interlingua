package com.mikesamuel.cil.xlate.common;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.j8.J8BaseLeafNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.jmin.JminBaseNode;
import com.mikesamuel.cil.ast.jmin.JminNodeType;
import com.mikesamuel.cil.ast.jmin.JminNodeVariant;
import com.mikesamuel.cil.ast.jmin.JminTypeDeclaration;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.JavaLang;
import com.mikesamuel.cil.ast.meta.MemberInfo;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.MethodTypeContainer;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PackageSpecification;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.StaticType;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.parser.SourcePosition;
import com.mikesamuel.cil.util.LogUtils;
import com.mikesamuel.cil.xlate.common.FlatTypes.FlatParamInfo;

/**
 * Translates {@linkplain com.mikesamuel.cil.ast.j8 j8} parse trees to
 * {@linkplain com.mikesamuel.cil.ast.jmin jmin} parse trees.
 * <p>
 * This translates a Java 8 parse tree to a minimal java parse tree preserving
 * semantics except for some around reflection.
 * <p>
 * Differences
 * <ul>
 *   <li>There are no anonymous or inner classes.  All classes are named and
 *     top-level.<br>
 *     This makes it easier for backends that don't themselves allow inner
 *     classes, and other backends lose little.</li>
 *   <li>One type declaration per compilation unit.</li>
 *   <li>Turn {@code enum} classes into normal class declarations and replace
 *     operations on them with operators that can be bound in a reference-type
 *     agnostic mode.
 *     <br>
 *     Some backends may want to treat <i>enum</i> values as object types with
 *     methods the way idiomatic Java does.
 *     <br>
 *     Some may want to treat <i>enum</i> values as symbolic names for integers
 *     the way iodimatic C++ does.
 *     <br>
 *     Some, like Go, treate <i>enum</i> values as symbolic names with methods.
 *   <li>Replace reflective operations with ones that lookup into side-tables.
 *     Only
 * </ul>
 */
public final class J8ToJmin {

  final Logger logger;
  final StaticType.TypePool typePool;

  J8ToJmin(Logger logger, StaticType.TypePool typePool) {
    this.logger = logger;
    this.typePool = typePool;
  }

  void error(NodeI<?, ?, ?> src, String msg) {
    LogUtils.log(logger, Level.SEVERE, src, msg, null);
  }

  StaticType type(TypeSpecification ts, SourcePosition pos) {
    return typePool.type(ts, pos, logger);
  }

  /** Translates one compilation unit. */
  public final
  ImmutableList<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> translate(
      ImmutableList<com.mikesamuel.cil.ast.j8.CompilationUnitNode> cus) {
    Translator translator = new Translator();

    // Run once so we can assign names.
    translator.setMode(Mode.COLLECT);
    for (com.mikesamuel.cil.ast.j8.CompilationUnitNode cu : cus) {
      translator.xlate(cu);
    }

    // Resolve naming conflicts.
    translator.setMode(Mode.ONCE_MORE_WITH_FEELING);

    // Run again now that we know the names for each class.
    for (com.mikesamuel.cil.ast.j8.CompilationUnitNode cu : cus) {
      translator.xlate(cu);
    }

    ImmutableList.Builder<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> b
        = ImmutableList.builder();
    for (com.mikesamuel.cil.ast.jmin.TypeDeclarationNode decl
         : translator.getTypeDeclarations()) {
      Optional<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> cuOpt =
          makeCompilationUnit(decl);
      if (cuOpt.isPresent()) {
        b.add(cuOpt.get());
      }
    }
    return b.build();
  }


  private
  Optional<com.mikesamuel.cil.ast.jmin.CompilationUnitNode> makeCompilationUnit(
      com.mikesamuel.cil.ast.jmin.TypeDeclarationNode typeDeclaration) {
    Optional<JminTypeDeclaration> td =
        typeDeclaration.finder(JminTypeDeclaration.class)
        .exclude(JminTypeDeclaration.class)
        .findOne();

    TypeInfo ti = null;
    if (td.isPresent()) {
      ti = td.get().getDeclaredTypeInfo();
    }
    if (ti == null) {
      LogUtils.log(
          logger, Level.SEVERE, typeDeclaration,
          "Missing package metadata for type", null);
      return Optional.absent();
    }

    Preconditions.checkState(
        ti.canonName.parent.type == Name.Type.PACKAGE,
        "Not flat", ti.canonName);
    Name packageName = ti.canonName.parent;
    if (Name.DEFAULT_PACKAGE.equals(packageName)) {
      LogUtils.log(
          logger, Level.SEVERE, td.get(),
          "Cannot translate classes in the default package", null);
      return Optional.absent();
    }

    // Figure out the declarations package and construct a package declaration.
    // Then add the type declaration.
    com.mikesamuel.cil.ast.jmin.PackageDeclarationNode packageDeclaration =
        com.mikesamuel.cil.ast.jmin.PackageDeclarationNode.Variant.Declaration
        .buildNode(toPackage(packageName, typeDeclaration.getSourcePosition()));

    return Optional.of(
        com.mikesamuel.cil.ast.jmin.CompilationUnitNode.Variant
        .PackageDeclarationTypeDeclaration.buildNode(
            packageDeclaration, typeDeclaration));
  }


  enum Mode {
    COLLECT,
    ONCE_MORE_WITH_FEELING,
  }


  final class Translator {
    private Mode mode;
    private final List<com.mikesamuel.cil.ast.jmin.TypeDeclarationNode>
        typeDeclarations = new ArrayList<>();

    /**
     * Map hierarchical type names to flat type names.
     * <p>
     * This is advisory during Mode.COLLECT, but on the mode switch we make
     * sure this is a proper 1:1 mapping.
     */
    private final FlatTypes flatTypes = new FlatTypes(logger, typePool.r);

    // We need to update this whenever we descend into a type declaration, or
    // exit one.
    private FlatParamInfo typeParameterContext =
        FlatTypes.EMPTY_FLAT_PARAM_INFO;

    private final MetadataBridge metadataBridge = new MetadataBridge() {

      private Name bridgeName(Name x) {
        switch (x.type) {
          case AMBIGUOUS:
            throw new AssertionError(x);
          case PACKAGE:
            return x;
          case CLASS:
          case TYPE_PARAMETER:
            return bridgeTypeSpecification(
                TypeSpecification.unparameterized(x))
                .rawName;
          case METHOD: {
            Name bridgeParent = bridgeName(x.parent);
            if (bridgeParent.equals(x.parent)) { return x; }
            return bridgeParent.method(x.identifier, x.variant);
          }
          case FIELD:
          case LOCAL: {
            Name bridgeParent = bridgeName(x.parent);
            if (bridgeParent.equals(x.parent)) { return x; }
            return bridgeParent.child(x.identifier, x.type);
          }
          default:
            break;

        }
        throw new AssertionError(x);
      }

      @Override
      public DeclarationPositionMarker bridgeDeclarationPositionMarker(
          DeclarationPositionMarker x) {
        return x;
      }

      @Override
      public Name bridgeDeclaredExpressionName(Name x) {
        return bridgeName(x);
      }

      private final Map<ExpressionNameResolver, ExpressionNameResolver> enrs
          = new IdentityHashMap<>();

      @Override
      public ExpressionNameResolver bridgeExpressionNameResolver(
          ExpressionNameResolver x) {
        ExpressionNameResolver bridged = enrs.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this));
          enrs.put(x, bridged);
        }
        // TODO: error out or remap local names like com, org, net, java that
        // mask package names.
        return bridged;
      }

      private final Map<MemberInfo, MemberInfo> mis = new IdentityHashMap<>();

      @Override
      public MemberInfo bridgeMemberInfo(MemberInfo x) {
        MemberInfo bridged = mis.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this));
          mis.put(x, bridged);
        }
        return bridged;
      }

      @Override
      public MethodDescriptor bridgeMethodDescriptor(MethodDescriptor x) {
        return x.map(this);
      }

      @Override
      public int bridgeMethodVariant(int x) {
        return x;
      }

      @Override
      public Name.Type bridgeNamePartType(Name.Type x) {
        return x;
      }

      @Override
      public Name bridgeReferencedExpressionName(Name x) {
        return bridgeName(x);
      }

      @Override
      public StaticType bridgeStaticType(StaticType x) {
        TypeSpecification s = bridgeTypeSpecification(x.typeSpecification);
        return typePool.type(s, null, logger);
      }

      private final Map<TypeInfo, TypeInfo> tis = new IdentityHashMap<>();

      @Override
      public TypeInfo bridgeTypeInfo(TypeInfo x) {
        TypeInfo bridged = tis.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this));
          tis.put(x, bridged);
        }
        return bridged;
      }

      private final Map<TypeNameResolver, TypeNameResolver> tnrs =
          new IdentityHashMap<>();
      @Override
      public TypeNameResolver bridgeTypeNameResolver(TypeNameResolver x) {
        // TODO: We should not bridge import resolvers if we do not emit
        // imports.  Maybe we need an alpha renaming pass that runs afterwards
        // and which enforces namespace separation.
        TypeNameResolver bridged = tnrs.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this, typePool.r));
          tnrs.put(x, bridged);
        }
        return bridged;
      }

      @Override
      public TypeSpecification bridgeTypeSpecification(TypeSpecification x) {
        return flatType(x);
      }

    };

    void setMode(Mode mode) {
      this.mode = Preconditions.checkNotNull(mode);
      flatTypes.setRecording(this.mode == Mode.COLLECT);
      if (this.mode == Mode.ONCE_MORE_WITH_FEELING) {
        flatTypes.disambiguate();
      }
      typeDeclarations.clear();
    }

    /**
     * Flattens types: {@code foo.bar.Baz<X>.Boo<Y>} &rarr;
     * {@code foo.bar.Baz$Boo<X, T>}.
     */
    TypeSpecification flatType(StaticType.TypePool.ClassOrInterfaceType t) {
      return flatType(t.typeSpecification);
    }

    TypeSpecification flatType(TypeSpecification bumpyType) {
      return (TypeSpecification) flatType((PartialTypeSpecification) bumpyType);
    }

    PartialTypeSpecification flatType(PartialTypeSpecification bumpyType) {
      Name bumpyName = bumpyType.getRawName();
      switch (bumpyName.type) {
        case PACKAGE:
          return bumpyType;
        case TYPE_PARAMETER:
          throw new Error(); // TDOO: rework using typeParameterContext
        case METHOD: {
          MethodTypeContainer m = (MethodTypeContainer) bumpyType;
          TypeSpecification flatParent = flatType(m.parent);

          ImmutableList<TypeBinding> bindings = m.bindings();
          ImmutableList<TypeBinding> flatBindings = ImmutableList.of();
          if (!bindings.isEmpty()) {
            Optional<CallableInfo> ciOpt =
                typePool.r.resolveCallable(bumpyName);
            Map<Name, TypeBinding> bindingsByName = Maps.newLinkedHashMap();
            if (ciOpt.isPresent()) {
              putBindings(ciOpt.get().typeParameters, bindings, bindingsByName);
            }
            flatBindings = flatBindings(bumpyName, bindingsByName);
          }

          return new MethodTypeContainer(
              flatParent,
              flatParent.rawName.method(
                  bumpyName.identifier, bumpyName.variant),
              flatBindings
              );
        }
        case CLASS: {
          TypeSpecification bumpy = (TypeSpecification) bumpyType;
          Name flatName = flatTypes.getFlatTypeName(bumpyName);

          // The package should not change.
          PackageSpecification packageName = null;
          for (PartialTypeSpecification p = bumpyType.parent(); p != null;
               p = p.parent()) {
            if (p instanceof PackageSpecification) {
              packageName = (PackageSpecification) p;
              break;
            }
          }
          Preconditions.checkState(
              packageName != null
              && flatName.parent.equals(packageName.packageName));

          // Collect bindings so we can remap them to flat parameter names.
          Map<Name, TypeBinding> bindingsByName = Maps.newLinkedHashMap();
          bumpy.withBindings(
              new Function<PartialTypeSpecification,
                           ImmutableList<TypeBinding>>() {

                @SuppressWarnings("synthetic-access")
                @Override
                public ImmutableList<TypeBinding> apply(
                    PartialTypeSpecification pts) {
                  ImmutableList<TypeBinding> bindings = pts.bindings();
                  if (!bindings.isEmpty()) {
                    List<Name> bumpyParams = null;
                    if (pts instanceof TypeSpecification) {
                      Optional<TypeInfo> tiOpt = typePool.r.resolve(
                          pts.getRawName());
                      if (tiOpt.isPresent()) {
                        bumpyParams = tiOpt.get().parameters;
                      }
                    } else if (pts instanceof MethodTypeContainer) {
                      Optional<CallableInfo> ciOpt = typePool.r.resolveCallable(
                          pts.getRawName());
                      if (ciOpt.isPresent()) {
                        bumpyParams = ciOpt.get().typeParameters;
                      }
                    }
                    if (bumpyParams != null) {
                      putBindings(bumpyParams, bindings, bindingsByName);
                    }
                  }
                  return bindings;
                }
              });

          ImmutableList<TypeBinding> allBindings = flatBindings(
              bumpyName, bindingsByName);

          return new TypeSpecification(
              packageName, flatName.identifier, Name.Type.CLASS,
              allBindings, bumpy.nDims);
        }
        case FIELD:
          // A primitive type.
          return bumpyType;
        case AMBIGUOUS:
        case LOCAL:
          break;
      }
      throw new AssertionError(bumpyName);
    }

    private void putBindings(
        List<Name> bumpyParams, List<TypeBinding> bindings,
        Map<Name, TypeBinding> bindingsByName) {
      int nParams = bumpyParams.size();
      if (bindings.size() == nParams) {
        for (int i = 0; i < nParams; ++i) {
          bindingsByName.put(
              bumpyParams.get(i), bindings.get(i));
        }
      } else {
        LogUtils.log(
            logger, Level.SEVERE, (SourcePosition) null,
            "Type parameter arity mismatch.  Expected "
                + bumpyParams.size() + " got " + bindings, null);
      }
    }

    /** @param bindingsByName maps bumpy parameter names to bumpy bindings. */
    private ImmutableList<TypeBinding> flatBindings(
        Name bumpyName, Map<Name, TypeBinding> bindingsByName) {
      FlatParamInfo paramInfo = flatTypes.getFlatParamInfo(bumpyName);
      ImmutableList.Builder<TypeBinding> b = ImmutableList.builder();
      for (Name bumpyParam : paramInfo.bumpyParametersInOrder) {
        TypeBinding untranslatedBinding = bindingsByName.get(bumpyParam);
        if (untranslatedBinding == null) {
          untranslatedBinding = TypeBinding.WILDCARD;
        }
        b.add(untranslatedBinding.subst(
            new Function<Name, TypeBinding>() {

              @SuppressWarnings("synthetic-access")
              @Override
              public TypeBinding apply(Name nm) {
                if (nm.type == Name.Type.CLASS) {
                  return new TypeBinding(
                      TypeSpecification.unparameterized(
                          flatTypes.getFlatTypeName(nm)));
                } else {
                  Preconditions.checkArgument(
                      nm.type == Name.Type.TYPE_PARAMETER, nm);
                  return new TypeBinding(flatType(
                      TypeSpecification.unparameterized(nm)));
                }
              }

            }));
      }
      return b.build();
    }



    ImmutableList<com.mikesamuel.cil.ast.jmin.TypeDeclarationNode>
        getTypeDeclarations() {
      return ImmutableList.copyOf(typeDeclarations);
    }

    JminBaseNode xlate(J8BaseNode inp) {
      J8BaseNode node8 = before(inp);

      Supplier<ImmutableList<JminBaseNode>> childrenSupplier =
          new Supplier<ImmutableList<JminBaseNode>>() {
        @Override
        public ImmutableList<JminBaseNode> get() {
          ImmutableList.Builder<JminBaseNode> b =
              ImmutableList.builder();
          for (int i = 0, n = node8.getNChildren(); i < n; ++i) {
            JminBaseNode xlatedChild = xlate(node8.getChild(i));
            if (xlatedChild != null) {
              b.add(xlatedChild);
            }
          }
          return b.build();
        }
      };

      J8NodeVariant v8 = node8.getVariant();

      JminNodeVariant vm = VARIANT_MAP.get(v8);
      JminBaseNode nodem;
      if (vm != null) {
        ImmutableList<JminBaseNode> xlatedChildren = childrenSupplier.get();
        if (node8 instanceof J8BaseLeafNode) {
          Preconditions.checkState(xlatedChildren.isEmpty());
          nodem = vm.buildNode(node8.getValue());
        } else {
          nodem = vm.buildNode(xlatedChildren);
        }
      } else {
        Xlater xlater = XLATERS.get(v8);
        Preconditions.checkNotNull(xlater, v8);
        nodem = xlater.xlate(this, node8, childrenSupplier);
      }
      if (nodem != null) {
        nodem.copyMetadataFrom(node8, metadataBridge);
      }
      if (nodem instanceof com.mikesamuel.cil.ast.jmin.TypeDeclarationNode) {
        com.mikesamuel.cil.ast.jmin.TypeDeclarationNode d =
            (com.mikesamuel.cil.ast.jmin.TypeDeclarationNode) nodem;
        // We turn all anonymous classes into named classes.
        // TODO: convert overridden methods on enum instances into table
        this.typeDeclarations.add(d);
      }
      return after(nodem);
    }

    J8BaseNode before(J8BaseNode node8) {
      return node8;
    }

    JminBaseNode after(JminBaseNode nodem) {
      return nodem;
    }

    J8ToJmin getMinner() {
      return J8ToJmin.this;
    }
  }

  interface Xlater {
    @Nullable JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children);
  }

  static final Xlater IGNORE_XLATER = new Xlater() {

    @Override
    public @Nullable JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
      return null;
    }

  };

  static final
  ImmutableMap<J8NodeVariant, Xlater> XLATERS =
      ImmutableMap.<J8NodeVariant, Xlater>builder()
      .put(
          com.mikesamuel.cil.ast.j8.
          PackageDeclarationNode.Variant.Declaration,
          new Xlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              return com.mikesamuel.cil.ast.jmin.
                  PackageDeclarationNode.Variant.Declaration.buildNode(
                      Iterables.filter(
                          children.get(),
                          nodeTypeIn(JminNodeType.PackageName)));
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.ConstructorBodyNode.Variant
          .LcExplicitConstructorInvocationBlockStatementsRc,
          new Xlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              // Make sure there is an explicit constructor invocation.
              com.mikesamuel.cil.ast.jmin.ExplicitConstructorInvocationNode inv
                  = null;
              com.mikesamuel.cil.ast.jmin.BlockStatementsNode stmts = null;
              for (JminBaseNode child : children.get()) {
                switch (child.getNodeType()) {
                  case ExplicitConstructorInvocation:
                    inv = (com.mikesamuel.cil.ast.jmin
                           .ExplicitConstructorInvocationNode) child;
                    break;
                  case BlockStatements:
                    stmts = (com.mikesamuel.cil.ast.jmin
                             .BlockStatementsNode) child;
                    break;
                  default:
                    throw new AssertionError(child);
                }
              }
              if (inv == null) {
                inv = com.mikesamuel.cil.ast.jmin
                    .ExplicitConstructorInvocationNode
                    .Variant
                    .TypeArgumentsSuperLpArgumentListRpSem
                    .buildNode();
              }
              ImmutableList.Builder<JminBaseNode> b = ImmutableList.builder();
              b.add(inv);
              if (stmts != null) { b.add(stmts); }
              return com.mikesamuel.cil.ast.jmin.ConstructorBodyNode.Variant
                  .LcExplicitConstructorInvocationBlockStatementsRc
                  .buildNode(b.build());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.CompilationUnitNode.Variant
          .PackageDeclarationImportDeclarationTypeDeclaration,
          new Xlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              // Filter out imports.
              ImmutableList<JminBaseNode> filteredChildren =
                  ImmutableList.copyOf(
                      Iterables.filter(
                          children.get(),
                          nodeTypeIn(
                              JminNodeType.PackageDeclaration,
                              JminNodeType.TypeDeclaration)));
              return com.mikesamuel.cil.ast.jmin.CompilationUnitNode.Variant
                  .PackageDeclarationTypeDeclaration.buildNode(
                      filteredChildren);
            }

          })
      .put(com.mikesamuel.cil.ast.j8.ImportDeclarationNode.Variant
           .SingleTypeImportDeclaration,
          IGNORE_XLATER)
      .put(com.mikesamuel.cil.ast.j8.ImportDeclarationNode.Variant
           .TypeImportOnDemandDeclaration,
          IGNORE_XLATER)
      .put(com.mikesamuel.cil.ast.j8.ImportDeclarationNode.Variant
           .SingleStaticImportDeclaration,
          IGNORE_XLATER)
      .put(com.mikesamuel.cil.ast.j8.ImportDeclarationNode.Variant
           .StaticImportOnDemandDeclaration,
           IGNORE_XLATER)
      .put(com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode.Variant
           .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments,
           new Xlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator,
                J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children) {
              com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode t8 =
                  (com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode) n8;
              StaticType t = t8.getStaticType();
              if (!(t instanceof StaticType.TypePool.ClassOrInterfaceType)) {
                xlator.getMinner().error(
                    n8,
                    t == null
                    ? "Missing type information for " + t8
                    : t + " is not a class or interface type");
                t = xlator.getMinner().type(
                    JavaLang.JAVA_LANG_OBJECT, n8.getSourcePosition());
              }
              TypeSpecification flat = xlator.flatType(
                  (TypePool.ClassOrInterfaceType) t);
              return xlator.getMinner().toClassOrInterfaceType(flat);
            }

           })
      .put(com.mikesamuel.cil.ast.j8.NormalClassDeclarationNode.Variant
          .Declaration,
          new Xlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              for (int i = 0, n = children.size(); i < n; ++i) {
                if (JminNodeType.SimpleTypeName
                    == children.get(i).getNodeType()) {
                  if (JminNodeType.Superclass
                      != children.get(i+1).getNodeType()) {
                    com.mikesamuel.cil.ast.jmin.SuperclassNode extendsObject =
                        com.mikesamuel.cil.ast.jmin.SuperclassNode.Variant
                        .ExtendsClassType.buildNode(
                            xlator.getMinner().toClassType(
                                JavaLang.JAVA_LANG_OBJECT));
                    children = ImmutableList.<JminBaseNode>builder()
                        .addAll(children.subList(0, i + 1))
                        .add(extendsObject)
                        .addAll(children.subList(i + 1, n))
                        .build();
                  }
                  break;
                }
              }
              return com.mikesamuel.cil.ast.jmin.NormalClassDeclarationNode
                  .Variant.Declaration.buildNode(children);
            }

          })
      .put(com.mikesamuel.cil.ast.j8.TypeDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .build();  // TODO

  static com.mikesamuel.cil.ast.jmin.PackageNameNode toPackage(
      Name pkg, SourcePosition pos) {
    ImmutableList.Builder<com.mikesamuel.cil.ast.jmin.IdentifierNode> b
        = ImmutableList.builder();
    for (Name nm = pkg; !Name.DEFAULT_PACKAGE.equals(nm); nm = nm.parent) {
      Preconditions.checkNotNull(nm);
      com.mikesamuel.cil.ast.jmin.IdentifierNode ident = toIdent(nm);
      ident.setSourcePosition(pos);
      b.add(ident);
    }
    return com.mikesamuel.cil.ast.jmin.PackageNameNode.Variant
        .IdentifierDotIdentifier
        .buildNode(b.build().reverse());
  }

  static com.mikesamuel.cil.ast.jmin.IdentifierNode toIdent(Name nm) {
    return com.mikesamuel.cil.ast.jmin.IdentifierNode.Variant.Builtin
        .buildNode(nm.identifier)
        .setNamePartType(nm.type);
  }

  com.mikesamuel.cil.ast.jmin.ClassTypeNode toClassType(
      TypeSpecification flatType) {
    return com.mikesamuel.cil.ast.jmin.ClassTypeNode
        .Variant.ClassOrInterfaceType
        .buildNode(toClassOrInterfaceType(flatType));
  }

  com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode
      toClassOrInterfaceType(TypeSpecification flat) {
    Name flatName = flat.rawName;
    com.mikesamuel.cil.ast.jmin.PackageNameNode pkg =
        toPackage(flatName.parent, null);
    List<com.mikesamuel.cil.ast.jmin.AnnotationNode> annots =
        ImmutableList.of();  // TODO
    com.mikesamuel.cil.ast.jmin.IdentifierNode className =
        toIdent(flatName);
    com.mikesamuel.cil.ast.jmin.TypeArgumentsNode typeArgs =
        toTypeArguments(flat.bindings);
    ImmutableList.Builder<JminBaseNode> b = ImmutableList.builder();
    if (pkg != null) {
      b.add(pkg);
    } else {
      error(
          null,
          "Minimal java does not allow classes in the default package "
          + flat
          );
    }
    b.addAll(annots);
    b.add(className);
    if (typeArgs != null) {
      b.add(typeArgs);
    }
    return com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode
        .Variant.PackageNameDotAnnotationIdentifierTypeArguments
        .buildNode(b.build());
  }

  static @Nullable
  com.mikesamuel.cil.ast.jmin.TypeArgumentsNode toTypeArguments(
      Iterable<? extends TypeBinding> bindings) {
    if (Iterables.isEmpty(bindings)) { return null; }
    System.err.println(bindings);
    throw new Error("TODO");
  }

  static final ImmutableMap<J8NodeVariant, JminNodeVariant> VARIANT_MAP;
  static {
    ImmutableMap.Builder<J8NodeVariant, JminNodeVariant> b =
        ImmutableMap.builder();

    Map<String, JminNodeType> jminNodeTypes = Maps.newHashMap();
    for (JminNodeType tmin : JminNodeType.values()) {
      JminNodeType dupe = jminNodeTypes.put(tmin.name(), tmin);
      Preconditions.checkState(dupe == null);
    }

    Map<String, JminNodeVariant> jminNodeVariants = Maps.newHashMap();
    for (J8NodeType t8 : J8NodeType.values()) {
      JminNodeType tmin = jminNodeTypes.get(t8.name());
      if (tmin == null) {
        // TODO: check t8 against whitelist of handled
        continue;
      }
      jminNodeVariants.clear();
      for (Enum<? extends JminNodeVariant> vmin
           : tmin.getVariantType().getEnumConstants()) {
        JminNodeVariant dupe = jminNodeVariants
            .put(vmin.name(), (JminNodeVariant) vmin);
        Preconditions.checkState(dupe == null);
      }

      StringBuilder sb8 = new StringBuilder();
      StringBuilder sbmin = new StringBuilder();
      for (Enum<? extends J8NodeVariant> v8e
           : t8.getVariantType().getEnumConstants()) {
        JminNodeVariant vmin = jminNodeVariants.get(v8e.name());
        if (vmin == null) {
          // TODO: check v8 against whitelist of handled
          continue;
        }

        J8NodeVariant v8 = (J8NodeVariant) v8e;
        sb8.setLength(0);
        v8.getParSer().appendShallowStructure(sb8);
        sbmin.setLength(0);
        vmin.getParSer().appendShallowStructure(sbmin);

        if (sb8.toString().equals(sbmin.toString())) {
          b.put(v8, vmin);
        } else {
          // TODO: check v8 against whitelist of handled
          continue;
        }
      }
    }
    VARIANT_MAP = b.build();
  }

  static Predicate<JminBaseNode> nodeTypeIn(
      JminNodeType t, JminNodeType... ts) {
    return new Predicate<JminBaseNode>() {

      EnumSet<JminNodeType> types = EnumSet.of(t, ts);

      @Override
      public boolean apply(JminBaseNode nmin) {
        return types.contains(nmin.getNodeType());
      }
    };
  }
}
