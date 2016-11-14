package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class ExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void testIntLit() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        "1",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1", -1),
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
        "1 + 1 + 1",
        // AssignmentExpression as opposed to LambdaExpression
        /**/push(ExpressionNode.Variant.ConditionalExpression),
        // + is left-associative so this is (1 + 1) + 1
        /*..*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*....*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*......*/push(PrimaryNode.Variant.ExpressionAtomPostOp),
        /*........*/push(ExpressionAtomNode.Variant.Literal),
        /*..........*/push(LiteralNode.Variant.IntegerLiteral),
        /*............*/push(IntegerLiteralNode.Variant.Builtin),
        /*..............*/content("1", -1),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/push(AdditiveOperatorNode.Variant.Pls),
        /*........*/token("+", -1),
        /*......*/pop(),
        /*......*/push(PrimaryNode.Variant.ExpressionAtomPostOp),
        /*........*/push(ExpressionAtomNode.Variant.Literal),
        /*..........*/push(LiteralNode.Variant.IntegerLiteral),
        /*............*/push(IntegerLiteralNode.Variant.Builtin),
        /*..............*/content("1", -1),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/push(AdditiveOperatorNode.Variant.Pls),
        /*......*/token("+", -1),  // Why is this a child of multiplicative?
        /*....*/pop(),
        /*....*/push(PrimaryNode.Variant.ExpressionAtomPostOp),
        /*......*/push(ExpressionAtomNode.Variant.Literal),
        /*........*/push(LiteralNode.Variant.IntegerLiteral),
        /*..........*/push(IntegerLiteralNode.Variant.Builtin),
        /*............*/content("1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop());
  }

  @Test
  public void testSimpleFieldAccess() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        // This does not actually match the FieldAccess production.
        // It actually matches the ExpressionName production.
        // FieldAccess is reserved for those productions like
        // (complexExpression).field.
        "obj.field",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("obj", -1),
        pop(),
        pop(),
        token(".", -1),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("field", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testSimpleMethodCall() {
    //try (Profile p = Profile.startCounting())
    {
      assertParsePasses(
          PTree.complete(NodeType.Expression),
          // This does not actually match the FieldAccess production.
          // It actually matches the ExpressionName production.
          // FieldAccess is reserved for those productions like
          // (complexExpression).field.
          "obj.method()",
          /**/push(ExpressionNode.Variant.ConditionalExpression),
          /*..*/push(PrimaryNode.Variant.Ambiguous),
          /*....*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
          /*......*/push(ContextFreeNameNode.Variant.Name),
          /*........*/push(IdentifierNode.Variant.Builtin),
          /*..........*/content("obj", -1),
          /*........*/pop(),
          /*......*/pop(),
          /*....*/pop(),
          /*....*/push(PostOpNode.Variant.MethodInvocation),
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
        "((Foo) foo).bar",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Parenthesized),
        token("(", -1),
        push(ExpressionNode.Variant.ConditionalExpression),
        push(UnaryExpressionNode.Variant.CastExpression),
        push(CastExpressionNode.Variant.Reference),
        token("(", -1),
        push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        push(ClassOrInterfaceTypeNode.Variant.ContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("Foo", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        token(")", -1),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        token(")", -1),
        pop(),
        push(PostOpNode.Variant.FieldAccess),
        token(".", -1),
        push(IdentifierNode.Variant.Builtin),
        content("bar", -1),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

}
