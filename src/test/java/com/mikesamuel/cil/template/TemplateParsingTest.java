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
        "1 + (%x) + 3",
        "Expression.ConditionalExpression",
        "  AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "    AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "      ExpressionAtom.Literal",
        "        Literal.IntegerLiteral",
        "          IntegerLiteral.Builtin 1",
        "      AdditiveOperator.Pls",
        "      TemplateInterpolation.LpPctNodeTypeHintTemplateComprehensionRp",
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
        "{ %%{ ; %%} }",

        "Block.LcBlockStatementsRc",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.BlockStart",
        "  BlockStatements.BlockStatementBlockStatementBlockTypeScope",
        "    BlockStatement.Statement",
        "      Statement.EmptyStatement",
        "        EmptyStatement.Sem",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.End");
  }

  @Test
  public final void testInterpolationInExpressionStatement() {
    assertParseTree(
        PTree.complete(NodeType.BlockStatements),
        "int x; %%{ let s = \"foo\"; int (%s); %%} continue;",

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
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.BlockStart",
        "      VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
        "        VariableDeclaratorId.IdentifierDims",
        "          Identifier.Builtin s",
        "        VariableInitializer.Expression",
        "          Expression.ConditionalExpression",
        "            ExpressionAtom.Literal",
        "              Literal.StringLiteral",
        "                StringLiteral.Builtin \"foo\"",
        "  BlockStatement.LocalVariableDeclarationStatement",
        "    LocalVariableDeclarationStatement.LocalVariableDeclarationSem",
        "      LocalVariableDeclaration.Declaration",
        "        UnannType.NotAtType",
        "          Type.PrimitiveType",
        "            PrimitiveType.AnnotationNumericType",
        "              NumericType.IntegralType",
        "                IntegralType.Int",
        "        TemplateInterpolation.LpPctNodeTypeHintTemplateComprehensionRp",
        "          TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "            Expression.ConditionalExpression",
        "              Primary.Ambiguous",
        "                ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                  ContextFreeName.Name",
        "                    Identifier.Builtin s",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.End",
        "  BlockStatement.Statement",
        "    Statement.ContinueStatement",
        "      ContinueStatement.ContinueLabelSem");
  }

  @Test
  public final void testTopLevelLoop() {
    assertParseTree(
        PTree.complete(NodeType.CompilationUnit),
        "%%for (x : xs) { package foo; class (%x) {} %%}",

        "TemplatePseudoRoot.CompilationUnit",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.LoopStart",
        "      Identifier.Builtin x",
        "      Expression.ConditionalExpression",
        "        Primary.Ambiguous",
        "          ContextFreeNames.ContextFreeNameDotContextFreeName",
        "            ContextFreeName.Name",
        "              Identifier.Builtin xs",
        "  CompilationUnit.PackageDeclarationImportDeclarationTypeDeclaration",
        "    PackageDeclaration.Declaration",
        "      PackageName.IdentifierDotIdentifier",
        "        Identifier.Builtin foo",
        "    TypeDeclaration.ClassDeclaration",
        "      ClassDeclaration.NormalClassDeclaration",
        "        NormalClassDeclaration.Declaration",
        "          TemplateInterpolation.LpPctNodeTypeHintTemplateComprehensionRp",
        "            TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "              Expression.ConditionalExpression",
        "                Primary.Ambiguous",
        "                  ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                    ContextFreeName.Name",
        "                      Identifier.Builtin x",
        "          ClassBody.LcClassBodyDeclarationRc",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.End");
  }
}
