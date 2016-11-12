package com.mikesamuel.cil.ast;

import org.junit.Test;

@SuppressWarnings("javadoc")
public final class MiscParseTest extends AbstractParSerTestCase {

  @Test
  public final void testFormalParameterList() {
    parseSanityCheck(
        FormalParameterListNode.Variant.FormalParametersComLastFormalParameter,
        "String name, Class<? extends Enum<? extends ParSerable>> variantClass"
        );
  }

  @Test
  public final void testFieldOfThis() {
    parseSanityCheck(
        ExpressionNode.Variant.ConditionalExpression,
        "this.field"
        );
  }

  @Test
  public final void testMethodCallViaThis() {
    parseSanityCheck(
        ExpressionNode.Variant.ConditionalExpression,
        "this.op()"
        );
  }

  @Test
  public final void testMethodCallViaThisAsStatementExpression() {
    parseSanityCheck(
        StatementExpressionNode.Variant.MethodInvocation,
        "this.op()"
        );
  }
}
