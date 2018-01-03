package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.ContextFreeNameNode;
import com.mikesamuel.cil.ast.j8.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.j8.DiamondNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.FieldNameNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameScope;
import com.mikesamuel.cil.ast.j8.J8LimitedScopeElement;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8TypeReference;
import com.mikesamuel.cil.ast.j8.J8TypeScope;
import com.mikesamuel.cil.ast.j8.LocalNameNode;
import com.mikesamuel.cil.ast.j8.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsOrDiamondNode;
import com.mikesamuel.cil.ast.j8.TypeNameNode;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
       .DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.parser.Positioned;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A pass that looks at {@link J8NodeType#ContextFreeNames} and converts them to
 * type, field, and method references as appropriate and sets
 * {@linkplain TypeReference#setReferencedTypeInfo TypeReference meta-data}.
 * <p>
 * This happens before computing types for expressions which means we can't
 * attach declaring types to field names yet.  We also don't distinguish yet
 * between
 * <ol>
 *   <li>enum field references and constant references in switch cases.
 *     <pre>switch (x) { case FOO; case BAR; }</pre>
 *     because the names {@code FOO} and {@code BAR} could appear in multiple
 *     {@code enum}s and also be constant local or field accesses in scope.
 *   <li>qualified class instance creation expressions.
 *     <pre>foo.new Bar()</pre>
 *     because multiple classes might have inner class {@code Bar}s, and we need
 *     to know the parameterization of type variables of {@code foo} to type
 *     methods and fields in the created instance.
 * </ol>
 */
final class DisambiguationPass extends AbstractRewritingPass {

  private final List<J8TypeScope> typeScopes = Lists.newArrayList();
  private final List<J8ExpressionNameScope> nameScopes = Lists.newArrayList();
  private final TypeInfoResolver typeInfoResolver;
  private final boolean useLongNames;

  DisambiguationPass(
      TypeInfoResolver typeInfoResolver, Logger logger,
      boolean useLongNames) {
    super(logger);
    this.typeInfoResolver = typeInfoResolver;
    this.useLongNames = useLongNames;
  }

  @Override
  protected ProcessingStatus previsit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (node instanceof J8TypeScope) {
      typeScopes.add((J8TypeScope) node);
    }
    if (node instanceof J8ExpressionNameScope) {
      nameScopes.add((J8ExpressionNameScope) node);
    }
    return ProcessingStatus.CONTINUE;
  }

  @Override
  protected ProcessingStatus postvisit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    ProcessingStatus result = ProcessingStatus.CONTINUE;

    // By inspection of the grammar, context free names
    // never have non-template siblings.
    int cfnIndex = node.finder(ContextFreeNamesNode.class).indexOf();
    if (cfnIndex >= 0) {
      result = rewriteContextFreeNames(
          pathFromRoot, node, (ContextFreeNamesNode) node.getChild(cfnIndex));
    } else {
      switch (node.getNodeType()) {
        case FieldName: {
          IdentifierNode id = node.firstChildWithType(IdentifierNode.class);
          id.setNamePartType(Name.Type.FIELD);
          if (pathFromRoot != null
              && (ExpressionAtomNode.Variant.FreeField
                  == pathFromRoot.x.parent.getVariant())) {
            Optional<Name> nameOpt = expressionNameOf(
                pathFromRoot, id.getValue());
            if (nameOpt.isPresent()) {
              Name name = nameOpt.get();
              if (name.type == Name.Type.FIELD) {
                ((FieldNameNode) node).setReferencedExpressionName(name);
              }
            }
          }
          break;
        }
        case LocalName: {
          IdentifierNode id = node.firstChildWithType(IdentifierNode.class);
          id.setNamePartType(Name.Type.LOCAL);
          Optional<Name> nameOpt = expressionNameOf(
              pathFromRoot, id.getValue());
          if (nameOpt.isPresent()) {
            Name name = nameOpt.get();
            if (name.type == Name.Type.LOCAL) {
              ((LocalNameNode) node).setReferencedExpressionName(name);
            }
          }
          break;
        }
        case MethodName: {
          IdentifierNode id = node.firstChildWithType(IdentifierNode.class);
          id.setNamePartType(Name.Type.METHOD);
          break;
        }
        case ClassOrInterfaceType:
        case PackageOrTypeName:
        case TypeName:
        case TypeVariable:
          if (pathFromRoot != null) {
            boolean isPartial = false;
            switch (pathFromRoot.x.parent.getNodeType()) {
              case ClassOrInterfaceType:
              case PackageOrTypeName:
              case TypeImportOnDemandDeclaration:
              case TypeName:
                isPartial = true;
                break;
              case ClassOrInterfaceTypeToInstantiate:
                // We skip the type "Inner" in (outer.new Inner(...))
                // since it is not a complete type.
                if (pathFromRoot.prev != null && pathFromRoot.prev.prev != null
                    && (pathFromRoot.prev.x.parent.getNodeType()
                       == J8NodeType.UnqualifiedClassInstanceCreationExpression)
                    && (pathFromRoot.prev.prev.x.parent.getVariant()
                       == PrimaryNode.Variant.InnerClassCreation)) {
                  isPartial = true;
                }
                break;
              default:
                break;
            }
            if (isPartial) {
              break;
            }
          }
          resolveContextualizedTypeName(node);
          break;
        default:
          break;
      }
    }
    if (node instanceof J8TypeScope) {
      J8TypeScope popped = typeScopes.remove(typeScopes.size() - 1);
      Preconditions.checkState(popped == node);
    }
    if (node instanceof J8ExpressionNameScope) {
      J8ExpressionNameScope popped = nameScopes.remove(nameScopes.size() - 1);
      Preconditions.checkState(popped == node);
    }

    return result;
  }

  private void resolveContextualizedTypeName(J8BaseNode node) {
    ImmutableList<IdentifierNode> idents =
        node.finder(IdentifierNode.class)
        .exclude(J8NodeType.Annotation, J8NodeType.TypeArguments,
            J8NodeType.TypeParameters)
        .find();
    Name name = null;
    for (IdentifierNode ident : idents) {
      String identifier = ident.getValue();
      Name.Type type = ident.getNamePartType();
      if (type == null) {
        if (idents.size() == 1
            && node.getNodeType() == J8NodeType.TypeVariable) {
          type = Name.Type.TYPE_PARAMETER;
        } else {
          type = Name.Type.AMBIGUOUS;
        }
      }
      if (name == null && type == Name.Type.PACKAGE) {
        name = Name.DEFAULT_PACKAGE;
      }
      name = name != null
          ? name.child(identifier, type)
          : Name.root(identifier, type);

    }
    if (name == null) { return; }

    J8TypeScope scope = typeScopes.get(typeScopes.size() - 1);
    TypeNameResolver resolver = scope.getTypeNameResolver();
    if (resolver == null) {
      error(
          node,
          "Cannot resolve name " + name
          + " due to missing scope");
      return;
    }
    Optional<TypeInfo> resolution = resolveName(node, name, resolver);
    if (resolution.isPresent()) {
      TypeInfo ti = resolution.get();
      if (node instanceof J8TypeReference) {
        ((J8TypeReference) node).setReferencedTypeInfo(ti);
      }
      int i = idents.size();
      Name nm = ti.canonName;
      for (; --i >= 0 && !Name.DEFAULT_PACKAGE.equals(nm); nm = nm.parent) {
        IdentifierNode id = idents.get(i);
        if (!id.getValue().equals(nm.identifier)) {
          break;
        }
        id.setNamePartType(nm.type);
      }
    }
  }

  private ProcessingStatus rewriteContextFreeNames(
      @Nullable SList<Parent> pathFromRoot,
      J8BaseNode parent, ContextFreeNamesNode names) {
    Decomposed decomposed = decompose(names);
    if (decomposed == null) {
      return ProcessingStatus.BREAK;  // Logged within.
    }
    Preconditions.checkState(!typeScopes.isEmpty());
    J8TypeScope scope = typeScopes.get(typeScopes.size() - 1);

    TypeNameResolver resolver = scope.getTypeNameResolver();
    if (resolver == null) {
      error(
          names,
          "Cannot resolve name " + decomposed.name
          + " due to missing scope");
      return ProcessingStatus.BREAK;
    }

    switch (parent.getNodeType()) {
      case ClassOrInterfaceType: {
        Preconditions.checkState(
            parent.getVariant() ==
            ClassOrInterfaceTypeNode.Variant.ContextFreeNames);

        if (pathFromRoot != null && pathFromRoot.x.parent
            instanceof ClassOrInterfaceTypeToInstantiateNode) {
          // Hoist any diamond out into the type to instantiate, since
          // vanilla ClassOrInterfaceTypes cannot have diamonds.
          ClassOrInterfaceTypeToInstantiateNode newTypeNode =
              (ClassOrInterfaceTypeToInstantiateNode) pathFromRoot.x.parent;
          int nIdents = decomposed.idents.size();
          IdentifierEtc lastIdent = decomposed.idents.get(nIdents - 1);
          J8BaseNode lastIdentArguments = lastIdent.getArguments();
          if (lastIdentArguments != null
              && lastIdentArguments.getVariant()
              == TypeArgumentsOrDiamondNode.Variant.Diamond) {
            if (newTypeNode.getChild(newTypeNode.getNChildren() - 1)
                .getNodeType() != J8NodeType.Diamond) {
              // No existing diamond.
              DiamondNode diamond = lastIdentArguments.firstChildWithType(
                  DiamondNode.class);
              newTypeNode.add(diamond);
              lastIdent.consumeArguments();
            }
          }
        }

        ClassOrInterfaceTypeNode b = (ClassOrInterfaceTypeNode) parent;
        if (pathFromRoot != null
            && pathFromRoot.prev != null
            && pathFromRoot.prev.prev != null
            && pathFromRoot.prev.prev.x.parent.getVariant()
               == PrimaryNode.Variant.InnerClassCreation) {
          // Special case (expression.new ClassOrInterfaceTypeToInstantiate)
          // because we need to type expression before we can resolve the
          // rest of the type.
          b.remove(0);
          // 15.9.1 says
          // """
          // * If the class instance creation expression is qualified:
          //
          //   The ClassOrInterfaceTypeToInstantiate must unambiguously denote
          //   an inner class that is accessible, non-abstract, not an enum
          //   type, and a member of the compile-time type of the Primary
          //   expression or the ExpressionName.
          // """
          // so we know all the names are class names.
          Name allClasses = null;
          for (IdentifierEtc ietc : decomposed.idents) {
            String ident = ietc.identifier.getValue();
            allClasses = allClasses == null
                ? Name.root(ident, Name.Type.CLASS)
                : allClasses.child(ident, Name.Type.CLASS);
          }
          buildClassOrInterfaceType(null, allClasses, decomposed, b);
        } else {
          // Decide whether it's a class type or an interface type.
          Optional<TypeInfo> resolution = resolveName(
              b, decomposed.name, resolver);
          if (resolution.isPresent()) {
            b.remove(0);
            TypeInfo typeInfo = resolution.get();
            buildClassOrInterfaceType(
                typeInfo, typeInfo.canonName, decomposed, b);
          }
        }
        break;
      }
      case Primary: {
        Preconditions.checkArgument(
            parent.getVariant() == PrimaryNode.Variant.Ambiguous);
        Optional<ImmutableList<Decomposed>> dsOpt = expressionNameOf(
            pathFromRoot, decomposed, names);
        if (!dsOpt.isPresent()) { break; }
        ImmutableList<Decomposed> ds = dsOpt.get();
        // The left hand side is the LR seed and the rest are fields.
        J8BaseNode seed = null;
        {
          Decomposed seedDecomp = ds.get(0);
          switch (seedDecomp.name.type) {
            case LOCAL: {
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              IdentifierNode ident = seedDecomp.idents.get(0).identifier;
              ident.setNamePartType(Name.Type.LOCAL);
              LocalNameNode localName =
                  LocalNameNode.Variant.Identifier.buildNode(ident);
              localName.setSourcePosition(ident.getSourcePosition());
              localName.setReferencedExpressionName(seedDecomp.name);
              seed = ExpressionAtomNode.Variant.Local.buildNode(localName);
              break;
            }
            case FIELD: {
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              FieldNameNode fieldName = FieldNameNode.Variant.Identifier
                  .buildNode(seedDecomp.idents.get(0).identifier);
              IdentifierNode ident = seedDecomp.idents.get(0).identifier;
              ident.setNamePartType(Name.Type.FIELD);
              fieldName.setSourcePosition(ident.getSourcePosition());
              fieldName.setReferencedExpressionName(seedDecomp.name);
              seed = ExpressionAtomNode.Variant.FreeField.buildNode(fieldName);
              break;
            }
            case CLASS:
            case TYPE_PARAMETER:
              // It's legit to use a type parameter to the left of a static
              // member access as in
              //   class C<T extends java.util.Locale> {
              //     { System.err.println(T.ENGLISH); }
              //   }

              seed = ExpressionAtomNode.Variant.StaticMember.buildNode(
                  buildTypeNameNode(seedDecomp, names, resolver));
              break;
            case AMBIGUOUS:
            case METHOD:
            case PACKAGE:
              // Cannot have members.
              throw new AssertionError(seedDecomp.name);
          }
        }
        Preconditions.checkNotNull(seed);
        int nParts = ds.size();

        // Primary.ExpressionStatement is @anon.
        J8BaseNode expr = seed;

        for (Decomposed d : ds.subList(1, nParts)) {
          Preconditions.checkState(d.idents.size() == 1);
          expr = PrimaryNode.Variant.FieldAccess.buildNode(
              expr,
              FieldNameNode.Variant.Identifier.buildNode(
                  d.idents.get(0).identifier
                  .setNamePartType(Name.Type.FIELD)));
        }

        expr.setSourcePosition(parent.getSourcePosition());
        return ProcessingStatus.replace(expr);
      }
      case TypeName: {
        Decomposed d = decompose(names);
        if (d != null) {
          TypeNameNode typeName = buildTypeNameNode(d, parent, resolver);
          return ProcessingStatus.replace(typeName);
        }
        break;
      }
      case PackageOrTypeName: {
        Decomposed d = decompose(names);
        if (d != null) {
          return ProcessingStatus.replace(buildPackageOrTypeNameNode(d));
        }
        break;
      }
      default:
        System.err.println("Unhandled CFN parent " + parent);
        break;
    }
    return ProcessingStatus.BREAK;
  }

  private Optional<TypeInfo> resolveName(
      Positioned p, Name name, TypeNameResolver resolver) {
    ImmutableList<Name> canonNames =
        resolver.lookupTypeName(name);
    switch (canonNames.size()) {
      case 1:
        Name canonName = canonNames.get(0);
        Optional<TypeInfo> typeInfoOpt =
            typeInfoResolver.resolve(canonName);
        if (typeInfoOpt.isPresent()) {
          return typeInfoOpt;
        } else {
          error(p, "Unrecognized type " + canonName);
        }
        break;
      case 0:
        error(p, "Cannot resolve name " + name);
        break;
      default:
        error(p, "Ambiguous name " + name + " : " + canonNames);
    }
    return Optional.absent();
  }

  private static Optional<Name> expressionNameOf(
      @Nullable SList<Parent> pathFromRoot, String ident) {
    DeclarationPositionMarker marker = DeclarationPositionMarker.LATEST;
    for (SList<Parent> anc = pathFromRoot; anc != null; anc = anc.prev) {
      if (anc.x.parent instanceof J8ExpressionNameScope) {
        ExpressionNameResolver r = ((J8ExpressionNameScope) anc.x.parent)
            .getExpressionNameResolver();
        if (r == null) { continue; }
        Optional<Name> canonNameOpt = r.resolveReference(ident, marker);
        if (canonNameOpt.isPresent()) {
          return canonNameOpt;
        }
        marker = DeclarationPositionMarker.LATEST;  // Passing out of scope.
      } else if (anc.x.parent instanceof J8LimitedScopeElement) {
        marker = ((J8LimitedScopeElement) anc.x.parent)
            .getDeclarationPositionMarker();
      }
    }
    return Optional.absent();
  }

  @SuppressWarnings("synthetic-access")
  private Optional<ImmutableList<Decomposed>> expressionNameOf(
      @Nullable SList<Parent> pathFromRoot, Decomposed d,
      ContextFreeNamesNode names) {
    String ident0 = d.idents.get(0).identifier.getValue();

    // If the zero-th element matches in an expression name resolver, then
    // mark it up.
    Optional<Name> canonNameOpt = expressionNameOf(pathFromRoot, ident0);
    if (canonNameOpt.isPresent()) {
      Name canonName = canonNameOpt.get();
      ImmutableList.Builder<Decomposed> ds = ImmutableList.builder();
      IdentifierEtc ietc0 = d.idents.get(0);
      ds.add(new Decomposed(
          d.sourceNode, ImmutableList.of(ietc0),
          canonName));

      // The rest are all fields which will remain ambiguous until we have type
      // checked the rest.
      for (int i = 1; i < d.idents.size(); ++i) {
        IdentifierEtc ietc = d.idents.get(i);
        ds.add(new Decomposed(
            d.sourceNode, ImmutableList.of(ietc),
            Name.root(ietc.identifier.getValue(), Name.Type.FIELD)));
      }
      return Optional.of(ds.build());
    }

    // Else, try and match a maximal strict prefix of the identifiers as a type
    // name.
    TypeNameResolver canonResolver = null;
    for (SList<Parent> anc = pathFromRoot; anc != null; anc = anc.prev) {
      if (anc.x.parent instanceof J8TypeScope) {
        J8TypeScope ts = (J8TypeScope) anc.x.parent;
        canonResolver = ts.getTypeNameResolver();
        break;
      }
    }
    Preconditions.checkNotNull(canonResolver);
    SList<Name> stripped = null;
    int nStripped = 0;
    for (Name nm = d.name; nm != null && nm.type != Name.Type.PACKAGE;
        stripped = SList.append(stripped, nm), nm = nm.parent, ++nStripped) {
      ImmutableList<Name> canonNames = canonResolver.lookupTypeName(nm);
      canonNames = filterOutAnonymousNames(canonNames);
      if (!canonNames.isEmpty()) {
        if (canonNames.size() > 1) {
          error(
              names,
              "Ambiguous type for expression name " + d.name.toDottedString()
              + " : " + canonNames);
        }
        Name canonName = canonNames.get(0);
        ImmutableList.Builder<Decomposed> ds = ImmutableList.builder();
        ds.add(new Decomposed(
            d.sourceNode,
            d.idents.subList(0, d.idents.size() - nStripped),
            canonName));

        for (; stripped != null; stripped = stripped.prev, --nStripped) {
          Preconditions.checkState(stripped.x.variant == 0);
          IdentifierEtc ietc = d.idents.get(d.idents.size() - nStripped);
          ds.add(new Decomposed(
              d.sourceNode, ImmutableList.of(ietc),
              Name.root(stripped.x.identifier, Name.Type.FIELD)
             ));
        }
        return Optional.of(ds.build());
      }
    }

    // Else warn.
    error(names, "Cannot resolve ambiguous name " + d.name);
    return Optional.absent();
  }

  private ImmutableList<Name> filterOutAnonymousNames(
      ImmutableList<Name> canonNames) {
    ImmutableList.Builder<Name> b = ImmutableList.builder();
    for (Name canonName : canonNames) {
      Optional<TypeInfo> typeInfoOpt = this.typeInfoResolver.resolve(canonName);
      if (typeInfoOpt.isPresent()) {
        TypeInfo typeInfo = typeInfoOpt.get();
        if (!typeInfo.isAnonymous) {
          b.add(canonName);
          // TODO: do we need to check that outer types are not anonymous.
        }
      }
    }
    return b.build();
  }

  private @Nullable Decomposed decompose(ContextFreeNamesNode node) {
    ImmutableList.Builder<IdentifierEtc> parts = ImmutableList.builder();

    Name name = null;
    for (J8BaseNode child : node.getChildren()) {
      if (!(child instanceof ContextFreeNameNode)) {
        // Fail gracefully on an unevaluated template.
        error(child, "Cannot disambiguate " + child);
        return null;
      }
      IdentifierEtc ietc = identifierEtc((ContextFreeNameNode) child);
      if (ietc == null) {
        error(child, "Cannot find identifier");
        return null;
      }
      parts.add(ietc);
      String ident = ietc.identifier.getValue();
      Name.Type type = ietc.identifier.getNamePartType();
      if (type == null) {
        type = Name.Type.AMBIGUOUS;
      }
      if (name == null) {
        if (type == Name.Type.PACKAGE) {
          name = Name.DEFAULT_PACKAGE.child(ident, type);
        } else {
          name = Name.root(ident, type);
        }
      } else {
        name = name.child(ident, type);
      }
    }

    @SuppressWarnings("synthetic-access")
    Decomposed d = new Decomposed(node, parts.build(), name);
    return d;
  }


  private static final class Decomposed {
    final ContextFreeNamesNode sourceNode;
    final ImmutableList<IdentifierEtc> idents;
    final Name name;

    private Decomposed(
        ContextFreeNamesNode sourceNode,
        ImmutableList<IdentifierEtc> idents,
        Name name) {
      this.sourceNode = sourceNode;
      this.idents = idents;
      this.name = name;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      for (int i = 0, n = idents.size(); i < n; ++i) {
        if (i != 0) { sb.append(" . "); }
        IdentifierEtc ietc = idents.get(i);
        sb.append(ietc.identifier.getValue());
      }
      sb.append(':').append(name.type);
      return sb.toString();
    }
  }


  private static @Nullable
  IdentifierEtc identifierEtc(ContextFreeNameNode node) {
    ImmutableList.Builder<AnnotationNode> annotations =
        ImmutableList.builder();
    IdentifierNode identifier;
    J8BaseNode arguments;

    List<J8BaseNode> children = node.getChildren();
    int nChildren = children.size();
    if (nChildren == 0) {
      return null;
    }
    int index = nChildren - 1;
    J8BaseNode child = children.get(index);
    if (child instanceof TypeArgumentsNode
        || child instanceof TypeArgumentsOrDiamondNode) {
      arguments = child;
      --index;
    } else {
      arguments = null;
    }
    if (index < 0) {
      return null;
    }
    child = children.get(index);
    if (!(child instanceof IdentifierNode)) {
      return null;
    }
    identifier = (IdentifierNode) child;

    for (int i = 0; i < index; ++i) {
      child = children.get(i);
      if (child instanceof AnnotationNode) {
        annotations.add((AnnotationNode) child);
      } else {
        return null;
      }
    }

    @SuppressWarnings("synthetic-access")
    IdentifierEtc ietc = new IdentifierEtc(
        node, annotations.build(), identifier, arguments);
    return ietc;
  }


  private static final class IdentifierEtc {
    final ContextFreeNameNode node;

    final ImmutableList<AnnotationNode> annotations;

    final IdentifierNode identifier;

    /** TypeArguments or TypeArgumentsOrDiamond */
    private @Nullable J8BaseNode arguments;

    private IdentifierEtc(
        ContextFreeNameNode node,
        ImmutableList<AnnotationNode> annotations,
        IdentifierNode identifier,
        @Nullable J8BaseNode arguments) {
      this.node = node;
      this.annotations = annotations;
      this.identifier = identifier;
      this.arguments = arguments;
    }

    void consumeArguments() {
      this.arguments = null;
    }

    J8BaseNode getArguments() {
      return arguments;
    }

  }

  private void buildClassOrInterfaceType(
      @Nullable TypeInfo typeInfo, Name name, Decomposed d,
      ClassOrInterfaceTypeNode ctype) {
    ctype.setVariant(
        ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments);
    buildClassOrInterfaceType(name, d, d.idents.size() - 1, ctype);
    if (typeInfo != null) {
      ctype.setReferencedTypeInfo(typeInfo);
    }
    SourcePosition p = d.sourceNode.getSourcePosition();
    if (p != null) {
      ctype.setSourcePosition(p);
    }
  }

  private void buildClassOrInterfaceType(
      Name name, Decomposed d, int identIndex,
      ClassOrInterfaceTypeNode ctype) {
    IdentifierEtc ietc = identIndex >= 0 ? d.idents.get(identIndex) : null;
    boolean hasParent = name.parent != null
        && !Name.DEFAULT_PACKAGE.equals(name.parent)
        && (identIndex > 0 || useLongNames);

    if (hasParent) {
      ClassOrInterfaceTypeNode subctype =
          ClassOrInterfaceTypeNode.Variant
          .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments
          .buildNode();
      buildClassOrInterfaceType(name.parent, d, identIndex - 1, subctype);
      ctype.add(subctype);
    }
    if (ietc != null) {
      ctype.setSourcePosition(ietc.node.getSourcePosition());
    }

    if (ietc != null) {
      for (AnnotationNode annotation : ietc.annotations) {
        ctype.add(annotation);
      }
    }

    IdentifierNode newIdent = ietc != null
        ? ietc.identifier.shallowClone()
        : IdentifierNode.Variant.Builtin.buildNode(name.identifier);
    newIdent.setNamePartType(name.type)
        .setValue(name.identifier);
    ctype.add(newIdent);

    if (ietc != null) {
      J8BaseNode arguments = ietc.getArguments();
      if (arguments != null) {
        if (arguments instanceof TypeArgumentsNode) {
          ctype.add(arguments);
        } else {
          Preconditions.checkState(
              arguments instanceof TypeArgumentsOrDiamondNode);
          TypeArgumentsOrDiamondNode argsOrDiamond =
              (TypeArgumentsOrDiamondNode) arguments;
          switch (argsOrDiamond.getVariant()) {
            case Diamond:
              error(arguments, "Misplaced type arguments diamond (<>)");
              break;
            case TypeArguments:
              TypeArgumentsNode nestedArguments =
                  argsOrDiamond.firstChildWithType(TypeArgumentsNode.class);
              ctype.add(nestedArguments);
              break;
          }
        }
      }
    }
  }

  private TypeNameNode buildTypeNameNode(
      Decomposed d, J8BaseNode orig, TypeNameResolver resolver) {
    ImmutableList<IdentifierEtc> idents = d.idents;
    if (useLongNames) {
      ImmutableList.Builder<IdentifierEtc> b = ImmutableList.builder();
      int index = idents.size() - 1;
      for (Name n = d.name; n != null; n = n.parent) {
        if (Name.DEFAULT_PACKAGE.equals(n)) {
          break;
        }
        if (index >= 0) {
          IdentifierEtc ietc = idents.get(index);
          if (ietc.identifier.getValue().equals(n.identifier)) {
            b.add(ietc);
            --index;
            continue;
          }
          index = -1;
        }
        @SuppressWarnings("synthetic-access")
        IdentifierEtc ietc = new IdentifierEtc(
            null, ImmutableList.of(),
            IdentifierNode.Variant.Builtin.buildNode(n.identifier)
                .setNamePartType(n.type),
            null);
        b.add(ietc);
      }
      idents = b.build().reverse();
    }

    int nIdents = idents.size();
    TypeNameNode newTypeNameNode;
    IdentifierNode ident = idents.get(nIdents - 1).identifier;
    ident.setNamePartType(d.name.type);
    if (nIdents == 1) {
      newTypeNameNode = TypeNameNode.Variant.Identifier.buildNode(ident);
    } else {
      PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
          idents, 0, nIdents - 1);
      newTypeNameNode = TypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(child, ident);
    }
    newTypeNameNode.copyMetadataFrom(orig);
    ImmutableList<Name> canonNames = resolver.lookupTypeName(d.name);
    switch (canonNames.size()) {
      default:
        error(orig, "Ambiguous name " + d.name + ": " + canonNames);
        //$FALL-THROUGH$
      case 1:
        Name canonName = canonNames.get(0);
        if (canonName.type != Name.Type.AMBIGUOUS) {
          Optional<TypeInfo> tiOpt = typeInfoResolver.resolve(canonName);
          newTypeNameNode.setReferencedTypeInfo(tiOpt.orNull());
        }
        break;
      case 0:
        error(orig, "Unrecognized type " + d.name);
        break;
    }
    newTypeNameNode.setSourcePosition(d.sourceNode.getSourcePosition());
    return newTypeNameNode;
  }

  private PackageOrTypeNameNode buildPackageOrTypeNameNode(Decomposed d) {
    ImmutableList<IdentifierEtc> idents = d.idents;
    if (useLongNames) {
      ImmutableList.Builder<IdentifierEtc> b = ImmutableList.builder();
      int index = idents.size() - 1;
      for (Name n = d.name; n != null; n = n.parent) {
        if (Name.DEFAULT_PACKAGE.equals(n)) {
          break;
        }
        if (index >= 0) {
          IdentifierEtc ietc = idents.get(index);
          if (ietc.identifier.getValue().equals(n.identifier)) {
            b.add(ietc);
            --index;
            continue;
          }
          index = -1;
        }
        @SuppressWarnings("synthetic-access")
        IdentifierEtc ietc = new IdentifierEtc(
            null, ImmutableList.of(),
            IdentifierNode.Variant.Builtin.buildNode(n.identifier)
                .setNamePartType(n.type),
            null);
        b.add(ietc);
      }
      idents = b.build().reverse();
    }

    int nIdents = idents.size();
    PackageOrTypeNameNode newPackageOrTypeName;
    if (nIdents == 1) {
      newPackageOrTypeName = PackageOrTypeNameNode.Variant.Identifier.buildNode(
          idents.get(0).identifier);
    } else {
      PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
          idents, 0, nIdents - 1);
      newPackageOrTypeName =
          PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(child, idents.get(nIdents - 1).identifier);
    }
    newPackageOrTypeName.setSourcePosition(d.sourceNode.getSourcePosition());
    return newPackageOrTypeName;
  }

  private PackageOrTypeNameNode buildPackageOrTypeNameNode(
      ImmutableList<IdentifierEtc> idents, int left, int right) {
    Preconditions.checkArgument(left < right);
    if (left + 1 == right) {
      return PackageOrTypeNameNode.Variant.Identifier.buildNode(
          idents.get(left).identifier);
    }
    PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
        idents, left, right - 1);
    return PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
        .buildNode(child, idents.get(right - 1).identifier);
  }
}
