package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.AdditiveExpressionNode;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ClassTypeNode;
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
        + " (append pop)",
        g.seed.toString());
    assertEquals(
        "(append (LRSuffix ["
        + AdditiveExpressionNode.Variant
          .AdditiveExpressionAdditiveOperatorMultiplicativeExpression + "]))"
        + " AdditiveOperator MultiplicativeExpression",
        g.suffix.toString());
  }

  @Test
  public static void testClassType() {
    GrowTheSeed g = GrowTheSeed.of(NodeType.ClassType);
    assertEquals(
        "(append (LRSuffix "
        + ImmutableList.of(
            ClassTypeNode.Variant
            .ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments,
            ClassOrInterfaceTypeNode.Variant.ClassType)
        + "))"
        + " (\".\" {Annotation} Identifier) [TypeArguments]",
        g.suffix.toString());
    assertEquals(
        "(append (push ClassType.AnnotationIdentifierTypeArguments))"
        + " [Annotation {Annotation}]"  // Equivalent to {Annoatation}
        + " Identifier [TypeArguments]"
        + " (append pop)",
        g.seed.toString());
  }
}
