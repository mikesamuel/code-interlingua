package com.mikesamuel.cil.ast.traits;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.meta.Name;

/**
 * Corresponds to the declaration of a name within an
 * {@link ExpressionNameScope}.
 */
public interface ExpressionNameDeclaration extends NodeI {
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
    BaseNode node = (BaseNode) this;
    IdentifierNode identNode;
    switch (node.getNodeType()) {
      case VariableDeclaratorId:
        identNode = node.firstChildWithType(IdentifierNode.class);
        break;
      case EnumConstant:
        identNode = null;
        BaseNode nameNode = node.firstChildWithType(NodeType.EnumConstantName);
        if (nameNode != null) {
          BaseNode fieldNameNode = node.firstChildWithType(NodeType.FieldName);
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
