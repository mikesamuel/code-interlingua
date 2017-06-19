package com.mikesamuel.cil.xlate.common;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.j8.J8BaseLeafNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.jmin.JminBaseInnerNode;
import com.mikesamuel.cil.ast.jmin.JminBaseNode;
import com.mikesamuel.cil.ast.jmin.JminNodeType;
import com.mikesamuel.cil.ast.jmin.JminNodeVariant;
import com.mikesamuel.cil.ast.jmin.JminTypeDeclaration;
import com.mikesamuel.cil.ast.jmin.JminWholeType;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.FieldInfo;
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
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.mixins.TypeDeclaration;
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
 *   <li>Turn specialized {@code enum} instances into state vectors.
 *     and overridden methods into switches.
 *     <p>
 *     This allows us to map enum semantics (modulo reflection) to a smaller set
 *     of operators that are available in languages where enums are idiomatic in
 *     certain APIs but which do not allow specialization.
 *     <p>
 *     TODO: an optional pass that replaces field and method operations on enum
 *     instances with static operations and casts between enum-types and
 *     super-types thereof to simplify
 *     code for backends that benefit from treating enum types as integral
 *     types.
 *     <ul>
 *       <li>Some backends may want to treat <i>enum</i> values as object types
 *         with methods the way idiomatic Java does.
 *       <li>Some may want to treat <i>enum</i> values as symbolic names for
 *         integers the way iodimatic C++ does.
 *       <li>Some, like Go, treat <i>enum</i> values as symbolic names with
 *         methods.
 *     </ul>
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

    translator.finish();

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

    /** A guess at the node being translated for error reporting. */
    private SourcePosition current;

    /**
     * Map hierarchical type names to flat type names.
     * <p>
     * This is advisory during Mode.COLLECT, but on the mode switch we make
     * sure this is a proper 1:1 mapping.
     */
    private final FlatTypes flatTypes = new FlatTypes(logger, typePool.r);
    {
      // We assume elsewhere that this type maps to itself as when filling
      // in the super-type for class declarations.
      flatTypes.recordType(BName.of(JavaLang.JAVA_LANG_OBJECT.rawName));
    }

    private final List<Scope> scopes = Lists.newArrayList();

    final class OmniBridge implements MetadataBridge, TypeInfoResolver {

      private final Map<FName, TypeInfo> typeParameterTypeInfo =
          Maps.newHashMap();

      @SuppressWarnings("synthetic-access")
      @Override
      public Optional<TypeInfo> resolve(Name flatTypeName) {
        if (flatTypeName.type == Name.Type.CLASS) {
          BName bumpyName = flatTypes.getBumpyTypeName(FName.of(flatTypeName));
          Optional<TypeInfo> bumpyTi = typePool.r.resolve(bumpyName.name);
          if (bumpyTi.isPresent()) {
            return Optional.of(bridgeTypeInfo(bumpyTi.get()));
          }
        } else {
          TypeInfo flatTi = typeParameterTypeInfo.get(flatTypeName);
          if (flatTi != null) { return Optional.of(flatTi); }
          FName className = FName.of(flatTypeName.getContainingClass());
          if (flatTypeName.parent.type == Name.Type.CLASS) {
            FlatParamInfo fpi = flatTypes.getFlatParamInfo(className);
            int index = fpi.flatParametersInOrder.indexOf(
                FName.of(flatTypeName));
            BName bumpyTypeName = fpi.bumpyParametersInOrder.get(index);
            Optional<TypeInfo> bumpyTiOpt = typePool.r.resolve(
                bumpyTypeName.name);
            if (bumpyTiOpt.isPresent()) {
              TypeInfo bumpyTi = bumpyTiOpt.get();
              TypeInfo.Builder b = TypeInfo.builder(flatTypeName);
              if (bumpyTi.superType.isPresent()) {
                // TODO: does this bridging need to be done in the context of
                // className?  Push a scope for className,
                // and pop it after done?
                b.superType(Optional.of(
                    bridgeTypeSpecification(bumpyTi.superType.get())));
                b.interfaces(
                    Lists.transform(
                        bumpyTi.interfaces,
                        new Function<TypeSpecification, TypeSpecification>() {

                          @Override
                          public TypeSpecification apply(TypeSpecification t) {
                            return bridgeTypeSpecification(t);
                          }

                        }));
                flatTi = b.build();
                typeParameterTypeInfo.put(FName.of(flatTypeName), flatTi);
                return Optional.of(flatTi);
              }
            }
          } else {
            // Assume method
            // TODO
          }
          // TODO: construct TypeInfo from the bumpy type info, and cache it
          // TODO: lookup in cache above.
          // TODO: adjust bridging type info of type parameters to take into
          // account the scope so that it fits with this.
          throw new AssertionError(flatTypeName);
        }
        return Optional.absent();
      }

      private Name bridgeName(Name x) {
        if (x == null) { return null; }
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
        if (x == null) { return null; }
        return x;
      }

      @Override
      public Name bridgeDeclaredExpressionName(Name x) {
        if (x == null) { return null; }
        return bridgeName(x);
      }

      private final Map<ExpressionNameResolver, ExpressionNameResolver> enrs
          = new IdentityHashMap<>();

      @Override
      public ExpressionNameResolver bridgeExpressionNameResolver(
          ExpressionNameResolver x) {
        if (x == null) { return null; }
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
        if (x == null) { return null; }
        MemberInfo bridged = mis.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this));
          mis.put(x, bridged);
        }
        return bridged;
      }

      @Override
      public MethodDescriptor bridgeMethodDescriptor(MethodDescriptor x) {
        if (x == null) { return null; }
        return x.map(this);
      }

      @Override
      public int bridgeMethodVariant(int x) {
        return x;
      }

      @Override
      public Name.Type bridgeNamePartType(Name.Type x) {
        if (x == null) { return null; }
        return x;
      }

      @Override
      public Name bridgeReferencedExpressionName(Name x) {
        if (x == null) { return null; }
        return bridgeName(x);
      }

      @Override
      public StaticType bridgeStaticType(StaticType x) {
        if (x == null) { return null; }
        TypeSpecification s = bridgeTypeSpecification(x.typeSpecification);
        return bridgedTypePool.type(s, null, logger);
      }

      private final Map<TypeInfo, TypeInfo> tis = new IdentityHashMap<>();

      @Override
      public TypeInfo bridgeTypeInfo(TypeInfo x) {
        if (x == null) { return null; }
        if (x.canonName.type == Name.Type.TYPE_PARAMETER) {
          // Type parameters are context dependent.
          Optional<TypeInfo> flatTiOpt = resolve(bridgeName(x.canonName));
          Preconditions.checkState(flatTiOpt.isPresent());
          return flatTiOpt.get();
        }
        TypeInfo bridged = tis.get(x);
        if (bridged == null) {
          @SuppressWarnings("synthetic-access")
          FlatParamInfo fpi = flatTypes.getFlatParamInfo(BName.of(x.canonName));
          bridged = x.map(this)
              .outerClass(Optional.absent())
              .innerClasses(ImmutableList.of())
              .parameters(
                  Lists.transform(fpi.flatParametersInOrder, FName.NAME_OF))
              .build();
          tis.put(x, bridged);
        }
        return bridged;
      }

      private final Map<TypeNameResolver, TypeNameResolver> tnrs =
          new IdentityHashMap<>();
      @Override
      public TypeNameResolver bridgeTypeNameResolver(TypeNameResolver x) {
        if (x == null) { return null; }
        // TODO: We should not bridge import resolvers if we do not emit
        // imports.  Maybe we need an alpha renaming pass that runs afterwards
        // and which enforces namespace separation.
        TypeNameResolver bridged = tnrs.get(x);
        if (bridged == null) {
          bridged = Preconditions.checkNotNull(x.map(this, this));
          tnrs.put(x, bridged);
        }
        return bridged;
      }

      @Override
      public TypeSpecification bridgeTypeSpecification(TypeSpecification x) {
        if (x == null) { return null; }
        return flatType(x);
      }

      TypeBinding bridgeTypeBinding(TypeBinding x) {
        if (x == null) { return null; }
        if (x.typeSpec == null) { return x; }
        TypeSpecification bs = bridgeTypeSpecification(x.typeSpec);
        return bs == x.typeSpec ? x : new TypeBinding(x.variance, bs);
      }

    }

    OmniBridge metadataBridge;
    TypePool bridgedTypePool;
    private final
    Map<BName, Optional<FName>> bumpyInnerTypeNameToFlatOuterThisFieldName =
        Maps.newLinkedHashMap();
    private final Multimap<Name, MemberInfo> syntheticMembersStillToAdd =
        ArrayListMultimap.create();

    void setMode(Mode mode) {
      this.mode = Preconditions.checkNotNull(mode);
      flatTypes.setRecording(this.mode == Mode.COLLECT);
      if (this.mode == Mode.ONCE_MORE_WITH_FEELING) {
        flatTypes.disambiguate();
      }

      typeDeclarations.clear();
      metadataBridge = new OmniBridge();
      bridgedTypePool = new TypePool(metadataBridge);
      bumpyInnerTypeNameToFlatOuterThisFieldName.clear();
      syntheticMembersStillToAdd.clear();
    }

    void finish() {
      for (com.mikesamuel.cil.ast.jmin.TypeDeclarationNode td
           : typeDeclarations) {
        Optional<JminTypeDeclaration> dOpt = td
            .finder(JminTypeDeclaration.class)
            .exclude(JminTypeDeclaration.class)
            .findOne();
        Preconditions.checkState(dOpt.isPresent(), td);
        JminTypeDeclaration d = dOpt.get();
        TypeInfo ti = d.getDeclaredTypeInfo();
        Preconditions.checkNotNull(ti, d);
        Collection<MemberInfo> members =
            syntheticMembersStillToAdd.removeAll(ti.canonName);
        for (MemberInfo mi : members) {
          if (mi instanceof FieldInfo) {
            FieldInfo fi = (FieldInfo) mi;
            int mods = fi.modifiers;

            List<com.mikesamuel.cil.ast.jmin.ModifierNode.Variant> modVs =
                Lists.newArrayList();
            if (Modifier.isPublic(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Public);
            }
            if (Modifier.isPrivate(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Private);
            }
            if (Modifier.isStatic(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Static);
            }
            if (Modifier.isFinal(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Final);
            }
            if (Modifier.isStrict(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Strictfp);
            }
            if (Modifier.isVolatile(mods)) {
              modVs.add(
                  com.mikesamuel.cil.ast.jmin.ModifierNode.Variant.Volatile);
            }

            List<com.mikesamuel.cil.ast.jmin.ModifierNode> modifiers
                = Lists.transform(
                    modVs,
                    new Function<
                    com.mikesamuel.cil.ast.jmin.ModifierNode.Variant,
                        com.mikesamuel.cil.ast.jmin.ModifierNode>() {

                      @Override
                      public com.mikesamuel.cil.ast.jmin.ModifierNode apply(
                          com.mikesamuel.cil.ast.jmin.ModifierNode.Variant v) {
                        return v.buildNode();
                      }

                    });

            com.mikesamuel.cil.ast.jmin.UnannTypeNode type = toUnannType(
                fi.getValueType());

            com.mikesamuel.cil.ast.jmin.VariableDeclaratorIdNode declaratorId
                = com.mikesamuel.cil.ast.jmin
                .VariableDeclaratorIdNode.Variant
                .Identifier.buildNode(toIdent(fi.canonName))
                .setDeclaredExpressionName(fi.canonName);

            com.mikesamuel.cil.ast.jmin.VariableDeclaratorNode declarator
                = com.mikesamuel.cil.ast.jmin
                  .VariableDeclaratorNode.Variant
                  .VariableDeclaratorIdEqVariableInitializer
                  .buildNode(declaratorId);

            com.mikesamuel.cil.ast.jmin.VariableDeclaratorListNode declarators
                = com.mikesamuel.cil.ast.jmin
                  .VariableDeclaratorListNode.Variant
                  .VariableDeclaratorComVariableDeclarator
                  .buildNode(declarator);

            com.mikesamuel.cil.ast.jmin.FieldDeclarationNode fieldDecl
                = com.mikesamuel.cil.ast.jmin
                  .FieldDeclarationNode.Variant.Declaration.buildNode(
                      ImmutableList.<JminBaseNode>builder()
                      .addAll(modifiers)
                      .add(type)
                      .add(declarators)
                      .build());
            com.mikesamuel.cil.ast.jmin.ClassMemberDeclarationNode memberDecl
                = com.mikesamuel.cil.ast.jmin
                .ClassMemberDeclarationNode.Variant.FieldDeclaration
                .buildNode(fieldDecl);
            com.mikesamuel.cil.ast.jmin.ClassBodyDeclarationNode classBodyDecl
                = com.mikesamuel.cil.ast.jmin
                .ClassBodyDeclarationNode.Variant.ClassMemberDeclaration
                .buildNode(memberDecl);

            com.mikesamuel.cil.ast.jmin.ClassBodyNode body =
                ((JminBaseNode) d).firstChildWithType(
                    com.mikesamuel.cil.ast.jmin.ClassBodyNode.class);
            body.add(0, classBodyDecl);
          } else {
            throw new AssertionError(mi);
          }
        }
      }
    }

    void error(NodeI<?, ?, ?> src, String msg) {
      error(src != null ? src.getSourcePosition() : null, msg);
    }

    void error(SourcePosition pos, String msg) {
      LogUtils.log(
          logger, Level.SEVERE,
          pos != null ? pos : current,
          msg, null);
    }

    StaticType type(TypeSpecification ts, SourcePosition pos) {
      return typePool.type(ts, pos != null ? pos : current, logger);
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
      BName bumpyName = BName.of(bumpyType.getRawName());
      if (mode == Mode.COLLECT) {
        flatTypes.recordType(bumpyName);
      }
      switch (bumpyName.name.type) {
        case PACKAGE:
          return bumpyType;
        case TYPE_PARAMETER: {
          TypeSpecification ts = (TypeSpecification) bumpyType;
          for (Scope scope : Lists.reverse(scopes)) {
            FName flatName = scope.flatParamInfo.substMap.get(bumpyName);
            if (flatName != null) {
              return TypeSpecification.unparameterized(flatName.name)
                  .withNDims(ts.nDims);
            }
          }
          throw new AssertionError(bumpyType);
        }
        case METHOD: {
          MethodTypeContainer m = (MethodTypeContainer) bumpyType;
          TypeSpecification flatParent = flatType(m.parent);

          ImmutableList<TypeBinding> bindings = m.bindings();
          ImmutableList<TypeBinding> flatBindings = ImmutableList.of();
          if (!bindings.isEmpty()) {
            Optional<CallableInfo> ciOpt =
                typePool.r.resolveCallable(bumpyName.name);
            Map<BName, TypeBinding> bindingsByName = Maps.newLinkedHashMap();
            if (ciOpt.isPresent()) {
              putBindings(
                  Lists.transform(
                      ciOpt.get().typeParameters, BName.OF),
                  bindings, bindingsByName);
            }
            flatBindings = flatBindings(bumpyName, bindingsByName);
          }

          return new MethodTypeContainer(
              flatParent,
              flatParent.rawName.method(
                  bumpyName.name.identifier, bumpyName.name.variant),
              flatBindings
              );
        }
        case CLASS: {
          TypeSpecification bumpy = (TypeSpecification) bumpyType;
          FName flatName = flatTypes.getFlatTypeName(bumpyName);

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
              && flatName.name.parent.equals(packageName.packageName));

          // Collect bindings so we can remap them to flat parameter names.
          Map<BName, TypeBinding> bindingsByName = Maps.newLinkedHashMap();
          bumpy.withBindings(
              new Function<PartialTypeSpecification,
                           ImmutableList<TypeBinding>>() {

                @SuppressWarnings("synthetic-access")
                @Override
                public ImmutableList<TypeBinding> apply(
                    PartialTypeSpecification pts) {
                  ImmutableList<TypeBinding> bindings = pts.bindings();
                  if (!bindings.isEmpty()) {
                    List<BName> bumpyParams = null;
                    if (pts instanceof TypeSpecification) {
                      Optional<TypeInfo> tiOpt = typePool.r.resolve(
                          pts.getRawName());
                      if (tiOpt.isPresent()) {
                        bumpyParams = Lists.transform(
                            tiOpt.get().parameters, BName.OF);
                      }
                    } else if (pts instanceof MethodTypeContainer) {
                      Optional<CallableInfo> ciOpt = typePool.r.resolveCallable(
                          pts.getRawName());
                      if (ciOpt.isPresent()) {
                        bumpyParams = Lists.transform(
                            ciOpt.get().typeParameters, BName.OF);
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
              Preconditions.checkNotNull(packageName), flatName.name.identifier,
              Name.Type.CLASS, allBindings, bumpy.nDims);
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
        List<BName> bumpyParams, List<TypeBinding> bindings,
        Map<BName, TypeBinding> bindingsByName) {
      int nParams = bumpyParams.size();
      if (bindings.size() == nParams) {
        for (int i = 0; i < nParams; ++i) {
          bindingsByName.put(bumpyParams.get(i), bindings.get(i));
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
        BName bumpyName, Map<BName, TypeBinding> bindingsByName) {
      FName flatName = flatTypes.getFlatTypeName(bumpyName);
      FlatParamInfo paramInfo = flatTypes.getFlatParamInfo(flatName);
      ImmutableList.Builder<TypeBinding> b = ImmutableList.builder();
      for (BName bumpyParam : paramInfo.bumpyParametersInOrder) {
        TypeBinding untranslatedBinding = bindingsByName.get(bumpyParam);
        if (untranslatedBinding == null) {
          Scope top = scopes.get(scopes.size() - 1);
          TypeBinding inheritedBinding = top.inheritedBindings.get(bumpyParam);
          if (inheritedBinding == null) {
            untranslatedBinding = TypeBinding.WILDCARD;
          } else {
            untranslatedBinding = inheritedBinding;
          }
        }
        if (mode == Mode.COLLECT && untranslatedBinding.typeSpec != null) {
          flatTypes.recordType(BName.of(untranslatedBinding.typeSpec.rawName));
        }
        b.add(untranslatedBinding.subst(
            new Function<Name, TypeBinding>() {

              @SuppressWarnings("synthetic-access")
              @Override
              public TypeBinding apply(Name nm) {
                if (nm.type == Name.Type.CLASS) {
                  return new TypeBinding(
                      TypeSpecification.unparameterized(
                          flatTypes.getFlatTypeName(BName.of(nm)).name));
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

      JminBaseNode nodem;

      Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder
          = new Function<ImmutableList<JminBaseNode>, JminBaseNode>() {

            @Override
            public JminBaseNode apply(
                ImmutableList<JminBaseNode> xlatedChildren) {
              JminNodeVariant vm = VARIANT_MAP.get(v8);
              if (vm != null) {
                if (node8 instanceof J8BaseLeafNode) {
                  Preconditions.checkState(xlatedChildren.isEmpty());
                  return vm.buildNode(node8.getValue());
                } else {
                  return vm.buildNode(xlatedChildren);
                }
              } else {
                throw new Error("No builder for " + v8);
              }
            }
          };

      Xlater xlater = XLATERS.get(v8);
      if (xlater == null) {
        xlater = USE_DEFAULT_BUILDER;
      }
      nodem = xlater.xlate(this, node8, childrenSupplier, defaultBuilder);

      if (nodem != null) {
        nodem.copyMetadataFrom(node8, metadataBridge);
      }
      if (nodem instanceof JminTypeDeclaration) {
        switch (nodem.getNodeType()) {
          case NormalClassDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.ClassDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.ClassDeclarationNode
                    .Variant.NormalClassDeclaration
                    .buildNode(nodem)));
            break;
          case EnumDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.ClassDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.ClassDeclarationNode
                    .Variant.EnumDeclaration
                    .buildNode(nodem)));
            break;
          case NormalInterfaceDeclaration:
            this.typeDeclarations.add(
                com.mikesamuel.cil.ast.jmin.TypeDeclarationNode
                .Variant.InterfaceDeclaration
                .buildNode(
                    com.mikesamuel.cil.ast.jmin.InterfaceDeclarationNode
                    .Variant.NormalInterfaceDeclaration
                    .buildNode(nodem)));
            break;
          case TypeParameter:
            break;
          default:
            throw new AssertionError(nodem);
        }
      }
      return after(node8, nodem);
    }

    J8BaseNode before(J8BaseNode node8) {
      SourcePosition pos = node8.getSourcePosition();
      if (pos != null) { current = pos; }
      if (node8 instanceof TypeDeclaration) {
        J8TypeDeclaration td8 = (J8TypeDeclaration) node8;
        TypeInfo ti = td8.getDeclaredTypeInfo();
        if (ti != null && ti.canonName.type == Name.Type.CLASS) {
          BName bumpyName = BName.of(ti.canonName);
          FName flatName = flatTypes.getFlatTypeName(bumpyName);

          Map<BName, TypeBinding> inheritedBindings = Maps.newLinkedHashMap();
          int nScopes = scopes.size();
          if (nScopes != 0) {
            inheritedBindings.putAll(scopes.get(nScopes - 1).inheritedBindings);
          }
          putBindingsInheritedFromSuperType(ti, inheritedBindings);

          Scope s = new Scope(
              td8, bumpyName, flatName, flatTypes.getFlatParamInfo(flatName),
              ImmutableMap.copyOf(inheritedBindings));
          // Add all the identifiers from this scope so that we can generate
          // non-conflicting names.
          s.identifiersUsed.add(flatName.name.identifier);
          if (!scopes.isEmpty()) {
            s.identifiersUsed.addAll(
                scopes.get(scopes.size() - 1).identifiersUsed);
          }
          for (com.mikesamuel.cil.ast.j8.IdentifierNode id :
                  node8
                  .finder(com.mikesamuel.cil.ast.j8.IdentifierNode.class)
                  .exclude(JminTypeDeclaration.class)
                  .find()) {
            s.identifiersUsed.add(id.getValue());
          }
          for (MemberInfo mi : ti.transitiveMembers(typePool.r)) {
            s.identifiersUsed.add(mi.canonName.identifier);
          }
          scopes.add(s);
        }
      }
      return node8;
    }

    private void putBindingsInheritedFromSuperType(
        TypeInfo ti, Map<BName, TypeBinding> bindings) {
      Iterable<TypeSpecification> allSts = Iterables.concat(
          ti.superType.asSet(), ti.interfaces);
      for (TypeSpecification st : allSts) {
        while (true) {
          Optional<TypeInfo> stiOpt = typePool.r.resolve(st.rawName);
          if (stiOpt.isPresent()) {
            TypeInfo sti = stiOpt.get();
            putBindingsInheritedFromSuperType(sti, bindings);
            int nBindings = st.bindings.size();
            if (nBindings == sti.parameters.size()) {
              for (int i = 0; i < nBindings; ++i) {
                bindings.put(
                    BName.of(sti.parameters.get(i)), st.bindings.get(i));
              }
            }
          }
          if (st.parent instanceof TypeSpecification) {
            st = (TypeSpecification) st.parent;
          } else {
            break;
          }
        }
      }
    }

    /**
     * Updates the SimpleTypeName of a TypeDeclaration to match the flattened
     * name.
     */
    private void updateSimpleName(
        TypeDeclaration<JminBaseNode, JminNodeType, JminNodeVariant> node) {
      com.mikesamuel.cil.ast.jmin.SimpleTypeNameNode nameNode =
          node.firstChildWithType(
              com.mikesamuel.cil.ast.jmin.SimpleTypeNameNode.class);
      com.mikesamuel.cil.ast.jmin.IdentifierNode ident =
          nameNode.firstChildWithType(
              com.mikesamuel.cil.ast.jmin.IdentifierNode.class);
      String flatIdent = node.getDeclaredTypeInfo().canonName.identifier;
      ident.setValue(flatIdent);
    }

    JminBaseNode after(J8BaseNode node8, JminBaseNode nodem) {
      if (node8 instanceof TypeDeclaration) {
        TypeInfo ti = ((TypeDeclaration<?, ?, ?>) node8).getDeclaredTypeInfo();
        if (ti != null && ti.canonName.type == Name.Type.CLASS) {
          @SuppressWarnings("unchecked")  // By node generator convention.
          TypeDeclaration<JminBaseNode, JminNodeType, JminNodeVariant> decl =
              (TypeDeclaration<JminBaseNode, JminNodeType, JminNodeVariant>)
              nodem;
          int scopeIndex = scopes.size() - 1;
          Preconditions.checkState(scopeIndex >= 0);
          Scope top = scopes.get(scopeIndex);
          Preconditions.checkState(top.bumpyName.name.equals(ti.canonName));
          // Fixup the name.
          updateSimpleName(decl);
          // Add any outer type variables to the declarations.
          if (!top.flatParamInfo.flatParametersInOrder.isEmpty()) {
            com.mikesamuel.cil.ast.jmin.TypeParametersNode tps =
                nodem.firstChildWithType(
                    com.mikesamuel.cil.ast.jmin.TypeParametersNode.class);
            if (tps == null) {
              tps = com.mikesamuel.cil.ast.jmin.TypeParametersNode.Variant
                  .LtTypeParameterListGt.buildNode(
                      com.mikesamuel.cil.ast.jmin.TypeParameterListNode
                      .Variant.TypeParameterComTypeParameter.buildNode());

              boolean added = false;
              for (int i = 0, n = nodem.getNChildren(); i < n; ++i) {
                if (nodem.getChild(i).getNodeType()
                    == JminNodeType.SimpleTypeName) {
                  ((JminBaseInnerNode) nodem).add(i + 1, tps);
                  added = true;
                  break;
                }
              }
              Preconditions.checkState(added);
            }
            com.mikesamuel.cil.ast.jmin.TypeParameterListNode tplist
                = tps.firstChildWithType(
                    com.mikesamuel.cil.ast.jmin.TypeParameterListNode.class);
            Map<FName, com.mikesamuel.cil.ast.jmin.TypeParameterNode> existing
                = Maps.newLinkedHashMap();
            for (int i = 0, n = tplist.getNChildren(); i < n; ++i) {
              com.mikesamuel.cil.ast.jmin.TypeParameterNode tp =
                  (com.mikesamuel.cil.ast.jmin.TypeParameterNode)
                  tplist.getChild(i);
              TypeInfo fpi = tp.getDeclaredTypeInfo();
              existing.put(FName.of(fpi.canonName), tp);
            }
            ImmutableList.Builder<JminBaseNode> fullParams =
                ImmutableList.builder();
            Map<BName, com.mikesamuel.cil.ast.j8.TypeParameterNode>
                bumpyTypeParamDecls = Maps.newLinkedHashMap();
            FlatParamInfo flatParamInfo = top.flatParamInfo;
            for (int i = 0, n = flatParamInfo.flatParametersInOrder.size();
                i < n; ++i) {
              FName paramName = flatParamInfo.flatParametersInOrder.get(i);
              com.mikesamuel.cil.ast.jmin.TypeParameterNode tp =
                  existing.remove(paramName);
              if (tp == null) {
                BName origName = flatParamInfo.bumpyParametersInOrder.get(i);
                if (!bumpyTypeParamDecls.containsKey(origName)) {
                  BName outerClassName = BName.of(
                      origName.name.getContainingClass());
                  Scope outerScope = getScope(outerClassName);
                  com.mikesamuel.cil.ast.j8.TypeParametersNode ps =
                      ((J8BaseNode) outerScope.typeDecl).firstChildWithType(
                          com.mikesamuel.cil.ast.j8.TypeParametersNode.class);
                  for (com.mikesamuel.cil.ast.j8.TypeParameterNode p :
                        ps.finder(
                            com.mikesamuel.cil.ast.j8.TypeParameterNode.class)
                        .find()) {
                    TypeInfo pti = p.getDeclaredTypeInfo();
                    if (pti == null) {
                      error(p, "Missing type info");
                    } else {
                      bumpyTypeParamDecls.put(BName.of(pti.canonName), p);
                    }
                  }
                }
                com.mikesamuel.cil.ast.j8.TypeParameterNode p8 =
                    bumpyTypeParamDecls.get(origName);
                tp = (com.mikesamuel.cil.ast.jmin.TypeParameterNode) xlate(p8);
              }
              updateSimpleName(tp);
              fullParams.add(tp);
            }
            tplist.replaceChildren(fullParams.build());
          }

          scopes.remove(scopeIndex);
        }
      }
      return nodem;
    }

    Optional<FName> getOuterRefName(BName bumpyInnerTypeName) {
      Scope scope = getScope(bumpyInnerTypeName);
      if (bumpyInnerTypeName.name.parent.type == Name.Type.PACKAGE) {
        return Optional.absent();
      }
      Optional<FName> fnOpt = bumpyInnerTypeNameToFlatOuterThisFieldName
          .get(bumpyInnerTypeName);
      if (fnOpt == null) {
        BName bumpyOuterTypeName = BName.of(
            bumpyInnerTypeName.name.parent.getContainingClass());

        FName flatOuterTypeName = flatTypes.getFlatTypeName(bumpyOuterTypeName);
        FName flatInnerTypeName = flatTypes.getFlatTypeName(bumpyInnerTypeName);

        String reservedIdentifier = "$$containingInstance";

        // First, reserve an identifier for the outer instance field.
        Optional<TypeInfo> bumpyTi = typePool.r.resolve(
            bumpyInnerTypeName.name);
        Optional<TypeInfo> flatTi = Optional.absent();
        boolean isStaticClass = false;
        if (bumpyTi.isPresent()) {
          TypeInfo ti = bumpyTi.get();
          if (Modifier.isStatic(ti.modifiers)) {
            isStaticClass = true;
          } else {
            flatTi = Optional.of(metadataBridge.bridgeTypeInfo(ti));
          }
        }
        if (isStaticClass) {
          fnOpt = Optional.absent();
        } else {
          if (flatTi.isPresent()) {
            reservedIdentifier = scope.nonConflictingIdentifier(
                "$$containingInstance");
          } else {
            error(current, "Missing type info for " + flatInnerTypeName);
          }

          // Second, compute the outer type by adding any type parameters
          // inherited from the outer type.
          ImmutableList.Builder<TypeBinding> bindings = ImmutableList.builder();
          FlatParamInfo innerParamInfo = flatTypes.getFlatParamInfo(
              flatInnerTypeName);
          FlatParamInfo outerParamInfo = flatTypes.getFlatParamInfo(
              flatOuterTypeName);
          for (BName outerParam : outerParamInfo.bumpyParametersInOrder) {
            bindings.add(new TypeBinding(TypeSpecification.unparameterized(
                innerParamInfo.substMap.get(outerParam).name)));
          }
          TypeSpecification flatTypeInInnerTypeScope = TypeSpecification
              .unparameterized(flatOuterTypeName.name)
              .withBindings(bindings.build());

          // Third, add a field declaration and store the relationship.
          Name fieldName = flatInnerTypeName.name.child(
              reservedIdentifier, Name.Type.FIELD);
          FieldInfo fi = new FieldInfo(Modifier.FINAL, fieldName);
          registerSyntheticMember(fi);
          fi.setValueType(flatTypeInInnerTypeScope);

          fnOpt = Optional.of(FName.of(fieldName));
        }

        bumpyInnerTypeNameToFlatOuterThisFieldName.put(
            bumpyInnerTypeName, fnOpt);
      }
      return fnOpt;
    }

    Optional<JminBaseNode> getThisInContextAsPrimary(Name bumpyTypeName) {
      int lastIndex = scopes.size() - 1;
      int nameIndex = -1;
      for (int i = lastIndex; i >= 0; --i) {
        Scope s = scopes.get(i);
        if (s.bumpyName.name.equals(bumpyTypeName)) {
          nameIndex = i;
          break;
        }
      }
      if (nameIndex < 0) {
        return Optional.absent();
      }
      JminBaseNode primary =
          // reachable via @anon from Primary
          com.mikesamuel.cil.ast.jmin.ExpressionAtomNode.Variant.This
          .buildNode();
      for (int i = lastIndex; i > nameIndex; --i) {
        Scope s = scopes.get(i);
        Optional<FName> outerRefOpt = getOuterRefName(s.bumpyName);

        if (outerRefOpt.isPresent()) {
          FName outerRef = outerRefOpt.get();
          com.mikesamuel.cil.ast.jmin.IdentifierNode ident =
              toIdent(outerRef.name);
          com.mikesamuel.cil.ast.jmin.FieldNameNode fieldName =
              com.mikesamuel.cil.ast.jmin.FieldNameNode.Variant.Identifier
              .buildNode(ident);
          fieldName.setReferencedExpressionName(outerRef.name);
          primary = com.mikesamuel.cil.ast.jmin.PrimaryNode.Variant.FieldAccess
              .buildNode(primary, fieldName);
        } else {
          error(
              (SourcePosition) null, "qualified this for " + s.bumpyName
              + " reaches outside a static type");
        }
      }
      return Optional.of(primary);
    }

    J8ToJmin getMinner() {
      return J8ToJmin.this;
    }

    Scope getScope(int delta) {
      return scopes.get(delta >= 0 ? delta : scopes.size() + delta);
    }

    Scope getScope(BName bumpyTypeName) {
      for (int i = scopes.size(); --i >= 0;) {
        Scope s = scopes.get(i);
        if (s.bumpyName.equals(bumpyTypeName)) {
          return s;
        }
      }
      return null;
    }

    Scope getScope(FName flatTypeName) {
      for (int i = scopes.size(); --i >= 0;) {
        Scope s = scopes.get(i);
        if (s.flatName.equals(flatTypeName)) {
          return s;
        }
      }
      return null;
    }

    void registerSyntheticMember(FieldInfo fi) {
      Name flatTypeName = fi.canonName.getContainingClass();
      Optional<TypeInfo> flatTiOpt = metadataBridge.resolve(flatTypeName);
      if (flatTiOpt.isPresent()) {
        TypeInfo flatTi = flatTiOpt.get();
        flatTi.addSyntheticMember(fi);
      }
      syntheticMembersStillToAdd.put(flatTypeName, fi);
    }


    com.mikesamuel.cil.ast.jmin.ClassTypeNode toClassType(
        TypeSpecification flatType) {
      com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode ct =
          toClassOrInterfaceType(flatType);
      com.mikesamuel.cil.ast.jmin.ClassTypeNode t =
          com.mikesamuel.cil.ast.jmin.ClassTypeNode
          .Variant.ClassOrInterfaceType
          .buildNode(ct);
      t.setStaticType(ct.getStaticType());
      return t;
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
            (SourcePosition) null,
            "Minimal java does not allow classes in the default package "
            + flat
            );
      }
      b.addAll(annots);
      b.add(className);
      if (typeArgs != null) {
        b.add(typeArgs);
      }
      com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode t =
          com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode
          .Variant.PackageNameDotAnnotationIdentifierTypeArguments
          .buildNode(b.build());

      t.setStaticType(bridgedTypePool.type(flat, current, logger));
      Optional<TypeInfo> ti = metadataBridge.resolve(flat.rawName);
      if (ti.isPresent()) {
        t.setReferencedTypeInfo(ti.get());
      }
      return t;
    }

    com.mikesamuel.cil.ast.jmin.ArrayTypeNode toArrayType(
        TypeSpecification flatElementType) {
      return com.mikesamuel.cil.ast.jmin.ArrayTypeNode
          .Variant.TypeAnnotationDim
          .buildNode(
              toType(flatElementType),
              com.mikesamuel.cil.ast.jmin.DimNode.Variant.LsRs.buildNode())
          .setStaticType(
              bridgedTypePool.type(flatElementType.arrayOf(), null, logger));
    }

    com.mikesamuel.cil.ast.jmin.ReferenceTypeNode toReferenceType(
        TypeSpecification flat) {
      if (flat.nDims != 0) {
        com.mikesamuel.cil.ast.jmin.ArrayTypeNode arrType =
            toArrayType(flat.withNDims(flat.nDims - 1));
        return com.mikesamuel.cil.ast.jmin.ReferenceTypeNode.Variant.ArrayType
            .buildNode(arrType)
            .setStaticType(arrType.getStaticType());
      }
      switch (flat.rawName.type) {
        case TYPE_PARAMETER: {
          com.mikesamuel.cil.ast.jmin.TypeVariableNode tv =
              com.mikesamuel.cil.ast.jmin.TypeVariableNode.Variant
              .AnnotationIdentifier.buildNode(toIdent(flat.rawName));
          Optional<TypeInfo> tiOpt = metadataBridge.resolve(flat.rawName);
          if (tiOpt.isPresent()) {
            tv.setReferencedTypeInfo(tiOpt.get());
          }
          com.mikesamuel.cil.ast.jmin.ReferenceTypeNode refType =
              com.mikesamuel.cil.ast.jmin.ReferenceTypeNode.Variant
              .TypeVariable.buildNode(tv);
          refType.setStaticType(bridgedTypePool.type(flat, null, logger));
          return refType;
        }
        case CLASS: {
          com.mikesamuel.cil.ast.jmin.ClassOrInterfaceTypeNode ct =
              toClassOrInterfaceType(flat);
          com.mikesamuel.cil.ast.jmin.ReferenceTypeNode refType =
              com.mikesamuel.cil.ast.jmin.ReferenceTypeNode.Variant
              .ClassOrInterfaceType.buildNode(ct);
          refType.setStaticType(ct.getStaticType());
          return refType;
        }
        case AMBIGUOUS:
        case FIELD:
        case LOCAL:
        case METHOD:
        case PACKAGE:
          break;
      }
      throw new AssertionError(flat);

    }

    com.mikesamuel.cil.ast.jmin.TypeNode toType(TypeSpecification flat) {
      if (flat.nDims != 0 || flat.rawName.type != Name.Type.FIELD) {
        return com.mikesamuel.cil.ast.jmin.TypeNode
            .Variant.ReferenceType.buildNode(toReferenceType(flat));
      } else {
        throw new Error("TODO");  // TODO primitive type
      }
    }

    com.mikesamuel.cil.ast.jmin.UnannTypeNode toUnannType(
        TypeSpecification flat) {
      com.mikesamuel.cil.ast.jmin.TypeNode t = toType(flat);
      return com.mikesamuel.cil.ast.jmin.UnannTypeNode.Variant.NotAtType
          .buildNode(t)
          .setStaticType(
              t.finder(JminWholeType.class).exclude(JminWholeType.class)
              .findOne().get()
              .getStaticType());
    }


    @Nullable
    com.mikesamuel.cil.ast.jmin.TypeArgumentsNode toTypeArguments(
        Iterable<? extends TypeBinding> bindings) {
      if (Iterables.isEmpty(bindings)) { return null; }

      ImmutableList.Builder<JminBaseNode> typeArguments =
          ImmutableList.builder();
      for (TypeBinding binding : bindings) {
        com.mikesamuel.cil.ast.jmin.ReferenceTypeNode refType =
            binding.typeSpec != null
            ? toReferenceType(binding.typeSpec)
            : null;
        if (binding.typeSpec == null) {
          throw new AssertionError(binding);
        }
        com.mikesamuel.cil.ast.jmin.TypeArgumentNode typeArgument = null;
        com.mikesamuel.cil.ast.jmin.WildcardBoundsNode wildcardBounds = null;
        switch (binding.variance) {
          case EXTENDS: {
            com.mikesamuel.cil.ast.jmin.WildcardNode wildcard;
            if (refType != null) {
              wildcardBounds = com.mikesamuel.cil.ast.jmin.WildcardBoundsNode
                  .Variant.ExtendsReferenceType
                  .buildNode(refType);
              wildcard = com.mikesamuel.cil.ast.jmin.WildcardNode
                  .Variant.AnnotationQmWildcardBounds.buildNode(wildcardBounds);
            } else {
              wildcard = com.mikesamuel.cil.ast.jmin.WildcardNode
                  .Variant.AnnotationQmWildcardBounds.buildNode();
            }
            typeArgument = com.mikesamuel.cil.ast.jmin.TypeArgumentNode
                .Variant.Wildcard.buildNode(wildcard);
            break;
          }
          case INVARIANT:
            typeArgument = com.mikesamuel.cil.ast.jmin.TypeArgumentNode
                .Variant.ReferenceType.buildNode(
                    Preconditions.checkNotNull(refType));
            break;
          case SUPER: {
            wildcardBounds = com.mikesamuel.cil.ast.jmin.WildcardBoundsNode
                .Variant.SuperReferenceType.buildNode(
                    Preconditions.checkNotNull(refType));
            com.mikesamuel.cil.ast.jmin.WildcardNode wildcard =
                com.mikesamuel.cil.ast.jmin.WildcardNode
                .Variant.AnnotationQmWildcardBounds.buildNode(wildcardBounds);
            typeArgument = com.mikesamuel.cil.ast.jmin.TypeArgumentNode
                .Variant.Wildcard.buildNode(wildcard);
            break;
          }
        }
        typeArguments.add(Preconditions.checkNotNull(typeArgument));
      }

      com.mikesamuel.cil.ast.jmin.TypeArgumentListNode typeArgumentList =
          com.mikesamuel.cil.ast.jmin.TypeArgumentListNode.Variant
          .TypeArgumentComTypeArgument
          .buildNode(typeArguments.build());
      return com.mikesamuel.cil.ast.jmin.TypeArgumentsNode
          .Variant.LtTypeArgumentListGt.buildNode(typeArgumentList);
    }

  }

  static abstract class Xlater {
    abstract @Nullable
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder);
  }

  static abstract class SimpleXlater extends Xlater {
    abstract @Nullable
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier);

    final @Nullable @Override
    JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder) {
      return xlate(xlator, n8, childrenSupplier);
    }
}

  static final Xlater IGNORE_XLATER = new Xlater() {

    @Override
    public @Nullable JminBaseNode xlate(
        Translator xlator,
        J8BaseNode n8, Supplier<ImmutableList<JminBaseNode>> children,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      return null;
    }

  };

  static final Xlater USE_DEFAULT_BUILDER = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> builder) {
      return builder.apply(childrenSupplier.get());
    }

  };


  static final Xlater DROP_UNLESS_HAS_CHILDREN = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      ImmutableList<JminBaseNode> children = childrenSupplier.get();
      if (children.isEmpty()) { return null; }
      return defaultBuilder.apply(children);
    }

  };

  static final Xlater EVALUATE_AND_DROP = new Xlater() {

    @Override
    public JminBaseNode xlate(
        Translator xlator, J8BaseNode n8,
        Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
        Function<ImmutableList<JminBaseNode>, JminBaseNode> defaultBuilder) {
      childrenSupplier.get();
      return null;
    }

  };

  static final
  ImmutableMap<J8NodeVariant, Xlater> XLATERS =
      ImmutableMap.<J8NodeVariant, Xlater>builder()
      .put(
          com.mikesamuel.cil.ast.j8.AnnotationNode.Variant.MarkerAnnotation,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.AnnotationNode.Variant
                  .NormalAnnotation.buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.
          AnnotationNode.Variant.SingleElementAnnotation,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> children) {
              throw new Error();  // TODO: add the implied element name
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.
          ClassBodyDeclarationNode.Variant.ClassMemberDeclaration,
          // Class members are pulled out into flat types, so don't leave
          // little class member declaration droppings inside
          DROP_UNLESS_HAS_CHILDREN)
      .put(
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.ClassDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          ConstructorDeclarationNode.Variant.Declaration,
          new Xlater() {

            @Override
            JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier,
                Function<ImmutableList<JminBaseNode>, JminBaseNode> builder) {
              com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode ctor8 =
                  (com.mikesamuel.cil.ast.j8.ConstructorDeclarationNode) n8;

              com.mikesamuel.cil.ast.jmin.ConstructorDeclarationNode ctor =
                  (com.mikesamuel.cil.ast.jmin.ConstructorDeclarationNode)
                  builder.apply(childrenSupplier.get());

              com.mikesamuel.cil.ast.jmin.ConstructorDeclaratorNode declarator
                  = ctor.firstChildWithType(
                      com.mikesamuel.cil.ast.jmin.ConstructorDeclaratorNode
                      .class);
              Scope s = xlator.getScope(-1);
              {
                com.mikesamuel.cil.ast.jmin.SimpleTypeNameNode nameNode =
                    declarator.firstChildWithType(
                        com.mikesamuel.cil.ast.jmin.SimpleTypeNameNode.class);
                com.mikesamuel.cil.ast.jmin.IdentifierNode ctorName =
                    nameNode.firstChildWithType(
                        com.mikesamuel.cil.ast.jmin.IdentifierNode.class);
                ctorName.setValue(s.flatName.name.identifier);
              }

              CallableInfo ctorInfo = (CallableInfo)
                  xlator.metadataBridge.bridgeMemberInfo(
                      ctor8.getMemberInfo());
              if (ctorInfo == null) {
                xlator.error(
                    n8, "Missing info for constructor "
                    + ctor.getMethodVariant());
                return ctor;
              }

              Optional<FName> outerRefOpt = xlator.getOuterRefName(s.bumpyName);
              Optional<TypeInfo> tiOpt = xlator.getMinner().typePool.r.resolve(
                  s.bumpyName.name);
              if (!tiOpt.isPresent()) {
                xlator.error(n8, "Missing type info for " + s.bumpyName);
                return ctor;
              }
              TypeInfo ti = tiOpt.get();
              Optional<FName> superOuterRef = xlator.getOuterRefName(
                  BName.of(ti.superType.get().rawName));
              if (!(outerRefOpt.isPresent() || superOuterRef.isPresent())) {
                return ctor;
              }

              // If the constructor forwards to this, then we need to forward
              // any outer references this requires, including any that the
              // destination constructor requires for its call to super.
              //
              // If the constructor invokes super, then we need to forward
              // any outer reference that the super type requires.
              //
              // So we do several things.
              // I. We declare between 0 and 2 extra formal parameters
              //    1. The super-type outer reference.
              //    2. This class's outer reference.
              // II. We rewrite the constructor chaining call to pass between
              //     0 and 2 actual parameters that include
              //    1. The super-type outer reference if there is one.
              //    2. This class's outer reference if there is one and the
              //       chaining call is a this(...) call.
              // III.If the chaining call is a super(...) call and the class
              // has an outer reference then we add a statement that assigns it.
              com.mikesamuel.cil.ast.jmin.ConstructorBodyNode body
                  = ctor.firstChildWithType(
                      com.mikesamuel.cil.ast.jmin.ConstructorBodyNode.class);
              com.mikesamuel.cil.ast.jmin
              .ExplicitConstructorInvocationNode chainingCall
                  = body.firstChildWithType(
                      com.mikesamuel.cil.ast.jmin
                      .ExplicitConstructorInvocationNode.class);

              boolean callsSuper = true;
              switch (chainingCall.getVariant()) {
                case TypeArgumentsSuperLpArgumentListRpSem:
                  break;
                case TypeArgumentsThisLpArgumentListRpSem:
                  callsSuper = false;
                  break;
              }

              SourcePosition pos = ctor.getSourcePosition();
              if (outerRefOpt.isPresent()) {
                FName fieldName = outerRefOpt.get();
                TypeSpecification type = fieldType(ctor, xlator, fieldName);
                Name paramName = addFormalParameter(
                    xlator, ctorInfo.canonName, declarator,
                    type, fieldName.name.identifier, 0);
                com.mikesamuel.cil.ast.jmin.ExpressionNode ref =
                    localReference(xlator, pos, type, paramName);
                if (callsSuper) {
                  StaticType thisType = null;  // TODO
                  com.mikesamuel.cil.ast.jmin.ExpressionAtomNode obj =
                      com.mikesamuel.cil.ast.jmin
                      .ExpressionAtomNode
                      .Variant.This.buildNode()
                      .setStaticType(thisType);
                  addAssignment(body, obj, fieldName, ref, 0);
                } else {
                  addActualParameter(chainingCall, ref, 0);
                }
              }
              if (superOuterRef.isPresent()) {
                FName fieldName = superOuterRef.get();
                TypeSpecification type = fieldType(ctor, xlator, fieldName);
                Name paramName = addFormalParameter(
                    xlator, ctorInfo.canonName, declarator,
                    type,
                    s.nonConflictingIdentifier("$$superContainingInstance"),
                    0);
                com.mikesamuel.cil.ast.jmin.ExpressionNode ref =
                    localReference(xlator, pos, type, paramName);
                ref.setStaticType(
                    xlator.bridgedTypePool.type(
                        type, ctor.getSourcePosition(),
                        xlator.getMinner().logger));
                addActualParameter(chainingCall, ref, 0);
              }
              return ctor;
            }

            private void addAssignment(
                com.mikesamuel.cil.ast.jmin.ConstructorBodyNode body,
                com.mikesamuel.cil.ast.jmin.ExpressionAtomNode obj,
                FName fieldName,
                com.mikesamuel.cil.ast.jmin.ExpressionNode rhs, int index) {
              StaticType t = rhs.getStaticType();
              com.mikesamuel.cil.ast.jmin.AssignmentNode assign =
                  com.mikesamuel.cil.ast.jmin.AssignmentNode
                  .Variant.LeftHandSideAssignmentOperatorExpression.buildNode(
                      com.mikesamuel.cil.ast.jmin.LeftHandSideNode
                      .Variant.FieldAccess.buildNode(
                          com.mikesamuel.cil.ast.jmin.PrimaryNode
                          .Variant.FieldAccess.buildNode(
                              obj,
                              com.mikesamuel.cil.ast.jmin
                              .FieldNameNode.Variant.Identifier.buildNode(
                                  toIdent(fieldName.name))
                              )
                              .setStaticType(t)
                          ),
                      com.mikesamuel.cil.ast.jmin
                      .AssignmentOperatorNode.Variant.Eq.buildNode(),
                      rhs)
                  .setStaticType(t);
              com.mikesamuel.cil.ast.jmin.StatementExpressionNode assignStmt =
                  com.mikesamuel.cil.ast.jmin
                  .StatementExpressionNode.Variant.Assignment.buildNode(
                      assign);

              com.mikesamuel.cil.ast.jmin.BlockStatementNode blockStmt =
                  com.mikesamuel.cil.ast.jmin
                  .BlockStatementNode.Variant.Statement.buildNode(
                      com.mikesamuel.cil.ast.jmin
                      .StatementNode.Variant.ExpressionStatement.buildNode(
                          com.mikesamuel.cil.ast.jmin.ExpressionStatementNode
                          .Variant.StatementExpressionSem.buildNode(assignStmt)
                          )
                      );

              com.mikesamuel.cil.ast.jmin.BlockStatementsNode stmts =
                  body.firstChildWithType(
                      com.mikesamuel.cil.ast.jmin.BlockStatementsNode.class);
              if (stmts == null) {
                stmts = com.mikesamuel.cil.ast.jmin
                    .BlockStatementsNode.Variant
                    .BlockStatementBlockStatementBlockTypeScope
                    .buildNode(blockStmt);
                body.add(stmts);
              } else {
                switch (stmts.getVariant()) {
                  case BlockStatementBlockStatementBlockTypeScope:
                    stmts.add(index, blockStmt);
                    return;
                  case BlockTypeScope:
                    int bodyIndex = stmts.getChildren().indexOf(body);
                    com.mikesamuel.cil.ast.jmin
                    .BlockStatementsNode newBody
                        = com.mikesamuel.cil.ast.jmin
                        .BlockStatementsNode.Variant
                        .BlockStatementBlockStatementBlockTypeScope
                        .buildNode(ImmutableList.<JminBaseNode>builder()
                            .add(blockStmt)
                            .addAll(body.getChildren())
                            .build());
                    body.replace(
                        bodyIndex,
                        newBody);
                    return;
                }
                throw new AssertionError(stmts);
              }
            }

            TypeSpecification fieldType(
                JminBaseNode context, Translator xlator, FName fieldName) {
              Optional<FieldInfo> fi = xlator.metadataBridge.resolveField(
                  fieldName.name);
              if (fi.isPresent()) {
                TypeSpecification type = fi.get().getValueType();
                if (type != null) {
                  Scope scope = xlator.getScope(-1);
                  if (!scope.inheritedBindings.isEmpty()) {
                    type = type.subst(
                        new Function<Name, TypeBinding>() {

                          @Override
                          public TypeBinding apply(Name flatTypeName) {
                            FName typeName = FName.of(flatTypeName);
                            @SuppressWarnings("synthetic-access")
                            BName bumpyTypeName = xlator.flatTypes
                                .getBumpyTypeName(typeName);
                            TypeBinding bumpyBinding = scope.inheritedBindings
                                .get(bumpyTypeName);
                            if (bumpyBinding != null) {
                              return xlator.metadataBridge.bridgeTypeBinding(
                                  bumpyBinding);
                            }
                            return null;
                          }

                        });
                  }
                  return type;
                } else {
                  xlator.error(
                      context, "Missing type for field " + fieldName);
                }
              } else {
                xlator.error(
                    context, "No info for field " + fieldName);
              }
              return JavaLang.JAVA_LANG_OBJECT;
            }

            Name addFormalParameter(
                Translator xlator, Name callableName,
                JminBaseNode declarator,
                TypeSpecification type, String identifier, int index) {
              Name localName = callableName.child(
                  identifier, Name.Type.LOCAL);
              // Find the formal parameter list.  If there is none,
              // manufacture one.
              int nDeclaratorChildren = declarator.getNChildren();
              JminBaseNode formalParameterList =
                  nDeclaratorChildren == 0
                  ? null : declarator.getChild(nDeclaratorChildren - 1);
              com.mikesamuel.cil.ast.jmin.UnannTypeNode unannType =
                  xlator.toUnannType(type);
              com.mikesamuel.cil.ast.jmin
              .VariableDeclaratorIdNode declaratorId =
                  com.mikesamuel.cil.ast.jmin.VariableDeclaratorIdNode
                  .Variant.Identifier.buildNode(toIdent(localName))
                  .setDeclaredExpressionName(localName);
              com.mikesamuel.cil.ast.jmin.FormalParameterNode formal =
                  com.mikesamuel.cil.ast.jmin.FormalParameterNode
                  .Variant.Declaration
                  .buildNode(unannType, declaratorId);
              if (formalParameterList == null
                  || (formalParameterList.getNodeType()
                      != JminNodeType.FormalParameterList)) {
                formalParameterList = com.mikesamuel.cil.ast.jmin
                    .FormalParameterListNode
                    .Variant.FormalParameterComFormalParameter
                    .buildNode(formal);
                ((JminBaseInnerNode) declarator).add(formalParameterList);
              } else {
                ((JminBaseInnerNode) formalParameterList)
                .add(index, formal);
              }
              return localName;
            }

            com.mikesamuel.cil.ast.jmin
            .ExpressionNode localReference(
                Translator xlator, SourcePosition pos,
                TypeSpecification type, Name localName) {
              StaticType t = xlator.bridgedTypePool.type(
                      type, pos,
                      xlator.getMinner().logger);

              return com.mikesamuel.cil.ast.jmin
                  .ExpressionNode.Variant.ConditionalExpression.buildNode(
                      com.mikesamuel.cil.ast.jmin
                      .ExpressionAtomNode.Variant.Local.buildNode(
                          com.mikesamuel.cil.ast.jmin
                          .LocalNameNode.Variant.Identifier.buildNode(
                              toIdent(localName)))
                      .setStaticType(t))
                  .setStaticType(t);
            }

            void addActualParameter(
                com.mikesamuel.cil.ast.jmin
                .ExplicitConstructorInvocationNode call,
                com.mikesamuel.cil.ast.jmin.ExpressionNode ref,
                int index) {
              int nCallChildren = call.getNChildren();
              JminBaseNode argumentList =
                  nCallChildren != 0
                  ? call.getChild(nCallChildren - 1)
                  : null;
              if (argumentList != null
                  && argumentList.getNodeType() == JminNodeType.ArgumentList) {
                ((JminBaseInnerNode) argumentList).add(index, ref);
              } else {
                argumentList = com.mikesamuel.cil.ast.jmin
                    .ArgumentListNode.Variant.ExpressionComExpression
                    .buildNode(ref);
                ((JminBaseInnerNode) call).add(argumentList);
              }
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.InterfaceDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          ClassMemberDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ClassOrInterfaceTypeNode.Variant
          .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> children) {
              com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode t8 =
                  (com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode) n8;
              StaticType t = t8.getStaticType();
              if (!(t instanceof StaticType.TypePool.ClassOrInterfaceType)) {
                xlator.error(
                    n8,
                    t == null
                    ? "Missing type information for " + t8
                    : t + " is not a class or interface type");
                t = xlator.type(
                    JavaLang.JAVA_LANG_OBJECT, n8.getSourcePosition());
              }
              TypeSpecification flat = xlator.flatType(
                  (TypePool.ClassOrInterfaceType) t);
              return xlator.toClassOrInterfaceType(flat);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .ConstructorBodyNode.Variant
          .LcExplicitConstructorInvocationBlockStatementsRc,
          new SimpleXlater() {

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
          com.mikesamuel.cil.ast.j8
          .CompilationUnitNode.Variant
          .PackageDeclarationImportDeclarationTypeDeclaration,
          new SimpleXlater() {

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
      .put(
          com.mikesamuel.cil.ast.j8.EnumConstantNode.Variant.Declaration,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              Iterable<JminBaseNode> children = childrenSupplier.get();
              children = Iterables.filter(
                  children,
                  Predicates.not(nodeTypeIn(JminNodeType.ClassBody)));
              return com.mikesamuel.cil.ast.jmin.EnumConstantNode
                  .Variant.Declaration.buildNode(children);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.ExpressionAtomNode.Variant.This,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              com.mikesamuel.cil.ast.j8.TypeNameNode outerType =
                  n8.firstChildWithType(
                      com.mikesamuel.cil.ast.j8.TypeNameNode.class);
              if (outerType != null) {
                TypeInfo ti = outerType.getReferencedTypeInfo();
                if (ti != null) {
                  Optional<JminBaseNode> thisRefOpt =
                      xlator.getThisInContextAsPrimary(ti.canonName);
                  if (thisRefOpt.isPresent()) {
                    JminBaseNode thisRef = thisRefOpt.get();
                    if (thisRef.getNodeType() == JminNodeType.Primary) {
                      thisRef = com.mikesamuel.cil.ast.jmin.ExpressionAtomNode
                          .Variant.Parenthesized.buildNode(
                              com.mikesamuel.cil.ast.jmin.ExpressionNode
                              .Variant.ConditionalExpression.buildNode(
                                  thisRef));
                    }
                    return thisRef;
                  } else {
                    xlator.error(
                        n8,
                        "Qualified `this` out of scope: "
                        + outerType.getTextContent("."));
                  }
                } else {
                  xlator.error(
                      n8,
                      "Missing type info for qualified `this`: "
                      + outerType.getTextContent("."));
                }
              }
              return com.mikesamuel.cil.ast.jmin.ExpressionAtomNode.Variant
                  .This.buildNode();
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParameterListNode.Variant
          .FormalParametersComLastFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              ImmutableList.Builder<JminBaseNode> formals
                  = ImmutableList.builder();
              for (JminBaseNode child : children) {
                JminNodeType nt = child.getNodeType();
                if (nt == JminNodeType.FormalParameterList) {
                  // Receiver parameters are dropped elsewhere,
                  // variadiac parameters are retyped elsewhere,
                  // but the FormalParameters intermediate list is rewrapped
                  // as a FormalParameterListNode so we unpack it here.
                  formals.addAll(child.getChildren());
                } else {
                  formals.add(child);
                }
              }
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(formals.build());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParameterListNode.Variant.LastFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParametersNode.Variant.FormalParameterComFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .FormalParametersNode.Variant.ReceiverParameterComFormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              if (children.isEmpty()) { return null; }
              return com.mikesamuel.cil.ast.jmin.FormalParameterListNode
                  .Variant.FormalParameterComFormalParameter
                  .buildNode(children);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.SingleTypeImportDeclaration,
           IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.TypeImportOnDemandDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.SingleStaticImportDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .ImportDeclarationNode.Variant.StaticImportOnDemandDeclaration,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.ClassDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.InterfaceDeclaration,
          // The controller will capture the type declarations so that
          // flat types translated from inner types are given their own
          // compilation unit.
          EVALUATE_AND_DROP)
      .put(
          com.mikesamuel.cil.ast.j8.
          InterfaceMemberDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .LastFormalParameterNode.Variant.FormalParameter,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              Preconditions.checkState(children.size() == 1);
              return children.get(0);
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .LastFormalParameterNode.Variant.Variadic,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              throw new Error("TODO");  // TODO
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8.MarkerAnnotationNode.Variant.AtTypeName,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              return com.mikesamuel.cil.ast.jmin.NormalAnnotationNode.Variant
                  .AtTypeNameLpElementValuePairListRp
                  .buildNode(childrenSupplier.get());
            }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .NormalClassDeclarationNode.Variant.Declaration,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              for (int i = 0, n = children.size(); i < n; ++i) {
                JminNodeType nt = children.get(i).getNodeType();
                if (nt == JminNodeType.Superclass) { break; }
                if (nt != JminNodeType.JavaDocComment
                    && nt != JminNodeType.Modifier
                    && nt != JminNodeType.SimpleTypeName
                    && nt != JminNodeType.TypeParameters) {
                  com.mikesamuel.cil.ast.jmin.SuperclassNode extendsObject =
                      com.mikesamuel.cil.ast.jmin.SuperclassNode.Variant
                      .ExtendsClassType.buildNode(
                          xlator.toClassType(JavaLang.JAVA_LANG_OBJECT));
                  children = ImmutableList.<JminBaseNode>builder()
                      .addAll(children.subList(0, i))
                      .add(extendsObject)
                      .addAll(children.subList(i, n))
                      .build();
                  break;
                }
              }
              return com.mikesamuel.cil.ast.jmin.NormalClassDeclarationNode
                  .Variant.Declaration.buildNode(children);
            }

          })
      .put(
           com.mikesamuel.cil.ast.j8.
           PackageDeclarationNode.Variant.Declaration,
           new SimpleXlater() {

             @Override
             public JminBaseNode xlate(
                 Translator xlator, J8BaseNode n8,
                 Supplier<ImmutableList<JminBaseNode>> children) {
               return com.mikesamuel.cil.ast.jmin.
                   PackageDeclarationNode.Variant.Declaration.buildNode(
                       Iterables.filter(
                           children.get(),
                           nodeTypeIn(JminNodeType.PackageName)));
             }

          })
      .put(
          com.mikesamuel.cil.ast.j8
          .ReceiverParameterNode.Variant
          .AnnotationUnannTypeSimpleTypeNameDotThis,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .TypeDeclarationNode.Variant.Sem,
          IGNORE_XLATER)
      .put(
          com.mikesamuel.cil.ast.j8
          .VariableDeclaratorIdNode.Variant.IdentifierDims,
          new SimpleXlater() {

            @Override
            public JminBaseNode xlate(
                Translator xlator, J8BaseNode n8,
                Supplier<ImmutableList<JminBaseNode>> childrenSupplier) {
              ImmutableList<JminBaseNode> children = childrenSupplier.get();
              com.mikesamuel.cil.ast.jmin.IdentifierNode ident =
                  (com.mikesamuel.cil.ast.jmin.IdentifierNode) children.get(0);
              if (children.size() != 1) {
                xlator.error(
                    n8, "Trailing [] in variable declaration."
                    + "  Did you forget to run the defragment types pass?");
              }

              return com.mikesamuel.cil.ast.jmin.VariableDeclaratorIdNode
                  .Variant.Identifier.buildNode(ident);
            }

          })
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

  /**
   * Information about the scope in which we're xlating fragments.
   */
  static final class Scope {
    final J8TypeDeclaration typeDecl;
    final BName bumpyName;
    final FName flatName;
    final FlatParamInfo flatParamInfo;
    final Set<String> identifiersUsed;
    /**
     * Inherited bindings that are implied by class nesting but must be
     * made explicit in flattened inner classes.
     */
    final ImmutableMap<BName, TypeBinding> inheritedBindings;

    Scope(
        J8TypeDeclaration typeDecl,
        BName bumpyName, FName flatName, FlatParamInfo flatParamInfo,
        ImmutableMap<BName, TypeBinding> inheritedBindings) {
      this.typeDecl = typeDecl;
      this.bumpyName = bumpyName;
      this.flatName = flatName;
      this.flatParamInfo = flatParamInfo;
      this.identifiersUsed = Sets.newHashSet();
      this.inheritedBindings = inheritedBindings;
    }

    /**
     * A name that does not conflict with that of any of the given type's
     * members.
     */
    String nonConflictingIdentifier(String prefix) {
      if (identifiersUsed.add(prefix)) {
        return prefix;
      }

      int counter = 0;
      StringBuilder sb = new StringBuilder(prefix).append('_');
      int length = sb.length();
      while (true) {
        sb.setLength(length);
        sb.append(counter);
        String candidate = sb.toString();
        if (identifiersUsed.add(candidate)) {
          return candidate;
        }
        ++counter;
      }
    }

  }
}
