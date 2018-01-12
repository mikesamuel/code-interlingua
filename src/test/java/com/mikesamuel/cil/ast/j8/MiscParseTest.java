package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.ignorable;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;

@SuppressWarnings("javadoc")
public final class MiscParseTest extends AbstractParSerTestCase {

  @Test
  public final void testFormalParameter() {
    parseSanityCheck(
        FormalParameterNode.Variant.Declaration,
        "String name"
        );
  }

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
        J8NodeType.ClassDeclaration,
        ""
        + "/** Comment */\n"
        + "public final class C {}",
        push(ClassDeclarationNode.Variant.NormalClassDeclaration),
        push(NormalClassDeclarationNode.Variant.Declaration),
        push(JavaDocCommentNode.Variant.Builtin),
        ignorable("/** Comment */", 0),
        pop(),
        push(ModifierNode.Variant.Public),
        token("public", -1),
        pop(),
        push(ModifierNode.Variant.Final),
        token("final", -1),
        pop(),
        token("class", -1),
        push(SimpleTypeNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("C", -1),
        pop(),
        pop(),
        push(ClassBodyNode.Variant.LcClassMemberDeclarationRc),
        token("{", -1),
        token("}", -1),
        pop(),
        pop(),
        pop());
  }
}
