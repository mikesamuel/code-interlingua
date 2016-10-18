package com.mikesamuel.cil.ast;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class ExpressionNodeTest extends AbstractParSerTestCase {

  @Test
  public void testIntLit() {
    assertParsePasses(
        NodeType.Expression,
        "1",
        MatchEvent.push(ExpressionNode.Variant.AssignmentExpression),
        MatchEvent.pop());
  }

  @Test
  public void testAdd3() {
    assertParsePasses(
        NodeType.Expression,
        "1 + 1 + 1",
        MatchEvent.push(ExpressionNode.Variant.AssignmentExpression),
        MatchEvent.pop());
  }

}
