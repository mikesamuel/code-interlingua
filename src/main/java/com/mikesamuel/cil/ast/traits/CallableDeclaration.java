package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.MethodHeaderNode;
import com.mikesamuel.cil.ast.MethodNameNode;
import com.mikesamuel.cil.ast.NodeType;

/**
 * Declares a callable which corresponds to a JVM method, static method, or
 * special method.
 */
public interface CallableDeclaration
extends ExpressionNameScope, MemberDeclaration {
  /**
   * The name of the method.
   */
  default String getMethodName() {
    MethodHeaderNode header = (MethodHeaderNode)
        ((BaseNode) this).firstChildWithType(NodeType.MethodHeader);
    if (header == null) { return null; }
    MethodDeclaratorNode declarator = (MethodDeclaratorNode)
        header.firstChildWithType(NodeType.MethodDeclarator);
    if (declarator == null) { return null; }
    MethodNameNode name = (MethodNameNode)
        declarator.firstChildWithType(NodeType.MethodName);
    if (name == null) {
      return null;
    }
    IdentifierNode identifier = (IdentifierNode)
        name.firstChildWithType(NodeType.Identifier);
    return identifier != null ? identifier.getValue() : null;
  }

  /**
   * The method descriptor if known.
   * <p>
   * By convention, until parameter types can be resolved to nominal types,
   * the method descriptor is based on the methods ordinal position among
   * methods with the same name declared in the same class.
   */
  String getMethodDescriptor();

  /**
   * @see #getMethodDescriptor
   */
  void setMethodDescriptor(String newMethodDescriptor);
}
