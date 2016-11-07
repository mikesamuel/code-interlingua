package com.mikesamuel.cil.ast;

import org.junit.Test;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public class PostExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void test() {
    this.assertParsePasses(
        NodeType.PostExpression,
        "x++",
        push(PostExpressionNode.Variant.ExpressionNameIncrDecrOperator),
        push(ExpressionNameNode.Variant.NotAtContextFreeNames),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.AnnotationIdentifierTypeArgumentsOrDiamond),
        push(IdentifierNode.Variant.Builtin),
        content("x", -1),
        pop(),
        pop(),
        pop(),
        pop(),
        push(IncrDecrOperatorNode.Variant.PlsPls),
        token("++", -1),
        pop(),
        pop()
        );
  }
}
