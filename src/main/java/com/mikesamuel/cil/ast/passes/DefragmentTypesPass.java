package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.j8.AnnotationNode;
import com.mikesamuel.cil.ast.j8.ArrayTypeNode;
import com.mikesamuel.cil.ast.j8.BlockStatementNode;
import com.mikesamuel.cil.ast.j8.DimNode;
import com.mikesamuel.cil.ast.j8.DimsNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeTypeTables;
import com.mikesamuel.cil.ast.j8.LocalVariableDeclarationStatementNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.TypeNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorListNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorNode;
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

  DefragmentTypesPass(Logger logger) {
    super(logger);
  }

  private static int countDims(@Nullable DimsNode dims) {
    int n = 0;
    if (dims != null) {
      for (J8BaseNode child : dims.getChildren()) {
        if (child.getNodeType() == J8NodeType.Dim) {
          ++n;
        }
      }
    }
    return n;
  }

  @Override
  protected ProcessingStatus postvisit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    if (!COMMON_ANCESTOR.contains(node.getNodeType())) {
      return ProcessingStatus.CONTINUE;
    }
    ImmutableList<? extends J8BaseNode> declList = splitDecls(node);
    ImmutableList.Builder<J8BaseNode> replacements = ImmutableList.builder();
    boolean foundOne = false;
    for (J8BaseNode decl : declList) {
      DimsNode dims = getDims(decl);
      if (dims != null) {
        J8BaseNode type = getType(decl);
        if (type != null) {
          J8BaseNode replacement = removeDimsAndAddDimsToType(decl, dims, type);
          replacements.add(replacement);
          foundOne = true;
          continue;
        } else {
          error(dims,
              "Floating array dimensions [] could not be reattached to a type");
        }
      }
      replacements.add(decl);
    }
    return foundOne
        ? ProcessingStatus.replace(replacements.build())
        : ProcessingStatus.CONTINUE;
  }

  /**
   * Any node type that can contain a {@link TypeNode} and floating
   * {@link DimsNode} that modify that type.
   * <p>
   * If the Dims are part of a {@link VariableDeclaratorListNode} then the
   * ancestor might be cloned, so we use the closest common ancestor that
   * appears in a {...} repetition.
   */
  private static final ImmutableSet<J8NodeType> COMMON_ANCESTOR =
      Sets.immutableEnumSet(
          J8NodeType.AnnotationTypeElementDeclaration,
          J8NodeType.BlockStatement,
          J8NodeType.CatchFormalParameter,
          J8NodeType.ClassBodyDeclaration,
          J8NodeType.EnhancedForStatement,
          J8NodeType.FormalParameter,
          J8NodeType.InterfaceMemberDeclaration,
          J8NodeType.LastFormalParameter,
          J8NodeType.MethodHeader,
          J8NodeType.Resource
          );

  /**
   * Any nodes on the path from a common ancestor to its Dims.
   */
  private static final
  ImmutableSet<J8NodeType> BETWEEN_COMMON_ANCESTOR_AND_DIMS =
      Sets.immutableEnumSet(
          // From ClassBodyDeclaration
          J8NodeType.ClassMemberDeclaration,
          J8NodeType.FieldDeclaration,

          // From InterfaceMemberDeclaration
          J8NodeType.ConstantDeclaration,

          // From BlockStatement
          J8NodeType.LocalVariableDeclarationStatement,
          J8NodeType.LocalVariableDeclaration,
          J8NodeType.Statement,
          J8NodeType.ForStatement,
          J8NodeType.BasicForStatement,
          J8NodeType.ForInit,

          J8NodeType.VariableDeclarator,
          J8NodeType.VariableDeclaratorId,
          J8NodeType.VariableDeclaratorList,
          J8NodeType.AnnotationTypeElementDeclaration,
          J8NodeType.MethodHeader,
          J8NodeType.MethodDeclarator);

  /**
   * Any nodes on the path from a common ancestor to its type.
   */
  private static final
  ImmutableSet<J8NodeType> BETWEEN_COMMON_ANCESTOR_AND_TYPE =
      Sets.immutableEnumSet(
          // From ClassBodyDeclaration
          J8NodeType.ClassMemberDeclaration,
          J8NodeType.FieldDeclaration,

          // From InterfaceMemberDeclaration
          J8NodeType.ConstantDeclaration,

          // From BlockStatement
          J8NodeType.LocalVariableDeclarationStatement,
          J8NodeType.LocalVariableDeclaration,
          J8NodeType.Statement,
          J8NodeType.ForStatement,
          J8NodeType.BasicForStatement,
          J8NodeType.ForInit,

          // From others
          J8NodeType.MethodHeader,
          J8NodeType.Result,
          J8NodeType.UnannType);

  private static final ImmutableSet<J8NodeType> TYPE_NODE_TYPES =
      Sets.immutableEnumSet(J8NodeType.Type, J8NodeType.ReferenceType);

  private static final ImmutableSet<J8NodeType> DIMS_NODE_TYPES =
      Sets.immutableEnumSet(J8NodeType.Dims);

  private static final ImmutableSet<J8NodeType> VAR_DECL_LIST_NODE_TYPES =
      Sets.immutableEnumSet(J8NodeType.VariableDeclaratorList);

  /**
   * When splitting a VariableDeclaratorList, we can't duplicate some structures
   * like
   * <code>for (int i = 0, j[] = {}; ...);</code>
   * into
   * <code>for (int i = 0; ...); for (int[] j = {}, ...);</code>
   * without changing semantics.
   * Instead, for all but the last, we drop these node types from the
   * duplication, leaving a vanilla {@link VariableDeclarationStatementNode}.
   */
  private static final ImmutableSet<J8NodeType> NO_REWRAP_SPLIT =
      Sets.immutableEnumSet(
          J8NodeType.ForInit, J8NodeType.BasicForStatement,
          J8NodeType.ForStatement, J8NodeType.Statement
          );

  static <T> T processAlongPath(
      J8BaseNode node, ImmutableSet<J8NodeType> between,
      ImmutableSet<J8NodeType> target, FindOp<T> op) {
    List<J8BaseNode> children = node.getChildren();
    for (int i = 0, n = children.size(); i < n; ++i) {
      J8BaseNode child = children.get(i);
      J8NodeType nt = child.getNodeType();
      if (target.contains(nt)) {
        T result = op.found(child);
        return op.intermediate(node, i, child, result);
      }
      if (between.contains(nt)) {
        T result = processAlongPath(child, between, target, op);
        return op.intermediate(node, i, child, result);
      }
    }
    return null;
  }

  static ImmutableList<? extends J8BaseNode> splitDecls(J8BaseNode start) {
    ImmutableList<J8BaseNode> ls = processAlongPath(
        start, BETWEEN_COMMON_ANCESTOR_AND_DIMS, VAR_DECL_LIST_NODE_TYPES,
        new FindOp<ImmutableList<J8BaseNode>>() {

          @SuppressWarnings("synthetic-access")
          @Override
          public ImmutableList<J8BaseNode> found(J8BaseNode node) {
            ImmutableList.Builder<J8BaseNode> split = null;

            VariableDeclaratorListNode declList =
                (VariableDeclaratorListNode) node;
            int lastSplitIndex = 0;
            int lastDimCount = -1;
            for (int i = 0, n = declList.getNChildren(); i <= n; ++i) {
              boolean needToAdd = false;
              if (i == n) {
                needToAdd = split != null;
              } else {
                J8BaseNode child = declList.getChild(i);
                if (child instanceof VariableDeclaratorNode) {
                  DimsNode dims = getDims(child);
                  int childDimCount = countDims(dims);
                  if (lastDimCount != -1 && childDimCount != lastDimCount) {
                    needToAdd = true;
                  }
                  lastDimCount = childDimCount;
                }
              }
              if (needToAdd) {
                if (split == null) {
                  split = ImmutableList.builder();
                }
                VariableDeclaratorListNode lsCopy =
                    declList.getVariant().buildNode(ImmutableList.of());
                lsCopy.copyMetadataFrom(declList);
                for (int j = lastSplitIndex; j < i; ++j) {
                  J8BaseNode splitChild = declList.getChild(j);
                  if (j != lastSplitIndex) {
                    // Remove redundant DimsNodes since later passes assume
                    // that there is a 1:1 relationship between DimsNodes
                    // and TypeNodes.
                    splitChild = removeDims(splitChild);
                  }
                  lsCopy.add(splitChild);
                }
                split.add(lsCopy);
                lastSplitIndex = i;
              }
            }
            return split != null ? split.build() : null;
          }

          @SuppressWarnings("synthetic-access")
          @Override
          public ImmutableList<J8BaseNode> intermediate(
              J8BaseNode node, int indexOfChild, J8BaseNode child,
              ImmutableList<J8BaseNode> splitChildren) {
            if (splitChildren == null) {
              return null;
            }
            ImmutableList.Builder<J8BaseNode> splitNodes =
                ImmutableList.builder();
            for (int i = 0, n = splitChildren.size(); i < n; ++i) {
              J8BaseNode splitChild = splitChildren.get(i);

              J8BaseNode wrappedChild;
              // We hack around local variable declarations in for statements.
              if (NO_REWRAP_SPLIT.contains(node.getNodeType())
                  && i + 1 < n) {
                wrappedChild = splitChild;
              } else {
                if (node.getNodeType() == J8NodeType.BlockStatement
                    && splitChild.getNodeType()
                       == J8NodeType.LocalVariableDeclaration) {
                  wrappedChild = BlockStatementNode.Variant
                      .LocalVariableDeclarationStatement
                      .buildNode(node.getChildren());
                  wrappedChild.copyMetadataFrom(node);

                  splitChild =
                      LocalVariableDeclarationStatementNode
                      .Variant.LocalVariableDeclarationSem.buildNode(
                          ImmutableList.of(splitChild));
                } else {
                  wrappedChild = ((J8BaseInnerNode) node).shallowClone();
                }
                ((J8BaseInnerNode) wrappedChild).replace(
                    indexOfChild, splitChild);
              }
              splitNodes.add(wrappedChild);
            }
            return splitNodes.build();
          }

        });
    if (ls == null) {
      return ImmutableList.of(start);
    }
    return ls;
  }

  static <T extends J8BaseNode> T find(
      J8BaseNode start, ImmutableSet<J8NodeType> between,
      ImmutableSet<J8NodeType> target, Class<T> cl) {
    return processAlongPath(
        start, between, target,
        new FindOp<T>() {

          @Override
          public T found(J8BaseNode node) {
            return cl.cast(node);
          }

          @Override
          public T intermediate(
              J8BaseNode node, int indexOfChild, J8BaseNode child, T x) {
            return x;
          }

        });
  }

  interface FindOp<T> {
    T found(J8BaseNode node);

    T intermediate(J8BaseNode node, int indexOfChild, J8BaseNode child, T x);
  }

  private static DimsNode getDims(J8BaseNode node) {
    return find(
        node, BETWEEN_COMMON_ANCESTOR_AND_DIMS, DIMS_NODE_TYPES,
        DimsNode.class);
  }

  private static J8BaseNode getType(J8BaseNode node) {
    return find(
        node, BETWEEN_COMMON_ANCESTOR_AND_TYPE, TYPE_NODE_TYPES,
        J8BaseNode.class);
  }

  private static J8BaseNode removeDimsAndAddDimsToType(
      J8BaseNode start, DimsNode dims, J8BaseNode type) {
    Preconditions.checkNotNull(dims);
    Preconditions.checkNotNull(type);
    // First, replace
    J8BaseNode withFixedType = processAlongPath(
        start,
        BETWEEN_COMMON_ANCESTOR_AND_TYPE, TYPE_NODE_TYPES,
        new FindOp<J8BaseNode>() {

          @Override
          public TypeNode found(J8BaseNode node) {
            TypeNode typeNode = (TypeNode) node;
            // There could be template nodes on this too.
            List<J8BaseNode> annotationsEtc = Lists.newArrayList();
            for (J8BaseNode child : dims.getChildren()) {
              if (child instanceof DimNode) {
                ArrayTypeNode newArrayType =
                    ArrayTypeNode.Variant.TypeAnnotationDim.buildNode(
                        ImmutableList.of(typeNode));

                for (J8BaseNode annotationEtc : annotationsEtc) {
                  newArrayType.add(annotationEtc);
                }
                annotationsEtc.clear();

                newArrayType.add(child);

                typeNode = TypeNode.Variant.ReferenceType.buildNode(
                    ImmutableList.of(
                        ReferenceTypeNode.Variant.ArrayType.buildNode(
                            ImmutableList.of(newArrayType))));
              } else {
                Preconditions.checkState(
                    child instanceof AnnotationNode
                    || J8NodeTypeTables.NONSTANDARD.contains(
                        child.getNodeType()));
                annotationsEtc.add(child);
              }
            }
            return typeNode;
          }

          @Override
          public J8BaseNode intermediate(
              J8BaseNode node, int indexOfChild, J8BaseNode child,
              J8BaseNode newChild) {
            Preconditions.checkState(
                child.getNodeType() == newChild.getNodeType(),
                "%s != %s",
                child, newChild);
            J8BaseInnerNode copy = ((J8BaseInnerNode) node).shallowClone();
            copy.replace(indexOfChild, newChild);
            return copy;
          }

        });

    return removeDims(withFixedType);
  }

  private static J8BaseNode removeDims(J8BaseNode start) {
    return processAlongPath(
        start,
        BETWEEN_COMMON_ANCESTOR_AND_DIMS, DIMS_NODE_TYPES,
        new FindOp<J8BaseNode>() {

          @Override
          public J8BaseNode found(J8BaseNode node) {
            return null;
          }

          @Override
          public J8BaseNode intermediate(
              J8BaseNode node, int indexOfChild, J8BaseNode child,
              @Nullable J8BaseNode newChild) {
            J8BaseInnerNode copy = (J8BaseInnerNode) node.shallowClone();
            if (newChild == null) {
              copy.remove(indexOfChild);
            } else {
              copy.replace(indexOfChild, newChild);
            }
            return copy;
          }

        });

  }
}
