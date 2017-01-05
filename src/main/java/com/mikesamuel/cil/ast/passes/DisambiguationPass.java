package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mikesamuel.cil.ast.AnnotationNode;
import com.mikesamuel.cil.ast.BaseExpressionNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.ExpressionAtomNode;
import com.mikesamuel.cil.ast.FieldNameNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.LocalNameNode;
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
 * between enum field references and constant references in switch cases yet.
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
  protected <N extends BaseNode> ProcessingStatus previsit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
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

    ImmutableList<BaseNode> children = node.getChildren();
    if (children.size() == 1) {
      BaseNode child = children.get(0);
      if (child instanceof ContextFreeNamesNode) {
        return rewriteContextFreeNames(
            pathFromRoot, node, (ContextFreeNamesNode) child, builder);
      }
    }
    // By inspection of the grammar, context free names
    // never have non-template siblings.
    Preconditions.checkState(!(node instanceof ContextFreeNamesNode));
    return ProcessingStatus.CONTINUE;
  }

  private ProcessingStatus rewriteContextFreeNames(
      @Nullable SList<Parent> pathFromRoot,
      BaseNode parent, ContextFreeNamesNode names,
      BaseNode.Builder<?, ?> parentBuilder) {
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
              ClassOrInterfaceTypeNode.Builder b =
                  (ClassOrInterfaceTypeNode.Builder) parentBuilder;
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
        BaseExpressionNode seed = null;
        {
          Decomposed seedDecomp = ds.get(0);
          switch (seedDecomp.name.type) {
            case LOCAL:
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              seed = ExpressionAtomNode.Variant.Local.nodeBuilder()
                  .add(
                      LocalNameNode.Variant.Identifier.nodeBuilder()
                      .setReferencedExpressionName(seedDecomp.name)
                      .add(seedDecomp.idents.get(0).identifier)
                      .build())
                  .build();
              break;
            case FIELD:
              Preconditions.checkState(seedDecomp.idents.size() == 1);
              seed = ExpressionAtomNode.Variant.FreeField.nodeBuilder()
                  .add(
                      FieldNameNode.Variant.Identifier.nodeBuilder()
                      .setReferencedExpressionName(seedDecomp.name)
                      .add(seedDecomp.idents.get(0).identifier)
                      .build())
                  .build();
              break;
            case CLASS:
            case TYPE_PARAMETER:
              // It's legit to use a type parameter to the left of a static
              // member access as in
              //   class C<T extends java.util.Locale> {
              //     { System.err.println(T.ENGLISH); }
              //   }

              seed = ExpressionAtomNode.Variant.StaticMember.nodeBuilder()
                  .add(buildTypeNameNode(seedDecomp))
                  .build();
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
        BaseExpressionNode expr = seed;

        for (Decomposed d : ds.subList(1, nParts)) {
          Preconditions.checkState(d.idents.size() == 1);
          expr = PrimaryNode.Variant.FieldAccess.nodeBuilder()
              .add(expr)
              .add(FieldNameNode.Variant.Identifier.nodeBuilder()
                  .add(d.idents.get(0).identifier)
                  .build())
              .build();
        }

        expr.setSourcePosition(parent.getSourcePosition());
        return ProcessingStatus.replace(expr);
      }
      case TypeName: {
        Decomposed d = Decomposed.of(names);
        if (d != null) {
          return ProcessingStatus.replace(buildTypeNameNode(d));
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
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
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
    // TODO
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

      ImmutableList<BaseNode> children = node.getChildren();
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
      TypeInfo typeInfo, Name name, Decomposed d,
      ClassOrInterfaceTypeNode.Builder ctype) {
    buildClassOrInterfaceType(name, d, d.idents.size() - 1, ctype);
    ctype.variant(
        ClassOrInterfaceTypeNode.Variant
        .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments);
    ctype.setReferencedTypeInfo(typeInfo);
    SourcePosition p = d.sourceNode.getSourcePosition();
    if (p != null) {
      ctype.setSourcePosition(p);
    }
  }

  private void buildClassOrInterfaceType(
      Name name, Decomposed d, int identIndex,
      ClassOrInterfaceTypeNode.Builder ctype) {
    IdentifierEtc ietc = identIndex >= 0 ? d.idents.get(identIndex) : null;
    boolean hasParent = name.parent != null
        && !Name.DEFAULT_PACKAGE.equals(name.parent)
        && (identIndex > 0 || useLongNames);

    if (hasParent) {
      ClassOrInterfaceTypeNode.Builder subctype =
          ClassOrInterfaceTypeNode.Variant
          .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments
          .nodeBuilder();
      buildClassOrInterfaceType(name.parent, d, identIndex - 1, subctype);
      ctype.add(subctype.build());
    }
    if (ietc != null) {
      ctype.setSourcePosition(ietc.node.getSourcePosition());
    }

    if (ietc != null) {
      for (AnnotationNode annotation : ietc.annotations) {
        ctype.add(annotation);
      }
    }

    IdentifierNode.Builder identBuilder = ietc != null
        ? ietc.identifier.builder()
        : IdentifierNode.Variant.Builtin.nodeBuilder();
    identBuilder.leaf(name.identifier);
    identBuilder.setNamePartType(name.type);
    ctype.add(identBuilder.build());

    if (ietc != null) {
      if (ietc.arguments != null) {
        ctype.add(ietc.arguments);
      }
    }
  }

  private TypeNameNode buildTypeNameNode(Decomposed d) {
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
            IdentifierNode.Variant.Builtin.nodeBuilder()
                .leaf(n.identifier)
                .setNamePartType(n.type)
                .build(),
            null);
        b.add(ietc);
      }
      idents = b.build().reverse();
    }

    int nIdents = idents.size();
    TypeNameNode.Builder b;
    if (nIdents == 1) {
      b = TypeNameNode.Variant.Identifier.nodeBuilder()
          .add(idents.get(0).identifier);
    } else {
      PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
          idents, 0, nIdents - 1);
      b = TypeNameNode.Variant.PackageOrTypeNameDotIdentifier.nodeBuilder()
          .add(child)
          .add(idents.get(nIdents - 1).identifier);
    }
    if (d.name.type != Name.Type.AMBIGUOUS) {
      Optional<TypeInfo> tiOpt = typeInfoResolver.resolve(d.name);
      b.setReferencedTypeInfo(tiOpt.orNull());
    }
    b.setSourcePosition(d.sourceNode.getSourcePosition());
    return b.build();
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
            IdentifierNode.Variant.Builtin.nodeBuilder()
                .leaf(n.identifier)
                .setNamePartType(n.type)
                .build(),
            null);
        b.add(ietc);
      }
      idents = b.build().reverse();
    }

    int nIdents = idents.size();
    PackageOrTypeNameNode.Builder b;
    if (nIdents == 1) {
      b = PackageOrTypeNameNode.Variant.Identifier.nodeBuilder()
          .add(idents.get(0).identifier);
    } else {
      PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
          idents, 0, nIdents - 1);
      b = PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
          .nodeBuilder()
          .add(child)
          .add(idents.get(nIdents - 1).identifier);
    }
    b.setSourcePosition(d.sourceNode.getSourcePosition());
    return b.build();
  }

  private PackageOrTypeNameNode buildPackageOrTypeNameNode(
      ImmutableList<IdentifierEtc> idents, int left, int right) {
    Preconditions.checkArgument(left < right);
    if (left + 1 == right) {
      return PackageOrTypeNameNode.Variant.Identifier.nodeBuilder()
          .add(idents.get(left).identifier)
          .build();
    }
    PackageOrTypeNameNode child = buildPackageOrTypeNameNode(
        idents, left, right - 1);
    return PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier
        .nodeBuilder()
        .add(child)
        .add(idents.get(right - 1).identifier)
        .build();
  }
}
