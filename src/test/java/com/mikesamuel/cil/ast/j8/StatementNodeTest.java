package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.event.Event;

@SuppressWarnings("javadoc")
public final class StatementNodeTest extends AbstractParSerTestCase {

  private static Event token(String s) {
    return Event.token(s, -1);
  }

  private static Event content(String s) {
    return Event.content(s, -1);
  }

  @Test
  public void testIfThen() {
    assertParsePasses(
        J8NodeType.Statement,
        "if (true) return;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementNotElse),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true"),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.ReturnStatement),
        push(ReturnStatementNode.Variant.ReturnExpressionSem),
        token("return"),
        token(";"),
        pop(),
        pop(),
        pop(),

        pop()
        );
  }

  @Test
  public void testIfThenElse() {
    assertParsePasses(
        J8NodeType.Statement,
        "if (true) return; else break;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true"),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.ReturnStatement),
        push(ReturnStatementNode.Variant.ReturnExpressionSem),
        token("return"),
        token(";"),
        pop(),
        pop(),

        token("else"),

        push(StatementNode.Variant.BreakStatement),
        push(BreakStatementNode.Variant.BreakLabelSem),
        token("break"),
        token(";"),
        pop(),
        pop(),
        pop(),

        pop()
        );
  }

  @Test
  public void testNestedIf() {
    assertParsePasses(
        J8NodeType.Statement,
        "if (x) if (y) return; else break;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementNotElse),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("y"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.ReturnStatement),
        push(ReturnStatementNode.Variant.ReturnExpressionSem),
        token("return"),
        token(";"),
        pop(),
        pop(),

        token("else"),

        push(StatementNode.Variant.BreakStatement),
        push(BreakStatementNode.Variant.BreakLabelSem),
        token("break"),
        token(";"),
        pop(),
        pop(),

        pop(),
        pop(),

        pop(),
        pop());
  }

  @Test
  public void testNestedIfThroughWhile() {
    assertParsePasses(
        J8NodeType.Statement,
        "if (x) while (y) if (z()) return; else break;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementNotElse),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("x"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.WhileStatement),
        push(WhileStatementNode.Variant.WhileLpExpressionRpStatement),

        token("while"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("y"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(ExpressionAtomNode.Variant.MethodInvocation),
        push(MethodNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("z"),
        pop(),
        pop(),
        token("("),
        token(")"),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.ReturnStatement),
        push(ReturnStatementNode.Variant.ReturnExpressionSem),
        token("return"),
        token(";"),
        pop(),
        pop(),

        token("else"),

        push(StatementNode.Variant.BreakStatement),
        push(BreakStatementNode.Variant.BreakLabelSem),
        token("break"),
        token(";"),
        pop(),
        pop(),

        pop(),
        pop(),

        pop(),
        pop(),

        pop(),
        pop());
  }

  @Test
  public void testLabeledLoop() {
    assertParsePasses(
        J8NodeType.Statement,
        "label: do if (c) break label; while(m());",
        push(StatementNode.Variant.LabeledStatement),
        push(LabeledStatementNode.Variant.LabelClnStatement),

        push(LabelNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("label"),
        pop(),
        pop(),

        token(":"),

        push(StatementNode.Variant.DoStatement),
        push(DoStatementNode.Variant.DoStatementWhileLpExpressionRpSem),

        token("do"),

        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementNotElse),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("c"),
        pop(),
        pop(),
        pop(),
        pop(),
        pop(),

        token(")"),

        push(StatementNode.Variant.BreakStatement),
        push(BreakStatementNode.Variant.BreakLabelSem),

        token("break"),

        push(LabelNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("label"),
        pop(),
        pop(),

        token(";"),

        pop(),
        pop(),

        pop(),
        pop(),

        token("while"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(ExpressionAtomNode.Variant.MethodInvocation),

        push(MethodNameNode.Variant.Identifier),
        push(IdentifierNode.Variant.Builtin),
        content("m"),
        pop(),
        pop(),

        token("("),
        token(")"),

        pop(),
        pop(),

        token(")"),
        token(";"),

        pop(),
        pop(),

        pop(),
        pop());

  }
}
