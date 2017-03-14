package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;

/**
 * Mixin for a production whose non-{@literal @anon} variants have two
 * operands.  In the case of {@code instanceof} the second operand is not
 * an expression but a type.
 */
public interface BinaryOp<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * The left operand.
   */
  public default BaseNode<B, T, V> getLeftOperand() {
    for (int i = 0, n = this.getNChildren(); i < n; ++i) {
      BaseNode<B, T, V> child = this.getChild(i);
      NodeType<B, T> nt = child.getNodeType();
      if (nt.isNonStandard() && nt.getGrammar().isNonStandardInterstitial(nt)) {
        continue;
      }
      return child;
    }
    return null;
  }

  /**
   * The right operand.
   */
  public default BaseNode<B, T, V> getRightOperand() {
    for (int i = this.getNChildren(); --i >= 0;) {
      BaseNode<B, T, V> child = this.getChild(i);
      NodeType<B, T> nt = child.getNodeType();
      if (nt.isNonStandard() && nt.getGrammar().isNonStandardInterstitial(nt)) {
        continue;
      }
      return child;
    }
    return null;
  }

}
