package com.mikesamuel.cil.ast;

import static com.mikesamuel.cil.event.MatchEvent.content;
import static com.mikesamuel.cil.event.MatchEvent.pop;
import static com.mikesamuel.cil.event.MatchEvent.push;
import static com.mikesamuel.cil.event.MatchEvent.token;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public class UnaryExpressionTest extends AbstractParSerTestCase {

  @Test
  public void testPostfix() {
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "x++",
        push(UnaryExpressionNode.Variant.PostExpression),
        push(PostExpressionNode.Variant.LeftHandSideExpressionIncrDecrOperator),
        push(LeftHandSideNode.Variant.Ambiguous),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("x", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        push(IncrDecrOperatorNode.Variant.PlsPls),
        token("++", -1),
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
        push(PreExpressionNode.Variant.IncrDecrOperatorLeftHandSideExpression),
        push(IncrDecrOperatorNode.Variant.DshDsh),
        token("--", -1),
        pop(),
        push(LeftHandSideNode.Variant.Ambiguous),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("x", -1),
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
        push(UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression),
        push(PrefixOperatorNode.Variant.Dsh),
        token("-", -1),
        pop(),
        push(UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression),
        push(PrefixOperatorNode.Variant.Dsh),
        token("-", -1),
        pop(),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("x", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),
        pop()
        );
  }
}
