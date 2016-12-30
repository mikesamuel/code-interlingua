package com.mikesamuel.cil.ast.passes;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.AnnotationNode;
import com.mikesamuel.cil.ast.ArrayTypeNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.BaseNode.InnerBuilder;
import com.mikesamuel.cil.ast.DimNode;
import com.mikesamuel.cil.ast.DimsNode;
import com.mikesamuel.cil.ast.NodeOrBuilder;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.TypeNode;
import com.mikesamuel.cil.parser.SList;

/**
 * Java allows for array dimensions to be specified after a declaration name
 * so this pass collects all the pieces of a return type, or declaration type
 * and makes sure that they are under a common subtree.
 * <p>
 * {@code int x[]} is a vestige from Java's C heritage and can be rewritten as
 * {@code int[] x}.
 * <p>
 * When multiple declarations are grouped, this can require splitting
 */
final class DefragmentTypesPass extends AbstractRewritingPass {
  static int countDims(@Nullable DimsNode dims) {
    int n = 0;
    if (dims != null) {
      for (BaseNode child : dims.getChildren()) {
        if (child.getNodeType() == NodeType.Dim) {
          ++n;
        }
      }
    }
    return n;
  }

  @Override
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    if (COMMON_ANCESTOR.contains(builder.getNodeType())) {
      DimsNode dims = getDims(builder);
//      System.err.println("For " + builder.getNodeType() + ", dims=" + dims);
      if (dims != null) {
        BaseNode type = getType(builder);
//        System.err.println("For " + builder.getNodeType() + ", type=" + type);
        if (type != null) {
          BaseNode replacement = removeDimsAndAddDimsToType(
              builder, dims, type);
          return ProcessingStatus.replace(replacement);
        }
      }
    }
    return ProcessingStatus.CONTINUE;
  }

  private static final ImmutableSet<NodeType> BETWEEN_COMMON_ANCESTOR_AND_DIMS =
      Sets.immutableEnumSet(
          NodeType.VariableDeclaratorId,
          NodeType.VariableDeclaratorList,
          NodeType.AnnotationTypeElementDeclaration,
          NodeType.MethodHeader,
          NodeType.MethodDeclarator);

  private static final ImmutableSet<NodeType> BETWEEN_COMMON_ANCESTOR_AND_TYPE =
      Sets.immutableEnumSet(
          NodeType.MethodHeader,
          NodeType.Result,
          NodeType.UnannType);

  private static final ImmutableSet<NodeType> COMMON_ANCESTOR =
      Sets.immutableEnumSet(
          NodeType.AnnotationTypeElementDeclaration,
          NodeType.CatchFormalParameter,
          NodeType.EnhancedForStatement,
          NodeType.FormalParameter,
          NodeType.LastFormalParameter,
          NodeType.MethodHeader,
          NodeType.Resource
          );

  private static final ImmutableSet<NodeType> TYPE_NODE_TYPES =
      Sets.immutableEnumSet(NodeType.Type, NodeType.ReferenceType);

  private static final ImmutableSet<NodeType> DIMS_NODE_TYPES =
      Sets.immutableEnumSet(NodeType.Dims);

  static <T> T processAlongPath(
      NodeOrBuilder node, ImmutableSet<NodeType> between,
      ImmutableSet<NodeType> target, FindOp<T> op) {
//    System.err.println("Looking for " + target + " in " + node.getNodeType());
    ImmutableList<BaseNode> children = node.getChildren();
    for (int i = 0, n = children.size(); i < n; ++i) {
      BaseNode child = children.get(i);
      NodeType nt = child.getNodeType();
      if (target.contains(nt)) {
        T result = op.found(child);
        return op.intermediate(node, i, child, result);
      }
      if (between.contains(nt)) {
        T result = processAlongPath(child, between, target, op);
        return op.intermediate(node, i, child, result);
      }
    }
/*
    System.err.println(
        "Found nothing in " +
            Lists.transform(
                node.getChildren(),
                new Function<BaseNode, NodeType>() {
                  @Override
                  public NodeType apply(BaseNode n) { return n.getNodeType(); }
                }));
*/
    return null;
  }

  static <T extends BaseNode> T find(
      NodeOrBuilder start, ImmutableSet<NodeType> between,
      ImmutableSet<NodeType> target, Class<T> cl) {
    return processAlongPath(
        start, between, target,
        new FindOp<T>() {

          @Override
          public T found(NodeOrBuilder node) {
            return cl.cast(node);
          }

          @Override
          public T intermediate(
              NodeOrBuilder node, int indexOfChild, BaseNode child, T x) {
            return x;
          }

        });
  }

  interface FindOp<T> {
    T found(NodeOrBuilder node);

    T intermediate(NodeOrBuilder node, int indexOfChild, BaseNode child, T x);
  }

  private static DimsNode getDims(NodeOrBuilder node) {
    return find(
        node, BETWEEN_COMMON_ANCESTOR_AND_DIMS, DIMS_NODE_TYPES,
        DimsNode.class);
  }

  private static BaseNode getType(NodeOrBuilder node) {
    return find(
        node, BETWEEN_COMMON_ANCESTOR_AND_TYPE, TYPE_NODE_TYPES,
        BaseNode.class);
  }

  private static BaseNode removeDimsAndAddDimsToType(
      NodeOrBuilder start, DimsNode dims, BaseNode type) {
    Preconditions.checkNotNull(dims);
    Preconditions.checkNotNull(type);
    // First, replace
    BaseNode withFixedType = processAlongPath(
        start,
        BETWEEN_COMMON_ANCESTOR_AND_TYPE, TYPE_NODE_TYPES,
        new FindOp<BaseNode>() {

          @Override
          public TypeNode found(NodeOrBuilder node) {
//            System.err.println("found=" + node);
            TypeNode typeNode = (TypeNode) node;
            // There could be template nodes on this too.
            List<BaseNode> annotationsEtc = Lists.newArrayList();
            for (BaseNode child : dims.getChildren()) {
              if (child instanceof DimNode) {
                ArrayTypeNode.Builder arrayTypeBuilder =
                    ArrayTypeNode.Variant.TypeAnnotationDim.nodeBuilder()
                    .add(typeNode);

                for (BaseNode annotationEtc : annotationsEtc) {
                  arrayTypeBuilder.add(annotationEtc);
                }
                annotationsEtc.clear();

                arrayTypeBuilder.add(child);

                typeNode = TypeNode.Variant.ReferenceType.nodeBuilder()
                    .add(
                        ReferenceTypeNode.Variant.ArrayType.nodeBuilder()
                        .add(arrayTypeBuilder.build())
                        .build())
                    .build();
              } else {
                Preconditions.checkState(
                    child instanceof AnnotationNode
                    || NodeTypeTables.NONSTANDARD.contains(
                        child.getNodeType()));
                annotationsEtc.add(child);
              }
            }
//            System.err.println("\t=> " + typeNode);
            return typeNode;
          }

          @Override
          public BaseNode intermediate(
              NodeOrBuilder node, int indexOfChild, BaseNode child,
              BaseNode newChild) {
//            System.err.println("node\n" + node.toAsciiArt(". "));
//            System.err.println("indexOfChild=" + indexOfChild);
//            System.err.println("child\n" + child.toAsciiArt(". "));
//            System.err.println("newChild\n" + newChild.toAsciiArt(". "));
            Preconditions.checkState(
                child.getNodeType() == newChild.getNodeType(),
                "%s != %s",
                child, newChild);
            BaseNode.InnerBuilder<?, ?> b = (BaseNode.InnerBuilder<?, ?>)
                node.builder();
            b.replace(indexOfChild, newChild);
            return b.build();
          }

        });

//System.err.println("withFixedType\n" + withFixedType.toAsciiArt(". "));
    return processAlongPath(
        withFixedType,
        BETWEEN_COMMON_ANCESTOR_AND_DIMS, DIMS_NODE_TYPES,
        new FindOp<BaseNode>() {

          @Override
          public BaseNode found(NodeOrBuilder node) {
            return null;
          }

          @Override
          public BaseNode intermediate(
              NodeOrBuilder node, int indexOfChild, BaseNode child,
              @Nullable BaseNode newChild) {
            BaseNode.InnerBuilder<?, ?> builder = (InnerBuilder<?, ?>)
                node.builder();
            if (newChild == null) {
              builder.remove(indexOfChild);
            } else {
              builder.replace(indexOfChild, newChild);
            }
            return builder.build();
          }

        });
  }
}
