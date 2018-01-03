package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass;

abstract class SingleTypeRewriter extends AbstractRewritingPass {
  final PassState ps;
  final PassState.FlatteningType ft;

  SingleTypeRewriter(
      Logger logger, PassState ps, PassState.FlatteningType ft) {
      super(logger);
      this.ps = ps;
      this.ft = ft;
    }

  final void visit() {
    visitUnchanged((J8BaseNode) ft.root);
  }

  final void visitUnchanged(J8BaseNode node) {
    ProcessingStatus result = visit(node, null);
    Preconditions.checkState(
        result.replacements.size() == 1
        && result.replacements.get(0) == node);
  }
}
