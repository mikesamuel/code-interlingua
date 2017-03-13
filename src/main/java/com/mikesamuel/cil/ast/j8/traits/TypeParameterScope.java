package com.mikesamuel.cil.ast.j8.traits;

import java.util.Collections;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.TypeParameterListNode;
import com.mikesamuel.cil.ast.j8.TypeParameterNode;
import com.mikesamuel.cil.ast.j8.TypeParametersNode;

/**
 * A scope for {@link TypeParametersNode type parameters}.
 */
public interface TypeParameterScope extends TypeScope {

  /**
   * The declared type parameters if any.
   */
  public default Iterable<TypeParameterNode> getDeclaredTypeParameters() {
    TypeParametersNode paramsNode = null;
    for (J8BaseNode child : ((J8BaseNode) this).getChildren()) {
      switch (child.getNodeType()) {
        case TypeParameters:
          paramsNode = (TypeParametersNode) child;
          break;
        case ConstructorDeclarator:
        case MethodHeader:
          paramsNode = (TypeParametersNode)
              child.firstChildWithType(J8NodeType.TypeParameters);
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
        new Function<J8BaseNode, TypeParameterNode>() {

          @Override
          public TypeParameterNode apply(J8BaseNode node) {
            return (TypeParameterNode) node;
          }

        });
  }
}
