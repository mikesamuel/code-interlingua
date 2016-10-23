package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public class UnaryExpressionTest extends AbstractParSerTestCase {

  @Test
  public void testPostfix() {
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "x++",
        push(UnaryExpressionNode.Variant.UnaryExpressionNotPlusMinus),
        push(UnaryExpressionNotPlusMinusNode.Variant.PostfixExpression),
        push(PostfixExpressionNode.Variant.PostExpression),
        push(PostExpressionNode.Variant.ExpressionNameIncrDecrOperator),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        push(IncrDecrOperatorNode.Variant.PlsPls),
        token("++"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public void testPrefix() {
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "--x",
        push(UnaryExpressionNode.Variant.PreExpression),
        push(PreExpressionNode.Variant.IncrDecrOperatorUnaryExpression),
        push(IncrDecrOperatorNode.Variant.DshDsh),
        token("--"),
        pop(),
        push(UnaryExpressionNode.Variant.UnaryExpressionNotPlusMinus),
        push(UnaryExpressionNotPlusMinusNode.Variant.PostfixExpression),
        push(PostfixExpressionNode.Variant.ExpressionName),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public void testDoubleNegation() {
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "- -x",
        push(UnaryExpressionNode.Variant.DshUnaryExpression),
        token("-"),
        push(UnaryExpressionNode.Variant.DshUnaryExpression),
        token("-"),
        push(UnaryExpressionNode.Variant.UnaryExpressionNotPlusMinus),
        push(UnaryExpressionNotPlusMinusNode.Variant.PostfixExpression),
        push(PostfixExpressionNode.Variant.ExpressionName),
        push(ExpressionNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }
}
