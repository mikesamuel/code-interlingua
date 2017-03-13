package com.mikesamuel.cil.ast.j8.traits;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * Corresponds to the declaration of a name within an
 * {@link ExpressionNameScope}.
 */
public interface ExpressionNameDeclaration extends J8Trait {
  /**
   * The name declared.
   */
  @Nullable Name getDeclaredExpressionName();

  /**
   * Sets the name declared.
   * @return this
   */
  ExpressionNameDeclaration setDeclaredExpressionName(
      Name newDeclaredExpressionName);

  /**
   * The text of the declared symbol.
   */
  default @Nullable String getDeclaredExpressionIdentifier() {
    // TODO: There has to be a better way than this.
    J8BaseNode node = (J8BaseNode) this;
    IdentifierNode identNode;
    switch (node.getNodeType()) {
      case VariableDeclaratorId:
        identNode = node.firstChildWithType(IdentifierNode.class);
        break;
      case EnumConstant:
        identNode = null;
        J8BaseNode nameNode = node.firstChildWithType(
            J8NodeType.EnumConstantName);
        if (nameNode != null) {
          J8BaseNode fieldNameNode = node.firstChildWithType(
              J8NodeType.FieldName);
          if (fieldNameNode != null) {
            identNode = fieldNameNode.firstChildWithType(IdentifierNode.class);
          }
        }
        break;
      default:
        throw new AssertionError(node.getNodeType());
    }
    return identNode != null ? identNode.getValue() : null;
  }
}
