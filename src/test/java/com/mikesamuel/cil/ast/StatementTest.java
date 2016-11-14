package com.mikesamuel.cil.ast;

import org.junit.Test;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;

@SuppressWarnings("javadoc")
public final class StatementTest extends AbstractParSerTestCase {

  private static MatchEvent token(String s) {
    return MatchEvent.token(s, -1);
  }

  private static MatchEvent content(String s) {
    return MatchEvent.content(s, -1);
  }

  @Test
  public void testIfThen() {
    assertParsePasses(
        NodeType.Statement,
        "if (true) return;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementNotElse),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true"),
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
        pop(),

        pop()
        );
  }

  @Test
  public void testIfThenElse() {
    assertParsePasses(
        NodeType.Statement,
        "if (true) return; else break;",
        push(StatementNode.Variant.IfStatement),
        push(IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement),

        token("if"),
        token("("),

        push(ExpressionNode.Variant.ConditionalExpression),
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true"),
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
        push(BreakStatementNode.Variant.BreakIdentifierSem),
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
        NodeType.Statement,
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
        push(BreakStatementNode.Variant.BreakIdentifierSem),
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
        NodeType.Statement,
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
        push(PrimaryNode.Variant.ExpressionAtomPostOp),
        push(ExpressionAtomNode.Variant.MethodInvocation),
        push(IdentifierNode.Variant.Builtin),
        content("z"),
        pop(),
        token("("),
        token(")"),
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
        push(BreakStatementNode.Variant.BreakIdentifierSem),
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
}
