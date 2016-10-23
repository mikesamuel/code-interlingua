package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.NodeType;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class GrowTheSeedTest extends TestCase {

  @Test
  public static void testAdditiveExpression() {
    GrowTheSeed g = GrowTheSeed.of(NodeType.AdditiveExpression);
    assertEquals(
        "(append (push AdditiveExpression.MultiplicativeExpression))"
        + " MultiplicativeExpression"
        + " (append pop)", g.seed.toString());
    assertEquals(
        "(append (LRSuffix ["
        + AdditiveExpressionNode.Variant
          .AdditiveExpressionAdditiveOperatorMultiplicativeExpression + "]))"
        + " AdditiveOperator MultiplicativeExpression",
        g.suffix.toString());
  }
}
