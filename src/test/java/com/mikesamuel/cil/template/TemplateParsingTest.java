package com.mikesamuel.cil.template;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class TemplateParsingTest extends AbstractParSerTestCase {

  @Override
  protected Input input(String content) {
    return Input.builder()
        .source(getName())
        .code(content)
        .allowNonStandardProductions(true)
        .build();
  }

  @Test
  public final void testInterpolationInExpressionContext() {
    assertParseTree(
        PTree.complete(NodeType.Expression),
        "1 + <% x %> + 3",
        "Expression.ConditionalExpression",
        "  AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "    AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "      ExpressionAtom.Literal",
        "        Literal.IntegerLiteral",
        "          IntegerLiteral.Builtin 1",
        "      AdditiveOperator.Pls",
        "      TemplateInterpolation.Interpolation",
        "        TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "          Expression.ConditionalExpression",
        "            Primary.Ambiguous",
        "              ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                ContextFreeName.Name",
        "                  Identifier.Builtin x",
        "    AdditiveOperator.Pls",
        "    ExpressionAtom.Literal",
        "      Literal.IntegerLiteral",
        "        IntegerLiteral.Builtin 3");
  }

  @Test
  public final void testDirectiveBlock() {
    assertParseTree(
        PTree.complete(NodeType.Block),
        "{ <% { %> ; <% } %> }",

        "Block.LcBlockStatementsRc",
        "  TemplateDirectives.TemplateDirectives",
        "    TemplateDirective.BlockStart",
        "  BlockStatements.BlockStatementBlockStatementBlockTypeScope",
        "    BlockStatement.Statement",
        "      Statement.EmptyStatement",
        "        EmptyStatement.Sem",
        "  TemplateDirectives.TemplateDirectives",
        "    TemplateDirective.End");
  }

  @Test
  public final void testInterpolationInExpressionStatement() {
    assertParseTree(
        PTree.complete(NodeType.BlockStatements),
        "int x; <% { %><% let s = \"foo\"; %> int <% s %>; <% } %> continue;",

        "BlockStatements.BlockStatementBlockStatementBlockTypeScope",
        "  BlockStatement.LocalVariableDeclarationStatement",
        "    LocalVariableDeclarationStatement.LocalVariableDeclarationSem",
        "      LocalVariableDeclaration.Declaration",
        "        UnannType.NotAtType",
        "          Type.PrimitiveType",
        "            PrimitiveType.AnnotationNumericType",
        "              NumericType.IntegralType",
        "                IntegralType.Int",
        "        VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
        "          VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
        "            VariableDeclaratorId.IdentifierDims",
        "              Identifier.Builtin x",
        "  TemplateDirectives.TemplateDirectives",
        "    TemplateDirective.BlockStart",
        "    TemplateDirective.Vars",
        "      Identifier.Builtin s",
        "      Expression.ConditionalExpression",
        "        ExpressionAtom.Literal",
        "          Literal.StringLiteral",
        "            StringLiteral.Builtin \"foo\"",
        "  BlockStatement.LocalVariableDeclarationStatement",
        "    LocalVariableDeclarationStatement.LocalVariableDeclarationSem",
        "      LocalVariableDeclaration.Declaration",
        "        UnannType.NotAtType",
        "          Type.PrimitiveType",
        "            PrimitiveType.AnnotationNumericType",
        "              NumericType.IntegralType",
        "                IntegralType.Int",
        "        TemplateInterpolation.Interpolation",
        "          TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "            Expression.ConditionalExpression",
        "              Primary.Ambiguous",
        "                ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                  ContextFreeName.Name",
        "                    Identifier.Builtin s",
        "  TemplateDirectives.TemplateDirectives",
        "    TemplateDirective.End",
        "  BlockStatement.Statement",
        "    Statement.ContinueStatement",
        "      ContinueStatement.ContinueLabelSem");
  }
}
