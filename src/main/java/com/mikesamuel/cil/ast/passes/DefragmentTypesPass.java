package com.mikesamuel.cil.ast.passes;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.DimsNode;
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
  protected <N extends BaseNode> ProcessingStatus postvisit(
      N node, @Nullable SList<Parent> pathFromRoot,
      BaseNode.Builder<N, ?> builder) {
    if (node instanceof DimsNode) {
      System.err.println("foo");
    }
    return ProcessingStatus.CONTINUE;
  }

}
