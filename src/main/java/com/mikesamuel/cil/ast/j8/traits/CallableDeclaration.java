package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.MethodDeclaratorNode;
import com.mikesamuel.cil.ast.j8.MethodHeaderNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;

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
        ((J8BaseNode) this).firstChildWithType(J8NodeType.MethodHeader);
    if (header == null) { return null; }
    MethodDeclaratorNode declarator = (MethodDeclaratorNode)
        header.firstChildWithType(J8NodeType.MethodDeclarator);
    if (declarator == null) { return null; }
    MethodNameNode name = (MethodNameNode)
        declarator.firstChildWithType(J8NodeType.MethodName);
    if (name == null) {
      return null;
    }
    IdentifierNode identifier = (IdentifierNode)
        name.firstChildWithType(J8NodeType.Identifier);
    return identifier != null ? identifier.getValue() : null;
  }

  /**
   * The method {@linkplain com.mikesamuel.cil.ast.meta.Name#variant variant}
   * if known.
   * <p>
   * By convention, until parameter types can be resolved to nominal types,
   * the method variant is based on the methods ordinal position among
   * methods with the same name declared in the same class.
   */
  int getMethodVariant();

  /**
   * @see #getMethodVariant
   * @return this
   */
  CallableDeclaration setMethodVariant(int newMethodVariant);
}
