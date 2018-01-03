package com.mikesamuel.cil.ast.passes.flatten;

import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.j8.ArgumentListNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8MemberDeclaration;
import com.mikesamuel.cil.ast.j8.ModifierNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.passes.flatten.PassState.FlatteningType;

final class ForwardClosedOverStateToConstructorsMiniPass {

  final Logger logger;
  final TypePool pool;

  ForwardClosedOverStateToConstructorsMiniPass(Logger logger, TypePool pool) {
    this.logger = logger;
    this.pool = pool;
  }

  void run(PassState ps) {
    for (PassState.FlatteningType ft : ps.inProcessOrder) {
      new Forwarder(ps, ft, false).visit((J8BaseNode) ft.root);
    }
  }

  final class Forwarder {
    final PassState ps;
    final FlatteningType ft;
    final boolean isStatic;

    Forwarder(PassState ps, FlatteningType ft, boolean isStatic) {
      this.ps = ps;
      this.ft = ft;
      this.isStatic = isStatic;
    }

    void visit(J8BaseNode node) {
      Forwarder fwdr = this;
      if (node instanceof J8MemberDeclaration) {
        boolean memberIsStatic = false;
        for (J8BaseNode child : node.getChildren()) {
          if (child.getVariant() == ModifierNode.Variant.Static) {
            memberIsStatic = true;
          }
        }
        fwdr = new Forwarder(ps, ft, memberIsStatic);
      }
      if (node instanceof UnqualifiedClassInstanceCreationExpressionNode) {
        fixup((UnqualifiedClassInstanceCreationExpressionNode) node);
      }
      for (J8BaseNode child : node.getChildren()) {
        fwdr.visit(child);
      }
    }

    private void fixup(UnqualifiedClassInstanceCreationExpressionNode cic) {
      Name cn = null;
      TypeInfo ti = cic.getDeclaredTypeInfo();
      if (ti != null) {
        cn = ti.canonName;
      } else {
        CallableInfo ci = cic.getCallableInfo();
        if (ci != null) {
          cn = ci.canonName.getContainingClass();
        }
      }
      if (cn == null) {
        return;
      }
      FlatteningType cft = ps.byBumpyName.get(BName.of(cn));
      if (cft == null || cft.closedOverInOrder == null
          || cft.closedOverInOrder.isEmpty()) {
        return;
      }

      ArgumentListNode args = cic.firstChildWithType(ArgumentListNode.class);
      if (args == null) {
        args = ArgumentListNode.Variant.ExpressionComExpression.buildNode();
        cic.add(args);
      }

      ImmutableList.Builder<J8BaseNode> b = ImmutableList.builder();
      for (PassState.ClosedOver co : cft.closedOverInOrder) {
        b.add(co.toExpressionNode(ps, ft, isStatic));
      }
      b.addAll(args.getChildren());
      args.replaceChildren(b.build());
    }
  }
}
