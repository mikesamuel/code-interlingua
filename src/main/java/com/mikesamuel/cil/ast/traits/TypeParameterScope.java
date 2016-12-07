package com.mikesamuel.cil.ast.traits;

import java.util.Collections;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.TypeParameterListNode;
import com.mikesamuel.cil.ast.TypeParameterNode;
import com.mikesamuel.cil.ast.TypeParametersNode;

/**
 * A scope for {@link TypeParametersNode type parameters}.
 */
public interface TypeParameterScope extends TypeScope {

  /**
   * The declared type parameters if any.
   */
  public default Iterable<TypeParameterNode> getDeclaredTypeParameters() {
    TypeParametersNode paramsNode = null;
    for (BaseNode child : ((BaseNode) this).getChildren()) {
      switch (child.getNodeType()) {
        case TypeParameters:
          paramsNode = (TypeParametersNode) child;
          break;
        case ConstructorDeclarator:
        case MethodHeader:
          paramsNode = (TypeParametersNode)
              child.firstChildWithType(NodeType.TypeParameters);
          break;
        default:
          break;
      }
    }
    if (paramsNode == null) {
      return Collections.emptyList();
    }
    TypeParameterListNode paramListNode = (TypeParameterListNode)
        paramsNode.getChildren().get(0);
    return Iterables.transform(
        paramListNode.getChildren(),
        new Function<BaseNode, TypeParameterNode>() {

          @Override
          public TypeParameterNode apply(BaseNode node) {
            return (TypeParameterNode) node;
          }

        });
  }
}
