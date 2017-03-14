package com.mikesamuel.cil.ast.j8;

import javax.annotation.Nullable;

/**
 * Some utilities for dealing with mixins.  These used to be part of the mixins
 * but since they deal with grammar specific node types they've moved here.
 * <p>
 * TODO: Figure out how the j8 and jmin grammars can share these and ideally
 * make them part of the mixins.
 */
public final class Mixins {
  private Mixins() {
    // static API
  }

  /**
   * The name of the method.
   */
  public static @Nullable String getMethodName(J8CallableDeclaration d) {
    switch (d.getNodeType()) {
      case ConstructorDeclaration:
      case InstanceInitializer:
        return "<init>";
      case StaticInitializer:
        return "<clinit>";
      default:
        break;
    }
    MethodHeaderNode header = (MethodHeaderNode)
        ((J8BaseNode) d).firstChildWithType(J8NodeType.MethodHeader);
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
   * The text of the declared symbol.
   */
  public static @Nullable String getDeclaredExpressionIdentifier(
      J8ExpressionNameDeclaration d) {
    // TODO: There has to be a better way than this.
    J8BaseNode node = (J8BaseNode) d;
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
  public static J8BaseNode getDeclaredTypeNode(J8LocalDeclaration d) {
    for (int i = 0, n = d.getNChildren(); i < n; ++i) {
      J8BaseNode child = d.getChild(i);
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
  public static J8BaseNode getDeclaredIdOrIds(J8LocalDeclaration d) {
    for (int i = 0, n = d.getNChildren(); i < n; ++i) {
      J8BaseNode child = d.getChild(i);
      J8NodeType childNodeType = child.getNodeType();
      if (childNodeType == J8NodeType.VariableDeclaratorId
          || childNodeType == J8NodeType.VariableDeclaratorList) {
        return child;
      }
    }
    return null;
  }

}
