package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseInnerNode;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.traits.FileNode;
import com.mikesamuel.cil.parser.SList;

abstract class AbstractRewritingPass
extends AbstractPass<ImmutableList<CompilationUnitNode>> {

  AbstractRewritingPass(Logger logger) {
    super(logger);
  }

  static final class ProcessingStatus {
    /**
     * Continue processing the subtree rooted at the current node.
     * In the post-processing phase, this is equivalent to break.
     */
    static ProcessingStatus CONTINUE = new ProcessingStatus("continue");
    /**
     * Cease processing the subtree rooted at the current node.
     * In the post-processing phase, this means use the current state of the
     * node as the replacement.
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
   * @return {@link ProcessingStatus#BREAK} to skip processing children and
   *     {@linkplain #postvisit post-processing}.
   *     {@link ProcessingStatus#CONTINUE} to continue with those steps
   *     or another status to specify what to replace node with in the
   *     rewritten AST.
   */
  @SuppressWarnings("static-method")  // may be overridden
  protected ProcessingStatus previsit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
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
   * @return {@link ProcessingStatus#BREAK} or
   *     {@link ProcessingStatus#CONTINUE} to leave node in its current position
   *     in the AST, or another status to specify what to use the node's package
   *     with the status as the replacements in the rewritten AST.
   */
  @SuppressWarnings("static-method")  // may be overridden
  protected ProcessingStatus postvisit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    return ProcessingStatus.CONTINUE;
  }

  protected final ProcessingStatus visit(
      BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    ProcessingStatus status = previsit(node, pathFromRoot);
    if (status == ProcessingStatus.CONTINUE) {
      if (node instanceof BaseInnerNode) {
        BaseInnerNode inode = (BaseInnerNode) node;
        List<BaseNode> children = ImmutableList.copyOf(inode.getChildren());
        int j = 0;
        for (int i = 0, n = children.size(); i < n; ++i, ++j) {
          BaseNode child = children.get(i);
          ProcessingStatus childStatus = visit(
              child, SList.append(pathFromRoot, makeParent(i, inode)));
          ImmutableList<BaseNode> replacements;
          if (childStatus == ProcessingStatus.BREAK
              || childStatus == ProcessingStatus.CONTINUE) {
            throw new AssertionError(childStatus);
          } else {
            replacements = childStatus.replacements;
          }

          Preconditions.checkState(inode.getChild(j) == child);

          if (replacements.isEmpty()) {
            inode.remove(j);
            --j;
          } else {
            inode.replace(j, replacements.get(0));
            for (BaseNode extraReplacement
                 : replacements.subList(1, replacements.size())) {
              inode.add(++j, extraReplacement);
            }
          }
        }

      }
      status = postvisit(node, pathFromRoot);
    }
    if (status == ProcessingStatus.BREAK
        || status == ProcessingStatus.CONTINUE) {
      return ProcessingStatus.replace(node);
    } else {
      return status;
    }
  }

  @Override
  ImmutableList<CompilationUnitNode>
  run(Iterable<? extends FileNode> fileNodes) {
    ImmutableList.Builder<CompilationUnitNode> b = ImmutableList.builder();
    for (FileNode fileNode : fileNodes) {
      ProcessingStatus status = visit((BaseNode) fileNode, null);
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
  protected Parent makeParent(int indexInParent, BaseInnerNode parent) {
    return new Parent(indexInParent, parent);
  }


  static class Parent {
    /** Index in parent's child list of the current node. */
    final int indexInParent;
    final BaseInnerNode parent;

    Parent(int indexInParent, BaseInnerNode parent) {
      this.indexInParent = indexInParent;
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "(" + parent.getNodeType() + "#" + indexInParent + ")";
    }
  }

}
