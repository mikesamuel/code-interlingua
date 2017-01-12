package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeTypeTables;

/**
 * Trait for a production whose non-{@literal @anon} variants have two
 * operands.  In the case of {@code instanceof} the second operand is not
 * an expression but a type.
 */
public interface BinaryOp extends NodeI {
  /**
   * The left operand.
   */
  public default BaseNode getLeftOperand() {
    for (int i = 0, n = this.getNChildren(); i < n; ++i) {
      BaseNode child = this.getChild(i);
      NodeType nt = child.getNodeType();
      if (NodeTypeTables.OPERATOR.contains(nt)) {
        break;
      }
      if (NodeTypeTables.NONSTANDARD.contains(nt)) {
        continue;
      }
      return child;
    }
    return null;
  }

  /**
   * The right operand.
   */
  public default BaseNode getRightOperand() {
    for (int i = this.getNChildren(); --i >= 0;) {
      BaseNode child = this.getChild(i);
      NodeType nt = child.getNodeType();
      if (NodeTypeTables.OPERATOR.contains(nt)) {
        break;
      }
      if (NodeTypeTables.NONSTANDARD.contains(nt)) {
        continue;
      }
      return child;
    }
    return null;
  }

}
