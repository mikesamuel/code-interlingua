package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;
import com.mikesamuel.cil.ptree.Profile;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

import java.util.EnumSet;

@SuppressWarnings("javadoc")
public final class ExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void testIntLit() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        "1",
        push(ExpressionNode.Variant.AssignmentExpression),
        push(AssignmentExpressionNode.Variant.ConditionalExpression),
        push(ConditionalExpressionNode.Variant.ConditionalOrExpression),
        push(ConditionalOrExpressionNode.Variant.ConditionalAndExpression),
        push(ConditionalAndExpressionNode.Variant.InclusiveOrExpression),
        push(InclusiveOrExpressionNode.Variant.ExclusiveOrExpression),
        push(ExclusiveOrExpressionNode.Variant.AndExpression),
        push(AndExpressionNode.Variant.EqualityExpression),
        push(EqualityExpressionNode.Variant.RelationalExpression),
        push(RelationalExpressionNode.Variant.ShiftExpression),
        push(ShiftExpressionNode.Variant.AdditiveExpression),
        push(AdditiveExpressionNode.Variant.MultiplicativeExpression),
        push(MultiplicativeExpressionNode.Variant.UnaryExpression),
        push(UnaryExpressionNode.Variant.UnaryExpressionNotPlusMinus),
        push(UnaryExpressionNotPlusMinusNode.Variant.PostfixExpression),
        push(PostfixExpressionNode.Variant.Primary),
        push(PrimaryNode.Variant.PrimaryNoNewArray),
        push(PrimaryNoNewArrayNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testAdd3() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        EnumSet.of(
            NodeType.Primary,
            NodeType.Expression,
            NodeType.AdditiveExpression,
            NodeType.AdditiveOperator,
            NodeType.IntegerLiteral),
        "1 + 1 + 1",
        // AssignmentExpression as opposed to LambdaExpression
        /**/push(ExpressionNode.Variant.AssignmentExpression),
        // + is left-associative so this is (1 + 1) + 1
        /*..*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*....*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*......*/push(AdditiveExpressionNode.Variant.MultiplicativeExpression),
        // A lot of stuff b/c of filter set above
        /*........*/push(PrimaryNode.Variant.PrimaryNoNewArray),
        /*..........*/push(IntegerLiteralNode.Variant.Builtin),
        /*............*/content("1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/push(AdditiveOperatorNode.Variant.Pls),
        /*........*/token("+", -1),
        /*......*/pop(),
        /*......*/push(PrimaryNode.Variant.PrimaryNoNewArray),
        /*........*/push(IntegerLiteralNode.Variant.Builtin),
        /*..........*/content("1", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/push(AdditiveOperatorNode.Variant.Pls),
        /*......*/token("+", -1),  // Why is this a child of multiplicative?
        /*....*/pop(),
        /*....*/push(PrimaryNode.Variant.PrimaryNoNewArray),
        /*......*/push(IntegerLiteralNode.Variant.Builtin),
        /*........*/content("1", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop());
  }

  @Test
  public void testSimpleFieldAccess() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        EnumSet.of(
            NodeType.Expression,
            NodeType.ExpressionName,
            NodeType.FieldAccess,
            NodeType.Identifier,
            NodeType.Primary),
        // This does not actually match the FieldAccess production.
        // It actually matches the ExpressionName production.
        // FieldAccess is reserved for those productions like
        // (complexExpression).field.
        "obj.field",
        push(ExpressionNode.Variant.AssignmentExpression),
        push(ExpressionNameNode.Variant.ExpressionNameDotIdentifier),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("obj", -1),
        pop(),
        pop(),
        token(".", -1),
        push(IdentifierNode.Variant.Builtin),
        content("field", -1),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testSimpleMethodCall() {
    try (Profile p = Profile.startCounting()) {
      assertParsePasses(
          PTree.complete(NodeType.Expression),
          EnumSet.of(
              NodeType.Expression,
              NodeType.ExpressionName,
              NodeType.FieldAccess,
              NodeType.MethodInvocation,
              NodeType.Identifier,
              NodeType.Primary),
          // This does not actually match the FieldAccess production.
          // It actually matches the ExpressionName production.
          // FieldAccess is reserved for those productions like
          // (complexExpression).field.
          "obj.method()",
          /**/push(ExpressionNode.Variant.AssignmentExpression),
          /*..*/push(PrimaryNode.Variant.PrimaryNoNewArray),
          /*....*/push(MethodInvocationNode.Variant
          /*....*/     .ExpressionNameDotTypeArgumentsIdentifierLpArgumentListRp),
          /*......*/push(ExpressionNameNode.Variant.Identifier),
          /*........*/push(IdentifierNode.Variant.Builtin),
          /*..........*/content("obj", -1),
          /*........*/pop(),
          /*......*/pop(),
          /*......*/token(".", -1),
          /*......*/push(IdentifierNode.Variant.Builtin),
          /*........*/content("method", -1),
          /*......*/pop(),
          /*......*/token("(", -1),
          /*......*/token(")", -1),
          /*....*/pop(),
          /*..*/pop(),
          /**/pop());
    }
  }

  @Test
  public final void testFieldOfCast() {
    assertParsePasses(
        NodeType.Expression,
        EnumSet.of(
            NodeType.FieldAccess,
            NodeType.CastExpression,
            NodeType.Identifier,
            NodeType.ExpressionName,
            NodeType.ReferenceType
            ),
        "((Foo) foo).bar",
        push(FieldAccessNode.Variant.PrimaryDotIdentifier),
        token("(", -1),
        push(CastExpressionNode.Variant
             .LpReferenceTypeAdditionalBoundRpUnaryExpressionNotPlusMinus),
        token("(", -1),
        push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        push(IdentifierNode.Variant.Builtin),
        content("Foo", -1),
        pop(),
        pop(),
        token(")", -1),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
        pop(),
        pop(),
        pop(),
        token(")", -1),
        token(".", -1),
        push(IdentifierNode.Variant.Builtin),
        content("bar", -1),
        pop(),
        pop()
        );
  }

}
