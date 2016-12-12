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
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.meta.TypeNameResolver;
import com.mikesamuel.cil.ast.traits.TypeScope;
import com.mikesamuel.cil.parser.SList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * A pass that looks at {@link NodeType#ContextFreeNames} and converts them to
 * type, field, and method references as appropriate and sets
 * {@linkplain TypeReference#setReferencedTypeInfo TypeReference meta-data}.
 * <p>
 * This happens before computing types for expressions which means we don't
 * attach field descriptors to field names yet.  We also don't distinguish yet
 * between enum field references in switch cases yet.
 */
final class DisambiguationPass extends AbstractRewritingPass {

  private final List<TypeScope> scopes = Lists.newArrayList();
  private final TypeInfoResolver typeInfoResolver;
  private final Logger logger;
  private final boolean useLongNames;

  DisambiguationPass(
      TypeInfoResolver typeInfoResolver, Logger logger,
      boolean useLongNames) {
    this.typeInfoResolver = typeInfoResolver;
    this.logger = logger;
    this.useLongNames = useLongNames;
  }

  protected void error(@Nullable BaseNode node, String message) {
    SourcePosition pos = node != null ? node.getSourcePosition() : null;
    String fullMessage = pos != null ? pos + ": " + message : message;
    logger.severe(fullMessage);
  }

  @Override
  protected <N extends BaseNode> ProcessingStatus previsit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    if (node instanceof TypeScope) {
      scopes.add((TypeScope) node);
    }

    ImmutableList<BaseNode> children = node.getChildren();
    if (children.size() == 1) {
      BaseNode child = children.get(0);
      if (child instanceof ContextFreeNamesNode) {
        rewriteContextFreeNames(node, (ContextFreeNamesNode) child, builder);
        return ProcessingStatus.BREAK;
      }
    }
    // By inspection of the grammar, context free names
    // never have non-template siblings.
    Preconditions.checkState(!(node instanceof ContextFreeNamesNode));
    return ProcessingStatus.CONTINUE;
  }

  private void rewriteContextFreeNames(
      BaseNode parent, ContextFreeNamesNode names,
      BaseNode.Builder<?, ?> parentBuilder) {
    Decomposed decomposed = Decomposed.of(names);
    if (decomposed != null) {
      Preconditions.checkState(!scopes.isEmpty());
      TypeScope scope = scopes.get(scopes.size() - 1);
      TypeNameResolver resolver = scope.getTypeNameResolver();
      if (resolver == null) {
        error(
            names,
            "Cannot resolve name " + decomposed.name
            + " due to missing scope");
      } else {
        switch (parent.getNodeType()) {
          case ClassOrInterfaceType:
            Preconditions.checkState(
                parent.getVariant() ==
                ClassOrInterfaceTypeNode.Variant.ContextFreeNames);
            // Decide whether it's a class type or an interface type.
            ImmutableList<Name> canonNames = ImmutableList.copyOf(
                resolver.lookupTypeName(decomposed.name));
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
          case Primary:
            Preconditions.checkArgument(
                parent.getVariant() == PrimaryNode.Variant.Ambiguous);

            break;
          default:
            System.err.println("Unhandled CFN parent " + parent);
        }
      }
    }
  }

  @Override
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    if (node instanceof TypeScope) {
      TypeScope popped = scopes.remove(scopes.size() - 1);
      Preconditions.checkState(popped == node);
    }
    // TODO: adjust variant if a child was a context free names node
    return ProcessingStatus.CONTINUE;
  }


  private static final class Decomposed {
    final ContextFreeNamesNode node;
    final ImmutableList<IdentifierEtc> idents;
    final Name name;

    private Decomposed(
        ContextFreeNamesNode node,
        ImmutableList<IdentifierEtc> idents,
        Name name) {
      this.node = node;
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
        if (name == null) {
          if (ietc.type == Name.Type.PACKAGE) {
            name = Name.DEFAULT_PACKAGE.child(ident, ietc.type);
          } else {
            name = Name.root(ident, ietc.type);
          }
        } else {
          name = name.child(ident, ietc.type);
        }
      }

      return new Decomposed(node, parts.build(), name);
    }
  }

  private static final class IdentifierEtc {
    final ContextFreeNameNode node;

    final ImmutableList<AnnotationNode> annotations;

    final IdentifierNode identifier;
    final Name.Type type;

    final @Nullable TypeArgumentsNode arguments;

    private IdentifierEtc(
        ContextFreeNameNode node,
        ImmutableList<AnnotationNode> annotations,
        IdentifierNode identifier,
        Name.Type type,
        @Nullable TypeArgumentsNode arguments) {
      this.node = node;
      this.annotations = annotations;
      this.identifier = identifier;
      this.type = type;
      this.arguments = arguments;
    }

    static @Nullable IdentifierEtc of(ContextFreeNameNode node) {
      ImmutableList.Builder<AnnotationNode> annotations =
          ImmutableList.builder();
      IdentifierNode identifier;
      Name.Type type;
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
      type = identifier.getNamePartType();
      if (type == null) { type = Name.Type.AMBIGUOUS; }

      for (int i = 0; i < index; ++i) {
        child = children.get(i);
        if (child instanceof AnnotationNode) {
          annotations.add((AnnotationNode) child);
        } else {
          return null;
        }
      }

      return new IdentifierEtc(
          node, annotations.build(), identifier, type, arguments);
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
    SourcePosition p = d.node.getSourcePosition();
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

}
