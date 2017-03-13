package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;

@SuppressWarnings("javadoc")
public final class ExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void testIntLit() {
    assertParsePasses(
        J8NodeType.Expression,
        "1",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }

  @Test
  public void testAdd3() {
    assertParsePasses(
        J8NodeType.Expression,
        "1 + 1 + 1",
        // AssignmentExpression as opposed to LambdaExpression
        /**/push(ExpressionNode.Variant.ConditionalExpression),
        // + is left-associative so this is (1 + 1) + 1
        /*..*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*....*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*......*/push(ExpressionAtomNode.Variant.Literal),
        /*........*/push(LiteralNode.Variant.IntegerLiteral),
        /*..........*/push(IntegerLiteralNode.Variant.Builtin),
        /*............*/content("1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/push(AdditiveOperatorNode.Variant.Pls),
        /*........*/token("+", -1),
        /*......*/pop(),
        /*......*/push(ExpressionAtomNode.Variant.Literal),
        /*........*/push(LiteralNode.Variant.IntegerLiteral),
        /*..........*/push(IntegerLiteralNode.Variant.Builtin),
        /*............*/content("1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/push(AdditiveOperatorNode.Variant.Pls),
        /*......*/token("+", -1),  // Why is this a child of multiplicative?
        /*....*/pop(),
        /*....*/push(ExpressionAtomNode.Variant.Literal),
        /*......*/push(LiteralNode.Variant.IntegerLiteral),
        /*........*/push(IntegerLiteralNode.Variant.Builtin),
        /*..........*/content("1", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop());
  }

  @Test
  public void testSimpleFieldAccess() {
    assertParsePasses(
        J8NodeType.Expression,
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
          J8NodeType.Expression,
          // This does not actually match the FieldAccess production.
          // It actually matches the ExpressionName production.
          // FieldAccess is reserved for those productions like
          // (complexExpression).field.
          "obj.method()",
          /**/push(ExpressionNode.Variant.ConditionalExpression),
          /*..*/push(PrimaryNode.Variant.MethodInvocation),
          /*....*/push(PrimaryNode.Variant.Ambiguous),
          /*......*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
          /*........*/push(ContextFreeNameNode.Variant.Name),
          /*..........*/push(IdentifierNode.Variant.Builtin),
          /*............*/content("obj", -1),
          /*..........*/pop(),
          /*........*/pop(),
          /*......*/pop(),
          /*....*/pop(),
          /*....*/token(".", -1),
          /*....*/push(MethodNameNode.Variant.Identifier),
          /*......*/push(IdentifierNode.Variant.Builtin),
          /*........*/content("method", -1),
          /*......*/pop(),
          /*....*/pop(),
          /*....*/token("(", -1),
          /*....*/token(")", -1),
          /*..*/pop(),
          /**/pop());
    }
  }

  @Test
  public final void testFieldOfCast() {
    assertParsePasses(
        J8NodeType.Expression,
        "((Foo) foo).bar",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.FieldAccess),
        push(ExpressionAtomNode.Variant.Parenthesized),
        token("(", -1),
        push(ExpressionNode.Variant.ConditionalExpression),
        push(UnaryExpressionNode.Variant.CastExpression),
        push(CastExpressionNode.Variant.Expression),
        token("(", -1),
        push(ConfirmCastNode.Variant.ReferenceTypeAdditionalBound),
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
        token(".", -1),
        push(FieldNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("bar", -1),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }


  @Test
  public final void testCallIndirectlyViaThis() {
    assertParsePasses(
        J8NodeType.Expression,
        "this.field.method(arg)",
        /**/push(ExpressionNode.Variant.ConditionalExpression),
        /*..*/push(PrimaryNode.Variant.MethodInvocation),
        /*....*/push(PrimaryNode.Variant.FieldAccess),
        /*......*/push(ExpressionAtomNode.Variant.This),
        /*........*/token("this", -1),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(FieldNameNode.Variant.Identifier),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("field", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(MethodNameNode.Variant.Identifier),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("method", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token("(", -1),
        /*....*/push(ArgumentListNode.Variant.ExpressionComExpression),
        /*......*/push(ExpressionNode.Variant.ConditionalExpression),
        /*........*/push(PrimaryNode.Variant.Ambiguous),
        /*..........*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*............*/push(ContextFreeNameNode.Variant.Name),
        /*..............*/push(IdentifierNode.Variant.Builtin),
        /*................*/content("arg", -1),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(")", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

}
