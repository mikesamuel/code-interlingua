package com.mikesamuel.cil.ast.passes;

import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.CompilationUnitNode;
import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.traits.FileNode;
import com.mikesamuel.cil.parser.SList;

/**
 * A pass which makes it simple to process a tree and rewrite in-place.
 */
public abstract class AbstractRewritingPass
extends AbstractPass<ImmutableList<FileNode>> {

  protected AbstractRewritingPass(Logger logger) {
    super(logger);
  }


  /**
   * Specifies replacements for the node being processed, and how/whether to
   * continue additional processing.
   */
  public static final class ProcessingStatus {
    /**
     * Continue processing the subtree rooted at the current node.
     * In the post-processing phase, this is equivalent to break.
     */
    public static ProcessingStatus CONTINUE = new ProcessingStatus(
        Mutation.CONTINUE);
    /**
     * Cease processing the subtree rooted at the current node.
     * In the post-processing phase, this means use the current state of the
     * node as the replacement.
     */
    public static ProcessingStatus BREAK = new ProcessingStatus(
        Mutation.BREAK);
    /**
     * Remove the subtree rooted at the current node and cease .
     */
    public static ProcessingStatus REMOVE = new ProcessingStatus(
        Mutation.REPLACE);

    /**
     * A status that specifies that the current node should be replaced with
     * the given ones in order.
     */
    public static ProcessingStatus replace(
        J8BaseNode node, J8BaseNode... rest) {
      return replace(
          ImmutableList.<J8BaseNode>builder().add(node).add(rest).build());
    }

    /**
     * A status that specifies that the current node should be replaced with
     * the given ones in order.
     */
    public static ProcessingStatus replace(
        Iterable<? extends J8BaseNode> replacements) {
      return new ProcessingStatus(
          Mutation.REPLACE, ImmutableList.copyOf(replacements));
    }

    final Mutation mut;
    /** The set of replacements. */
    public final ImmutableList<J8BaseNode> replacements;

    private ProcessingStatus(Mutation mut) {
      this(mut, ImmutableList.of());
    }

    private ProcessingStatus(
        Mutation mut, ImmutableList<J8BaseNode> replacements) {
      this.mut = mut;
      this.replacements = replacements;
    }

    @Override
    public String toString() {
      return mut.name();
    }
  }


  private enum Mutation {
    BREAK,
    CONTINUE,
    REPLACE,
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
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
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
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    return ProcessingStatus.CONTINUE;
  }

  protected final ProcessingStatus visit(
      J8BaseNode node, @Nullable SList<Parent> pathFromRoot) {
    ProcessingStatus status = previsit(node, pathFromRoot);
    if (status == ProcessingStatus.CONTINUE) {
      if (node instanceof J8BaseInnerNode) {
        visitChildren((J8BaseInnerNode) node, pathFromRoot);
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

  protected final void visitChildren(
      J8BaseInnerNode node, @Nullable SList<Parent> pathFromRoot) {
    List<J8BaseNode> children = ImmutableList.copyOf(node.getChildren());
    int j = 0;
    for (int i = 0, n = children.size(); i < n; ++i, ++j) {
      J8BaseNode child = children.get(i);
      ProcessingStatus childStatus = visit(
          child, SList.append(pathFromRoot, makeParent(j, node)));

      Preconditions.checkState(childStatus.mut == Mutation.REPLACE);
      Preconditions.checkState(node.getChild(j) == child);

      ImmutableList<J8BaseNode> replacements = childStatus.replacements;
      if (replacements.isEmpty()) {
        node.remove(j);
        --j;
      } else {
        node.replace(j, replacements.get(0));
        for (J8BaseNode extraReplacement
             : replacements.subList(1, replacements.size())) {
          node.add(++j, extraReplacement);
        }
      }
    }

  }

  @Override
  public ImmutableList<FileNode> run(Iterable<? extends FileNode> fileNodes) {
    ImmutableList.Builder<FileNode> b = ImmutableList.builder();
    for (FileNode fileNode : fileNodes) {
      ProcessingStatus status = visit((J8BaseNode) fileNode, null);
      Preconditions.checkState(status.mut == Mutation.REPLACE);
      for (J8BaseNode replacement : status.replacements) {
        b.add((FileNode) replacement);
      }
    }
    return b.build();
  }

  @SuppressWarnings("static-method")
  protected Parent makeParent(int indexInParent, J8BaseInnerNode parent) {
    return new Parent(indexInParent, parent);
  }


  /**
   * The position of a node in its parent.
   */
  public static class Parent {
    /** Index in parent's child list of the current node. */
    public final int indexInParent;
    /** The parent of the current node. */
    public final J8BaseInnerNode parent;

    Parent(int indexInParent, J8BaseInnerNode parent) {
      this.indexInParent = indexInParent;
      this.parent = parent;
    }

    @Override
    public String toString() {
      return "(" + parent.getNodeType() + "#" + indexInParent + ")";
    }
  }

}
