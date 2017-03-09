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
        "      TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
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
  public final void testInterpolationInExpressionContextWithExplicitTypeHint() {
    assertParseTree(
        PTree.complete(NodeType.Expression),
        "1 + (%x : MultiplicativeExpression)",
        "Expression.ConditionalExpression",
        "  AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "    ExpressionAtom.Literal",
        "      Literal.IntegerLiteral",
        "        IntegerLiteral.Builtin 1",
        "    AdditiveOperator.Pls",
        "    TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
        "      TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "        Expression.ConditionalExpression",
        "          Primary.Ambiguous",
        "            ContextFreeNames.ContextFreeNameDotContextFreeName",
        "              ContextFreeName.Name",
        "                Identifier.Builtin x",
        "      NodeTypeHint.ClnIdentifier",
        "        Identifier.Builtin MultiplicativeExpression");
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
        "      TemplateLocal.NotIdentifierDimsVariableDeclarator",
        "        VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
        "          VariableDeclaratorId.IdentifierDims",
        "            Identifier.Builtin s",
        "          VariableInitializer.Expression",
        "            Expression.ConditionalExpression",
        "              ExpressionAtom.Literal",
        "                Literal.StringLiteral",
        "                  StringLiteral.Builtin \"foo\"",
        "  BlockStatement.LocalVariableDeclarationStatement",
        "    LocalVariableDeclarationStatement.LocalVariableDeclarationSem",
        "      LocalVariableDeclaration.Declaration",
        "        UnannType.NotAtType",
        "          Type.PrimitiveType",
        "            PrimitiveType.AnnotationNumericType",
        "              NumericType.IntegralType",
        "                IntegralType.Int",
        "        TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
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
        "          TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
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

  @Test
  public final void testTemplateDecls() {
    assertParseTree(
        PTree.complete(NodeType.CompilationUnit),
        ""
        + "package p;\n"
        + "%%template foo(a, b, c) : Expression { (%a) + (%b) * (%c) }",

        "TemplatePseudoRoot.CompilationUnit",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.Function",
        "      TemplateHeader.Declaration",
        "        Identifier.Builtin foo",
        "        TemplateFormals.LocalNameComLocalName",
        "          LocalName.Identifier",
        "            Identifier.Builtin a",
        "          LocalName.Identifier",
        "            Identifier.Builtin b",
        "          LocalName.Identifier",
        "            Identifier.Builtin c",
        "        NodeTypeHint.ClnIdentifier",
        "          Identifier.Builtin Expression",
        "      TemplateBody.Any",
        "        Expression.ConditionalExpression",
        "          AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
        "            TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
        "              TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "                Expression.ConditionalExpression",
        "                  Primary.Ambiguous",
        "                    ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                      ContextFreeName.Name",
        "                        Identifier.Builtin a",
        "            AdditiveOperator.Pls",
        "            MultiplicativeExpression.MultiplicativeExpressionMultiplicativeOperatorUnaryExpression",
        "              TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
        "                TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "                  Expression.ConditionalExpression",
        "                    Primary.Ambiguous",
        "                      ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                        ContextFreeName.Name",
        "                          Identifier.Builtin b",
        "              MultiplicativeOperator.Str",
        "              TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
        "                TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "                  Expression.ConditionalExpression",
        "                    Primary.Ambiguous",
        "                      ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                        ContextFreeName.Name",
        "                          Identifier.Builtin c",
        "  CompilationUnit.PackageDeclarationImportDeclarationTypeDeclaration",
        "    PackageDeclaration.Declaration",
        "      PackageName.IdentifierDotIdentifier",
        "        Identifier.Builtin p");
  }

  @Test
  public final void testTemplateBodyIsSingleInterpolation() {
    assertParseTree(
        PTree.complete(NodeType.CompilationUnit),
        ""
        + "package p;\n"
        // Don't generalize the interpolation to replace the body.  Instead
        // treat the interpolation as the body.
        + "%%template foo(a, i) : Expression { (% a[i]) }",

        "TemplatePseudoRoot.CompilationUnit",
        "  TemplateDirectives.TemplateDirectiveTemplateDirective",
        "    TemplateDirective.Function",
        "      TemplateHeader.Declaration",
        "        Identifier.Builtin foo",
        "        TemplateFormals.LocalNameComLocalName",
        "          LocalName.Identifier",
        "            Identifier.Builtin a",
        "          LocalName.Identifier",
        "            Identifier.Builtin i",
        "        NodeTypeHint.ClnIdentifier",
        "          Identifier.Builtin Expression",
        "      TemplateBody.Any",
        "        TemplateInterpolation.LpPctTemplateComprehensionNodeTypeHintRp",
        "          TemplateComprehension.ExpressionComExpressionTemplateLoopTemplateCondition",
        "            Expression.ConditionalExpression",
        "              Primary.ArrayAccess",
        "                Primary.Ambiguous",
        "                  ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                    ContextFreeName.Name",
        "                      Identifier.Builtin a",
        "                Expression.ConditionalExpression",
        "                  Primary.Ambiguous",
        "                    ContextFreeNames.ContextFreeNameDotContextFreeName",
        "                      ContextFreeName.Name",
        "                        Identifier.Builtin i",
        "  CompilationUnit.PackageDeclarationImportDeclarationTypeDeclaration",
        "    PackageDeclaration.Declaration",
        "      PackageName.IdentifierDotIdentifier",
        "        Identifier.Builtin p");
  }
}
