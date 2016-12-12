package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.traits.NamePart;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeReference;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DisambiguationPassTest extends TestCase {

  @Test
  public static void testEmptyCompilationUnit() {
    assertDisambiguated(
        new String[] {
          "CompilationUnit.PackageDeclarationImportDeclarationTypeDeclaration",
        },
        new String[] {},
        Functions.identity(),
        false,
        null);
  }

  @Test
  public static void testExpressionName() {
    assertDisambiguated(
        new String[] {
            "Expression.ConditionalExpression",
            "  Primary.MethodInvocation",
            "    Primary.FieldAccess",
            "      ExpressionAtom.StaticMember",
            "        TypeName.Identifier",
            "          Identifier.Builtin Math",
            "      FieldName.Identifier",
            "        Identifier.Builtin PI",
            "    MethodName.Identifier",
            "      Identifier.Builtin hashCode",
        },
        new String[] {
            "//ExpressionName",
            "class C {",
            "  int x = Math.PI.hashCode();",
            "}"
        },
        nthWithNodeType(0, NodeType.Expression),
        false,
        null);
  }

  @Test
  public static void testFieldScopes() {
    assertDisambiguated(
        new String[] {
            "InstanceInitializer.Block",
            "  Block.LcBlockStatementsRc",
            "    BlockStatements.BlockStatementBlockStatementBlockTypeScope",
            "      BlockStatement.Statement",
            "        Statement.ExpressionStatement",
            "          ExpressionStatement.StatementExpressionSem",
            "            StatementExpression.MethodInvocation",
            "              MethodInvocation.ImplicitCallee",
            "                ExpressionAtom.MethodInvocation",
            "                  MethodName.Identifier : /java/lang/System.err.println",
            "                    Identifier.Builtin println",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      Primary.Field",
            "                        Identifier.Builtin i : /C.i",
            "      BlockStatement.LocalVariableDeclarationStatement",
            "        LocalVariableDeclarationStatement.LocalVariableDeclarationSem",
            "          LocalVariableDeclaration.Declaration",
            "            UnannType.NotAtType",
            "              Type.PrimitiveType",
            "                PrimitiveType.AnnotationNumericType",
            "                  NumericType.IntegralType",
            "                    IntegralType.Int",
            "            VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
            "              VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
            "                VariableDeclaratorId.IdentifierDims",
            "                  Identifier.Builtin i",
            "                VariableInitializer.Expression",
            "                  Expression.ConditionalExpression",
            "                    ExpressionAtom.Literal",
            "                      Literal.IntegerLiteral",
            "                        IntegerLiteral.Builtin 1",
            "      BlockStatement.Statement",
            "        Statement.ExpressionStatement",
            "          ExpressionStatement.StatementExpressionSem",
            "            StatementExpression.MethodInvocation",
            "              MethodInvocation.ImplicitCallee",
            "                ExpressionAtom.MethodInvocation",
            "                  MethodName.Identifier : /java/lang/System.err.println",
            "                    Identifier.Builtin println",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      Primary.Local : i",
            "                        Identifier.Builtin i",
        },
        new String[] {
            "//testFieldScopes",
            "import static java.lang.System.err.println;",
            "class C {",
            "  int i;",
            "  {",
            "    println(i);",  // Field
            "    int i = 1;",
            "    println(i);",  // Local
            "  }",
            "}"
        },
        nthWithNodeType(0, NodeType.InstanceInitializer),
        false,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testClassOrInterfaceType() {
    assertDisambiguated(
        new String[] {
            "FieldDeclaration.Declaration",
            "  UnannType.NotAtType",
            "    Type.ReferenceType",
            "      ReferenceType.ClassOrInterfaceType",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Object",
            "          Identifier.Builtin Object",
            "  VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
            "    VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
            "      VariableDeclaratorId.IdentifierDims",
            "        Identifier.Builtin x",
            "      VariableInitializer.Expression",
            "        Expression.ConditionalExpression",
            "          ExpressionAtom.UnqualifiedClassInstanceCreationExpression",
            "            UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody : /C$1",
            "              ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
            "                ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Object",
            "                  Identifier.Builtin Object",
            "              ClassBody.LcClassBodyDeclarationRc",
        },
        new String[] {
            "class C {",
            "  Object x = new Object() {};",
            "}"
        },

        nthWithNodeType(0, NodeType.FieldDeclaration),
        false,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testClassOrInterfaceTypeFull() {
    assertDisambiguated(
        new String[] {
            "FieldDeclaration.Declaration",
            "  UnannType.NotAtType",
            "    Type.ReferenceType",
            "      ReferenceType.ClassOrInterfaceType",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Object",
            "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "            ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "              Identifier.Builtin java",
            "            Identifier.Builtin lang",
            "          Identifier.Builtin Object",
            "  VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
            "    VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
            "      VariableDeclaratorId.IdentifierDims",
            "        Identifier.Builtin x",
            "      VariableInitializer.Expression",
            "        Expression.ConditionalExpression",
            "          ExpressionAtom.UnqualifiedClassInstanceCreationExpression",
            "            UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody : /C$1",
            "              ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
            "                ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Object",
            "                  ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "                    ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "                      Identifier.Builtin java",
            "                    Identifier.Builtin lang",
            "                  Identifier.Builtin Object",
            "              ClassBody.LcClassBodyDeclarationRc",
        },
        new String[] {
            "class C {",
            "  Object x = new Object() {};",
            "}"
        },

        nthWithNodeType(0, NodeType.FieldDeclaration),
        true,
        TYPE_AND_NAME_DECORATOR);
  }


  static void assertDisambiguated(
      String[] want,
      String[] input,
      Function<BaseNode, BaseNode> nodeFinder,
      boolean useLongNames,
      Function<? super BaseNode, ? extends String> decorator) {
    Logger logger = Logger.getAnonymousLogger();
    ImmutableList<CompilationUnitNode> cuNodes =
        ClassNamingPassTest.parseCompilationUnits(
            new String[][] { input });
    DeclarationPass declarationPass = new DeclarationPass(logger);
    TypeInfoResolver typeInfoResolver = declarationPass.run(cuNodes);
    ImmutableList<CompilationUnitNode> disambiguated =
        new DisambiguationPass(typeInfoResolver, logger, useLongNames)
        .run(cuNodes);
    CompilationUnitNode cuFinal = Iterables.getOnlyElement(disambiguated);
    BaseNode ofInterest = nodeFinder.apply(cuFinal);
    Preconditions.checkNotNull(ofInterest, cuFinal);
    assertEquals(
        Joiner.on('\n').join(want),
        ofInterest.toAsciiArt(
            "",
            decorator != null ? decorator : Functions.constant(null)));
  }


  static Function<BaseNode, BaseNode> withContent(String... content) {
    String delim = ", ";
    String targetTextContent = Joiner.on(delim).join(content);
    return new Function<BaseNode, BaseNode>() {

      @Override
      public BaseNode apply(BaseNode node) {
        String tc = node.getTextContent(delim);
        if (targetTextContent.equals(tc)) {
          return node;
        }
        if (tc.contains(targetTextContent)) {
          for (BaseNode child : node.getChildren()) {
            BaseNode match = apply(child);
            if (match != null) { return match; }
          }
        }
        return null;
      }
    };
  }

  static Function<BaseNode, BaseNode> nthWithNodeType(int n, NodeType nt) {
    Preconditions.checkArgument(n >= 0);

    return new Function<BaseNode, BaseNode>() {

      @Override
      public BaseNode apply(BaseNode root) {
        class CounterFinder {
          int remaining = n;

          BaseNode find(BaseNode node) {
            if (node.getNodeType() == nt) {
              if (remaining == 0) {
                return node;
              }
              --remaining;
            }
            for (BaseNode child : node.getChildren()) {
              BaseNode result = find(child);
              if (result != null) { return result; }
            }
            return null;
          }
        }
        return new CounterFinder().find(root);
      }

    };
  }


  static final Function<BaseNode, String> TYPE_AND_NAME_DECORATOR =
      new Function<BaseNode, String>() {

        @Override
        public String apply(BaseNode n) {
          TypeInfo ti = null;
          if (n instanceof TypeDeclaration) {
            ti = ((TypeDeclaration) n).getDeclaredTypeInfo();
          } else if (n instanceof TypeReference) {
            ti = ((TypeReference) n).getReferencedTypeInfo();
          }
          if (ti != null) { return ti.canonName.toString(); }
          if (n instanceof NamePart) {
            NamePart np = (NamePart) n;
            Name.Type nt = np.getNamePartType();
            if (nt != null) { return nt.toString(); }
          }
          return null;
        }

      };

}
