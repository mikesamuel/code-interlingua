package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.LastFormalParameterNode;
import com.mikesamuel.cil.ast.j8.UnannTypeNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorIdNode;
import com.mikesamuel.cil.ast.j8.VariableDeclaratorListNode;

/**
 * Declares one or more local variables.
 */
public interface LocalDeclaration extends J8Trait {
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
  default J8BaseNode getDeclaredTypeNode() {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      J8BaseNode child = getChild(i);
      J8NodeType childNodeType = child.getNodeType();
      if (childNodeType == J8NodeType.UnannType
          || childNodeType == J8NodeType.CatchType) {
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
  default J8BaseNode getDeclaredIdOrIds() {
    for (int i = 0, n = getNChildren(); i < n; ++i) {
      J8BaseNode child = getChild(i);
      J8NodeType childNodeType = child.getNodeType();
      if (childNodeType == J8NodeType.VariableDeclaratorId
          || childNodeType == J8NodeType.VariableDeclaratorList) {
        return child;
      }
    }
    return null;
  }
}
