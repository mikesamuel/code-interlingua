package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.AnnotationNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.FieldNameNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.LocalNameNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.TypeNameNode;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver;
import com.mikesamuel.cil.ast.meta.ExpressionNameResolver.DeclarationPositionMarker;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.traits.ExpressionNameScope;
import com.mikesamuel.cil.ast.traits.LimitedScopeElement;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A pass that looks at {@link NodeType#ContextFreeNames} and converts them to
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
 * <ol>
 */
final class DisambiguationPass extends AbstractRewritingPass {

  private final List<TypeScope> typeScopes = Lists.newArrayList();
  private final List<ExpressionNameScope> nameScopes = Lists.newArrayList();
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
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (node instanceof TypeScope) {
      typeScopes.add((TypeScope) node);
    }
    if (node instanceof ExpressionNameScope) {
      ExpressionNameScope nameScope = (ExpressionNameScope) node;

//    ExpressionNameResolver r = nameScope.getExpressionNameResolver();
//    if (r == null && !nameScopes.isEmpty()) {
//      nameScope.setExpressionNameResolver(
//          nameScopes.get(nameScopes.size() - 1).getExpressionNameResolver());
//    }
      nameScopes.add(nameScope);
    }

    List<BaseNode> children = node.getChildren();
    if (children.size() == 1) {
      BaseNode child = children.get(0);
      if (child instanceof ContextFreeNamesNode) {
        return rewriteContextFreeNames(
            pathFromRoot, node, (ContextFreeNamesNode) child);
      }
    }
    // By inspection of the grammar, context free names
    // never have non-template siblings.
    Preconditions.checkState(!(node instanceof ContextFreeNamesNode));
    return ProcessingStatus.CONTINUE;
  }

  private ProcessingStatus rewriteContextFreeNames(
      @Nullable SList<Parent> pathFromRoot,
      BaseNode parent, ContextFreeNamesNode names) {
    Decomposed decomposed = Decomposed.of(names);
    if (decomposed == null) {
      return ProcessingStatus.BREAK;  // Logged within.
    }
    Preconditions.checkState(!typeScopes.isEmpty());
    TypeScope scope = typeScopes.get(typeScopes.size() - 1);

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
          ImmutableList<Name> canonNames =
              resolver.lookupTypeName(decomposed.name);
          switch (canonNames.size()) {
            case 1:
              Name canonName = canonNames.get(0);
              Optional<TypeInfo> typeInfoOpt =
                  typeInfoResolver.resolve(canonName);
              if (typeInfoOpt.isPresent()) {
                TypeInfo typeInfo = typeInfoOpt.get();
                b.remove(0);
                buildClassOrInterfaceType(
                    typeInfo, canonName, decomposed, b);
              } else {
                error(names, "Unrecognized type " + canonName);
              }
              break;
            case 0:
              error(names, "Cannot resolve name " + decomposed.name);
              break;
            default:
              error(
                  names, "Ambiguous name " + decomposed.name
                  + " : " + canonNames);
          }
        }
        break;
      }
      case Primary: {
        Preconditions.checkArgument(
            parent.getVariant() == PrimaryNode.Variant.Ambiguous);
        Optional<ImmutableList<Decomposed>> dsOpt = expressionNameOf(
            pathFromRoot, names);
        if (!dsOpt.isPresent()) { break; }
        ImmutableList<Decomposed> ds = dsOpt.get();
        // The left hand side is the LR seed and the rest are fields.
        BaseNode seed = null;
        {
          Decomposed seedDecomp = ds.get(0);
          switch (seedDecomp.name.type) {
            case LOCAL:
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              LocalNameNode localName =
                  LocalNameNode.Variant.Identifier.buildNode(
                      seedDecomp.idents.get(0).identifier);
              localName.setReferencedExpressionName(seedDecomp.name);
              seed = ExpressionAtomNode.Variant.Local.buildNode(localName);
              break;
            case FIELD:
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              FieldNameNode fieldName = FieldNameNode.Variant.Identifier
                  .buildNode(seedDecomp.idents.get(0).identifier);
              fieldName.setReferencedExpressionName(seedDecomp.name);
              seed = ExpressionAtomNode.Variant.FreeField.buildNode(fieldName);
              break;
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
        BaseNode expr = seed;

        for (Decomposed d : ds.subList(1, nParts)) {
          Preconditions.checkState(d.idents.size() == 1);
          expr = PrimaryNode.Variant.FieldAccess.buildNode(
              expr,
              FieldNameNode.Variant.Identifier.buildNode(
                  d.idents.get(0).identifier));
        }

        expr.setSourcePosition(parent.getSourcePosition());
        return ProcessingStatus.replace(expr);
      }
      case TypeName: {
        Decomposed d = Decomposed.of(names);
        if (d != null) {
          TypeNameNode typeName = buildTypeNameNode(d, parent, resolver);
          return ProcessingStatus.replace(typeName);
        }
        break;
      }
      case PackageOrTypeName: {
        Decomposed d = Decomposed.of(names);
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

  @Override
  protected ProcessingStatus postvisit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (node instanceof TypeScope) {
      TypeScope popped = typeScopes.remove(typeScopes.size() - 1);
      Preconditions.checkState(popped == node);
    }
    if (node instanceof ExpressionNameScope) {
      ExpressionNameScope popped = nameScopes.remove(nameScopes.size() - 1);
      Preconditions.checkState(popped == node);
    }

    // TODO: adjust variant if a child was a context free names node
    return ProcessingStatus.CONTINUE;
  }

  @SuppressWarnings("synthetic-access")
  private Optional<ImmutableList<Decomposed>> expressionNameOf(
      @Nullable SList<Parent> pathFromRoot,
      ContextFreeNamesNode names) {
    Decomposed d = Decomposed.of(names);
    String ident0 = d.idents.get(0).identifier.getValue();

    // If the zero-th element matches in an expression name resolver, then
    // mark it up.
    Optional<Name> canonNameOpt = Optional.absent();
    DeclarationPositionMarker marker = DeclarationPositionMarker.LATEST;
    for (SList<Parent> anc = pathFromRoot; anc != null; anc = anc.prev) {
      if (anc.x.parent instanceof ExpressionNameScope) {
        ExpressionNameResolver r = ((ExpressionNameScope) anc.x.parent)
            .getExpressionNameResolver();
        if (r == null) { continue; }
        canonNameOpt = r.resolveReference(ident0, marker);
        if (canonNameOpt.isPresent()) { break; }
        marker = DeclarationPositionMarker.LATEST;  // Passing out of scope.
      } else if (anc.x.parent instanceof LimitedScopeElement) {
        marker = ((LimitedScopeElement) anc.x.parent)
            .getDeclarationPositionMarker();
      }
    }
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
      if (anc.x.parent instanceof TypeScope) {
        TypeScope ts = (TypeScope) anc.x.parent;
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
      if (!canonNames.isEmpty()) {
        if (canonNames.size() > 1) {
          error(
              names, "Ambiguous type for expression name "
                  + d.name.toDottedString() + " : " + canonNames);
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

    static @Nullable Decomposed of(ContextFreeNamesNode node) {
      ImmutableList.Builder<IdentifierEtc> parts = ImmutableList.builder();

      Name name = null;
      for (BaseNode child : node.getChildren()) {
        if (!(child instanceof ContextFreeNameNode)) {
          // Fail gracefully on an unevaluated template.
          return null;
        }
        IdentifierEtc ietc = IdentifierEtc.of((ContextFreeNameNode) child);
        if (ietc == null) {
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

      return new Decomposed(node, parts.build(), name);
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

  private static final class IdentifierEtc {
    final ContextFreeNameNode node;

    final ImmutableList<AnnotationNode> annotations;

    final IdentifierNode identifier;

    final @Nullable TypeArgumentsNode arguments;

    private IdentifierEtc(
        ContextFreeNameNode node,
        ImmutableList<AnnotationNode> annotations,
        IdentifierNode identifier,
        @Nullable TypeArgumentsNode arguments) {
      this.node = node;
      this.annotations = annotations;
      this.identifier = identifier;
      this.arguments = arguments;
    }

    static @Nullable IdentifierEtc of(ContextFreeNameNode node) {
      ImmutableList.Builder<AnnotationNode> annotations =
          ImmutableList.builder();
      IdentifierNode identifier;
      TypeArgumentsNode arguments;

      List<BaseNode> children = node.getChildren();
      int nChildren = children.size();
      if (nChildren == 0) { return null; }
      int index = nChildren - 1;
      BaseNode child = children.get(index);
      if (child instanceof TypeArgumentsNode) {
        arguments = (TypeArgumentsNode) child;
        --index;
      } else {
        arguments = null;
      }
      if (index < 0) { return null; }
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

      return new IdentifierEtc(
          node, annotations.build(), identifier, arguments);
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
      if (ietc.arguments != null) {
        ctype.add(ietc.arguments);
      }
    }
  }

  private TypeNameNode buildTypeNameNode(
      Decomposed d, BaseNode orig, TypeNameResolver resolver) {
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
    if (nIdents == 1) {
      newTypeNameNode = TypeNameNode.Variant.Identifier.buildNode(
          idents.get(0).identifier);
    } else {
      PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
          idents, 0, nIdents - 1);
      newTypeNameNode = TypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .buildNode(child, idents.get(nIdents - 1).identifier);
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
        error(orig, "Unrecognized name " + d.name);
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
