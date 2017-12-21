package com.mikesamuel.cil.ast.j8.ti;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.j8.AssignmentNode;
import com.mikesamuel.cil.ast.j8.AssignmentOperatorNode;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeToInstantiateNode;
import com.mikesamuel.cil.ast.j8.ExpressionAtomNode;
import com.mikesamuel.cil.ast.j8.ExpressionNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsOrDiamondNode;
import com.mikesamuel.cil.ast.j8.UnqualifiedClassInstanceCreationExpressionNode;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.PartialTypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification;
import com.mikesamuel.cil.ast.meta.TypeSpecification.TypeBinding;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass;
import com.mikesamuel.cil.ast.passes.AbstractRewritingPass.Parent;
import com.mikesamuel.cil.parser.SList;

final class Polyexpressions {

  static boolean isStandaloneExpression(
      SList<AbstractRewritingPass.Parent> pathFromRoot) {
    if (pathFromRoot == null) { return false; }
    return !Polyexpressions.isPolyExpression(pathFromRoot);
  }


  static boolean isPolyExpression(
      SList<AbstractRewritingPass.Parent> pathFromRoot) {
    if (pathFromRoot == null) { return false; }
    J8BaseNode expr = pathFromRoot.x.get();
    J8NodeVariant v = expr.getVariant();
    while (v == ExpressionAtomNode.Variant.Parenthesized) {
      expr = expr.firstChildWithType(J8NodeType.Expression);
      if (expr == null) {
        return false;
      }
      v = expr.getVariant();
    }
    if (v == ExpressionAtomNode.Variant.UnqualifiedClassInstanceCreationExpression
        || v == PrimaryNode.Variant.InnerClassCreation) {
      UnqualifiedClassInstanceCreationExpressionNode ctorCall =
          expr.firstChildWithType(
              UnqualifiedClassInstanceCreationExpressionNode.class);
      return ctorCall != null
          && inAssignmentOrInvocationContext(pathFromRoot.prev)
          && hasDiamond(ctorCall);
    } else if (v == PrimaryNode.Variant.MethodInvocation) {
      return inAssignmentOrInvocationContext(pathFromRoot.prev)
          && returnTypeUsesMethodTypeParameter((PrimaryNode) expr);
    } else if (v == PrimaryNode.Variant.MethodReference) {
      // TODO
      return false;
    } else if (v == ExpressionNode.Variant.LambdaExpression) {
      // TODO
      return false;
    } else {
      return false;
    }
  }

  private static boolean returnTypeUsesMethodTypeParameter(
      PrimaryNode methodInvocation) {
    MethodNameNode name = methodInvocation.firstChildWithType(
        MethodNameNode.class);
    CallableInfo ci = name != null ? name.getCallableInfo() : null;
    if (ci == null) {
      // TODO log
      return false;
    }
    if (!ci.typeParameters.isEmpty()) {
      TypeSpecification rt = ci.getReturnType();
      return rt != null && hasParentRecursively(rt, ci.canonName);
    }
    return false;
  }

  private static boolean hasParentRecursively(
      TypeSpecification ts, Name typeVariableScope) {
    if (ts.rawName.type == Name.Type.TYPE_PARAMETER
        && ts.rawName.parent.equals(typeVariableScope)) {
      return true;
    }
    for (PartialTypeSpecification pts = ts; pts != null; pts = pts.parent()) {
      for (TypeBinding b : pts.bindings()) {
        if (b.typeSpec != null && hasParentRecursively(b.typeSpec, typeVariableScope)) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasDiamond(
      UnqualifiedClassInstanceCreationExpressionNode e) {
    ClassOrInterfaceTypeToInstantiateNode type = e.firstChildWithType(
        ClassOrInterfaceTypeToInstantiateNode.class);
    if (type != null) {
      TypeArgumentsOrDiamondNode args = e.firstChildWithType(
          TypeArgumentsOrDiamondNode.class);
      return args != null && args.getVariant() ==
          TypeArgumentsOrDiamondNode.Variant.Diamond;
    }
    return false;
  }

  private static ImmutableSet<J8NodeVariant> IGNORABLE_EXPRESSION_VARIANTS
      = ImmutableSet.of(
          ExpressionNode.Variant.ConditionalExpression,
          ExpressionAtomNode.Variant.Parenthesized
          );


  static boolean inAssignmentOrInvocationContext(SList<Parent> pathFromRoot) {
    SList<Parent> expressionAncestorList = pathFromRoot;
    for (; expressionAncestorList != null;
         expressionAncestorList = expressionAncestorList.prev) {
      if (!IGNORABLE_EXPRESSION_VARIANTS.contains(
              expressionAncestorList.x.parent.getVariant())) {
        break;
      }
    }
    if (expressionAncestorList == null) { return false; }  // Missing context
    // JLS Sections 5.2 & 5.3
    J8BaseNode expressionAncestor = expressionAncestorList.x.parent;
    J8NodeVariant expressionAncestorVariant = expressionAncestor.getVariant();
    if (expressionAncestorVariant
        == AssignmentNode.Variant.LeftHandSideAssignmentOperatorExpression) {
      AssignmentOperatorNode op = expressionAncestor.firstChildWithType(
          AssignmentOperatorNode.class);
      return op != null
          && op.getVariant() == AssignmentOperatorNode.Variant.Eq;
    }

    return false;
  }

}
