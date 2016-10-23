package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PostfixExpressionNode;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class ReferenceTest extends TestCase {

  @Test
  public static void testLa() {
    PTParSer postExpr = (PTParSer)
        PostfixExpressionNode.Variant.PostExpression.getParSer();
    RangeSet<Integer> postLa1 = postExpr.getLookahead1();
    if (postLa1 != null) {
      assertTrue(postLa1.contains((int) 'a'));
      assertTrue(postLa1.contains((int) '('));
      assertTrue(postLa1.contains((int) '"'));
      assertTrue(postLa1.contains((int) '.'));
    }

    PTParSer exprName = (PTParSer)
        PostfixExpressionNode.Variant.ExpressionName.getParSer();
    RangeSet<Integer> exprNameLa1 = exprName.getLookahead1();
    if (exprNameLa1 != null) {
      assertTrue(exprNameLa1.contains((int) 'a'));
      assertTrue(exprNameLa1.contains((int) 'z'));
    }

    PTParSer primary = (PTParSer)
        PostfixExpressionNode.Variant.Primary.getParSer();
    RangeSet<Integer> primaryLa1 = primary.getLookahead1();
    if (primaryLa1 != null) {
      // TODO
    }

    PTParSer postfix = (PTParSer) NodeType.PostfixExpression.getParSer();
    RangeSet<Integer> postfixLa1 = postfix.getLookahead1();
    if (postfixLa1 != null) {
      // TODO
    }
  }

}
