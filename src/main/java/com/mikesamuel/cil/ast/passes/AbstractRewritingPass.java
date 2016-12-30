package com.mikesamuel.cil.ast.passes;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.BaseNode.Builder;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.parser.SList;

abstract class AbstractRewritingPass
implements AbstractPass<ImmutableList<CompilationUnitNode>> {

  static final class ProcessingStatus {
    /**
     * Continue processing the subtree rooted at the current node.
     * In the post-processing phase, this is equivalent to break.
     */
    static ProcessingStatus CONTINUE = new ProcessingStatus("continue");
    /**
     * Cease processing the subtree rooted at the current node.
     * In the post-processing phase, this means use the state of the builder
     * as the replacement.
     */
    static ProcessingStatus BREAK = new ProcessingStatus("break");
    /**
     * Remove the subtree rooted at the current node and cease .
     */
    static ProcessingStatus REMOVE = new ProcessingStatus("remove");

    static ProcessingStatus replace(BaseNode node, BaseNode... rest) {
      return replace(
          ImmutableList.<BaseNode>builder().add(node).add(rest).build());
    }

    static ProcessingStatus replace(Iterable<? extends BaseNode> replacements) {
      return new ProcessingStatus(
          "replace", ImmutableList.copyOf(replacements));
    }

    private final String text;
    final ImmutableList<BaseNode> replacements;

    private ProcessingStatus(String text) {
      this(text, ImmutableList.of());
    }

    private ProcessingStatus(
        String text, ImmutableList<BaseNode> replacements) {
      this.text = text;
      this.replacements = replacements;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /**
   * Called before processing a node's children.
   *
   * @param node the current node being processed.
   * @param pathFromRoot a set of ancestor and index pairs that form a path
   *   from the root (a {@link CompilationUnitNode}) to node.
   *   It should be the case that node is
   *   {@code pathFromRoot.x.parent.getChildren()
   *    .get(pathFromRoot.x.indexInParent)}
   *   when node is not itself the root, and that the transitive relationship
   *   {@code ls.prev == null
   *     || ls.prev.x.parent.getChildren().get(ls.x.indexInParent)
   *         == ls.x.parent}
   *   holds for all {@code ls} in the chain.
   * @param builder a builder seeded from node.
   * @return {@link ProcessingStatus#BREAK} to skip processing children and
   *     {@linkplain #postvisit post-processing}.
   *     {@link ProcessingStatus#CONTINUE} to continue with those steps
   *     or another status to specify what to replace node with in the
   *     rewritten AST.
   */
  @SuppressWarnings("static-method")  // may be overridden
  protected <N extends BaseNode> ProcessingStatus previsit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    return ProcessingStatus.CONTINUE;
  }

  /**
   * Called after processing a node's children to make any final adjustments.
   *
   * @param node the current node being processed.
   * @param pathFromRoot a set of ancestor and index pairs that form a path
   *   from the root (a {@link CompilationUnitNode}) to node.
   *   It should be the case that node is
   *   {@code pathFromRoot.x.parent.getChildren()
   *    .get(pathFromRoot.x.indexInParent)}
   *   when node is not itself the root, and that the transitive relationship
   *   {@code ls.prev == null
   *     || ls.prev.x.parent.getChildren().get(ls.x.indexInParent)
   *         == ls.x.parent}
   *   holds for all {@code ls} in the chain.
   * @param builder a builder seeded from node.
   * @return {@link ProcessingStatus#BREAK} or
   *     {@link ProcessingStatus#CONTINUE} to use the builder's state as a
   *     replacement for node in the rewritten AST
   *     or another status to specify what to use the node's package with
   *     the status as the replacements in the rewritten AST.
   */
  @SuppressWarnings("static-method")  // may be overridden
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    return ProcessingStatus.CONTINUE;
  }

  protected final <N extends BaseNode> ProcessingStatus visit(
      N node, @Nullable SList<Parent> pathFromRoot) {
    @SuppressWarnings("unchecked")  // Unsound but safe by convention.
    BaseNode.Builder<N, ?> builder = (Builder<N, ?>) node.builder();
    ProcessingStatus status = previsit(node, pathFromRoot, builder);
    if (status == ProcessingStatus.CONTINUE) {
      ImmutableList<BaseNode> children = node.getChildren();
      if (!children.isEmpty()) {
        BaseNode.InnerBuilder<N, ?> ibuilder =
            (BaseNode.InnerBuilder<N, ?>) builder;
        int j = 0;
        for (int i = 0, n = children.size(); i < n; ++i, ++j) {
          BaseNode child = children.get(i);
          ProcessingStatus childStatus = visit(
              child, SList.append(pathFromRoot, makeParent(i, node, builder)));
          ImmutableList<BaseNode> replacements;
          if (childStatus == ProcessingStatus.BREAK
              || childStatus == ProcessingStatus.CONTINUE) {
            throw new AssertionError(childStatus);
          } else {
            replacements = childStatus.replacements;
          }

          Preconditions.checkState(ibuilder.getChild(j) == child);

          if (replacements.isEmpty()) {
            ibuilder.remove(j);
            --j;
          } else {
            ibuilder.replace(j, replacements.get(0));
            for (BaseNode extraReplacement
                 : replacements.subList(1, replacements.size())) {
              ibuilder.add(++j, extraReplacement);
            }
          }
        }

      }
      status = postvisit(node, pathFromRoot, builder);
    }
    if (status == ProcessingStatus.BREAK
        || status == ProcessingStatus.CONTINUE) {
      if (builder.changed()) {
        return ProcessingStatus.replace(builder.build());
      } else {
        return ProcessingStatus.replace(node);
      }
    } else {
      return status;
    }
  }

  @Override
  public ImmutableList<CompilationUnitNode>
  run(Iterable<? extends CompilationUnitNode> compilationUnits) {
    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (CompilationUnitNode compilationUnit : compilationUnits) {
      ProcessingStatus status = visit(compilationUnit, null);
      if (status == ProcessingStatus.BREAK
          || status == ProcessingStatus.CONTINUE) {
        throw new AssertionError(status);
      } else {
        for (BaseNode replacement : status.replacements) {
          b.add((CompilationUnitNode) replacement);
        }
      }
    }
    return b.build();
  }

  @SuppressWarnings("static-method")
  protected <N extends BaseNode> Parent makeParent(
      int indexInParent, N parent, BaseNode.Builder<N, ?> parentBuilder) {
    return new Parent(indexInParent, parent, parentBuilder);
  }


  static class Parent {
    /** Index in parent's child list of the current node. */
    final int indexInParent;
    final BaseNode parent;
    final BaseNode.Builder<?, ?> parentBuilder;

    Parent(
        int indexInParent, BaseNode parent,
        BaseNode.Builder<?, ?> parentBuilder) {
      this.indexInParent = indexInParent;
      this.parent = parent;
      this.parentBuilder = parentBuilder;
    }
  }

}
