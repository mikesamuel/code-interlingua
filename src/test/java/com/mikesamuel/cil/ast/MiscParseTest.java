package com.mikesamuel.cil.ast;

import static com.mikesamuel.cil.event.MatchEvent.content;
import static com.mikesamuel.cil.event.MatchEvent.ignorable;
import static com.mikesamuel.cil.event.MatchEvent.pop;
import static com.mikesamuel.cil.event.MatchEvent.push;
import static com.mikesamuel.cil.event.MatchEvent.token;

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

  @Test
  public final void testJavaDocCommentsPreserved() {
    assertParsePasses(
        NodeType.ClassDeclaration,
        ""
        + "/** Comment */\n"
        + "public final class C {}",
        push(ClassDeclarationNode.Variant.NormalClassDeclaration),
        push(NormalClassDeclarationNode.Variant.Declaration),
        push(JavaDocCommentNode.Variant.Builtin),
        ignorable("/** Comment */", 0),
        pop(),
        push(ClassModifierNode.Variant.Public),
        token("public", -1),
        pop(),
        push(ClassModifierNode.Variant.Final),
        token("final", -1),
        pop(),
        token("class", -1),
        push(SimpleTypeNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("C", -1),
        pop(),
        pop(),
        push(ClassBodyNode.Variant.LcClassBodyDeclarationRc),
        token("{", -1),
        token("}", -1),
        pop(),
        pop(),
        pop());
  }
}
