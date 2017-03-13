package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeTypeTables;

/**
 * Trait for a production whose non-{@literal @anon} variants have two
 * operands.  In the case of {@code instanceof} the second operand is not
 * an expression but a type.
 */
public interface BinaryOp extends J8Trait {
  /**
   * The left operand.
   */
  public default J8BaseNode getLeftOperand() {
    for (int i = 0, n = this.getNChildren(); i < n; ++i) {
      J8BaseNode child = this.getChild(i);
      J8NodeType nt = child.getNodeType();
      if (J8NodeTypeTables.OPERATOR.contains(nt)) {
        break;
      }
      if (nt.isNonStandard()) {
        continue;
      }
      return child;
    }
    return null;
  }

  /**
   * The right operand.
   */
  public default J8BaseNode getRightOperand() {
    for (int i = this.getNChildren(); --i >= 0;) {
      J8BaseNode child = this.getChild(i);
      J8NodeType nt = child.getNodeType();
      if (J8NodeTypeTables.OPERATOR.contains(nt)) {
        break;
      }
      if (J8NodeTypeTables.NONSTANDARD.contains(nt)) {
        continue;
      }
      return child;
    }
    return null;
  }

}
