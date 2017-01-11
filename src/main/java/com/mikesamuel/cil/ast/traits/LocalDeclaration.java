package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.LastFormalParameterNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.UnannTypeNode;
import com.mikesamuel.cil.ast.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.VariableDeclaratorListNode;

/**
 * Declares one or more local variables.
 */
public interface LocalDeclaration extends NodeI {
  /**
   * The type of the local variables.
   * If the defragment type pass has run, the type is whole.
   * <p>
   * CAVEAT: if the local declaration is a {@link LastFormalParameterNode},
   * as in {@code UnannType... lastFormalParameterName},
   * then the type is the element type of the array.
   *
   * @return null if no type is available which may be the case for a
   *     well-formed template.  Typically this is a {@link UnannTypeNode} but
   *     {@code catch} blocks are an exception to this.
   */
  default BaseNode getDeclaredTypeNode() {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode child = getChild(i);
      NodeType childNodeType = child.getNodeType();
      if (childNodeType == NodeType.UnannType
          || childNodeType == NodeType.CatchType) {
        return child;
      }
    }
    return null;
  }

  /**
   * The sub-trees specifying the names of the locals declared.
   *
   * @return null if no type is available which may be the case for a
   *     well-formed template.  Otherwise a {@link VariableDeclaratorIdNode}
   *      or {@link VariableDeclaratorListNode} containing the names of the
   *      locals declared.
   */
  default BaseNode getDeclaredIdOrIds() {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      BaseNode child = getChild(i);
      NodeType childNodeType = child.getNodeType();
      if (childNodeType == NodeType.VariableDeclaratorId
          || childNodeType == NodeType.VariableDeclaratorList) {
        return child;
      }
    }
    return null;
  }
}
