package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

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
        push(ExpressionNode.Variant.ConditionalExpression),
        push(UnaryExpressionNode.Variant.PrefixOperatorPrimary),
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1", -1),
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
        "1 + 1 + 1",
        // AssignmentExpression as opposed to LambdaExpression
        /**/push(ExpressionNode.Variant.ConditionalExpression),
        // + is left-associative so this is (1 + 1) + 1
        /*..*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*....*/push(AdditiveExpressionNode.Variant
            .AdditiveExpressionAdditiveOperatorMultiplicativeExpression),
        /*......*/push(UnaryExpressionNode.Variant.PrefixOperatorPrimary),
        /*........*/push(PrimaryNode.Variant.ExpressionAtomPostOp),
        /*..........*/push(ExpressionAtomNode.Variant.Literal),
        /*............*/push(LiteralNode.Variant.IntegerLiteral),
        /*..............*/push(IntegerLiteralNode.Variant.Builtin),
        /*................*/content("1", -1),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/push(AdditiveOperatorNode.Variant.Pls),
        /*........*/token("+", -1),
        /*......*/pop(),
        /*......*/push(UnaryExpressionNode.Variant.PrefixOperatorPrimary),
        /*........*/push(PrimaryNode.Variant.ExpressionAtomPostOp),
        /*..........*/push(ExpressionAtomNode.Variant.Literal),
        /*............*/push(LiteralNode.Variant.IntegerLiteral),
        /*..............*/push(IntegerLiteralNode.Variant.Builtin),
        /*................*/content("1", -1),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/push(AdditiveOperatorNode.Variant.Pls),
        /*......*/token("+", -1),  // Why is this a child of multiplicative?
        /*....*/pop(),
        /*....*/push(UnaryExpressionNode.Variant.PrefixOperatorPrimary),
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
        /*..*/pop(),
        /**/pop());
  }

  @Test
  public void testSimpleFieldAccess() {
    assertParsePasses(
        PTree.complete(NodeType.Expression),
        EnumSet.of(
            NodeType.ContextFreeName,
            NodeType.Expression,
            NodeType.ExpressionAtom,
            NodeType.ExpressionName,
            NodeType.Identifier,
            NodeType.PostOp,
            NodeType.Primary),
        // This does not actually match the FieldAccess production.
        // It actually matches the ExpressionName production.
        // FieldAccess is reserved for those productions like
        // (complexExpression).field.
        "obj.field",
        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
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
        pop());
  }

  @Test
  public void testSimpleMethodCall() {
    //try (Profile p = Profile.startCounting())
    {
      assertParsePasses(
          PTree.complete(NodeType.Expression),
          EnumSet.of(
              NodeType.Expression,
              NodeType.ExpressionAtom,
              NodeType.ExpressionName,
              NodeType.MethodInvocation,
              NodeType.Identifier,
              NodeType.PostOp,
              NodeType.Primary),
          // This does not actually match the FieldAccess production.
          // It actually matches the ExpressionName production.
          // FieldAccess is reserved for those productions like
          // (complexExpression).field.
          "obj.method()",
          /**/push(ExpressionNode.Variant.ConditionalExpression),
          /*..*/push(PrimaryNode.Variant.Ambiguous),
          /*....*/push(IdentifierNode.Variant.Builtin),
          /*......*/content("obj", -1),
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
        EnumSet.of(
            NodeType.CastExpression,
            NodeType.ExpressionAtom,
            NodeType.ExpressionName,
            NodeType.MethodInvocation,
            NodeType.Identifier,
            NodeType.Primary,
            NodeType.PostOp,
            NodeType.ReferenceType
            ),
        "((Foo) foo).bar",
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Parenthesized),
        token("(", -1),
        push(CastExpressionNode.Variant.Reference),
        token("(", -1),
        push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        push(IdentifierNode.Variant.Builtin),
        content("Foo", -1),
        pop(),
        pop(),
        token(")", -1),
        push(PrimaryNode.Variant.Ambiguous),
        push(IdentifierNode.Variant.Builtin),
        content("foo", -1),
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
        pop()
        );
  }

}
