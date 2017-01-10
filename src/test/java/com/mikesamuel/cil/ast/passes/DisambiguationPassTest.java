package com.mikesamuel.cil.ast.passes;

import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.CompilationUnitNode;
import com.mikesamuel.cil.ast.NodeOrBuilder;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.ast.traits.ExpressionNameReference;
import com.mikesamuel.cil.ast.traits.NamePart;
import com.mikesamuel.cil.ast.traits.TypeDeclaration;
import com.mikesamuel.cil.ast.traits.TypeReference;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DisambiguationPassTest extends TestCase {

  @Test
  public static void testEmptyCompilationUnit() {
    assertDisambiguated(
        new String[][] {
          {
            "CompilationUnit.PackageDeclarationImportDeclarationTypeDeclaration",
          },
        },
        new String[][] {
          {
            "",
          },
        },
        all(with(NodeType.CompilationUnit)),
        false,
        null);
  }

  @Test
  public static void testPackageNameAndOrTypeName() {
    assertDisambiguated(
        new String[][] {
          {
            "ImportDeclaration.TypeImportOnDemandDeclaration",
            "  TypeImportOnDemandDeclaration.ImportPackageOrTypeNameDotStrSem",
            "    PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "      PackageOrTypeName.Identifier",
            "        Identifier.Builtin java",
            "      Identifier.Builtin util",
          },
          {
            "ImportDeclaration.SingleTypeImportDeclaration",
            "  SingleTypeImportDeclaration.ImportTypeNameSem",
            "    TypeName.PackageOrTypeNameDotIdentifier",
            "      PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "        PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "          PackageOrTypeName.Identifier",
            "            Identifier.Builtin java",
            "          Identifier.Builtin util",
            "        Identifier.Builtin regex",
            "      Identifier.Builtin Pattern",
          },
          {
            "ImportDeclaration.StaticImportOnDemandDeclaration",
            "  StaticImportOnDemandDeclaration.ImportStaticTypeNameDotStrSem",
            "    TypeName.PackageOrTypeNameDotIdentifier",
            "      PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "        PackageOrTypeName.Identifier",
            "          Identifier.Builtin java",
            "        Identifier.Builtin util",
            "      Identifier.Builtin Locale",
          },
          {
            "ImportDeclaration.SingleStaticImportDeclaration",
            "  SingleStaticImportDeclaration.ImportStaticTypeNameDotIdentifierSem",
            "    TypeName.PackageOrTypeNameDotIdentifier",
            "      PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "        PackageOrTypeName.Identifier",
            "          Identifier.Builtin java",
            "        Identifier.Builtin lang",
            "      Identifier.Builtin System",
            "    Identifier.Builtin err",
          },
        },
        new String[][] {
          {
            "import java.util. *;",
            "import java.util. regex.Pattern;",
            "import static java.util.Locale.*;",
            "import static java.lang.System.err;",
          },
        },
        all(with(NodeType.ImportDeclaration)),
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
            "        TypeName.Identifier : /java/lang/Math",
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
        nth(0, with(NodeType.Expression)),
        false,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testFullExpressionName() {
    assertDisambiguated(
        new String[] {
            "Expression.ConditionalExpression",
            "  Primary.MethodInvocation",
            "    Primary.FieldAccess",
            "      ExpressionAtom.StaticMember",
            "        TypeName.PackageOrTypeNameDotIdentifier : /java/lang/Math",
            "          PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "            PackageOrTypeName.Identifier",
            "              Identifier.Builtin java : PACKAGE",
            "            Identifier.Builtin lang : PACKAGE",
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
        nth(0, with(NodeType.Expression)),
        true,
        TYPE_AND_NAME_DECORATOR);
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
            "              MethodInvocation.ExplicitCallee",
            "                Primary.MethodInvocation",
            "                  ExpressionAtom.FreeField",
            "                    FieldName.Identifier : /java/lang/System.err",
            "                      Identifier.Builtin err",
            "                  MethodName.Identifier",
            "                    Identifier.Builtin println",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      ExpressionAtom.FreeField",
            "                        FieldName.Identifier : /C.i",
            "                          Identifier.Builtin i",
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
            "              MethodInvocation.ExplicitCallee",
            "                Primary.MethodInvocation",
            "                  ExpressionAtom.FreeField",
            "                    FieldName.Identifier : /java/lang/System.err",
            "                      Identifier.Builtin err",
            "                  MethodName.Identifier",
            "                    Identifier.Builtin println",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      ExpressionAtom.Local",
            "                        LocalName.Identifier : /C.<init>(1):i",
            "                          Identifier.Builtin i",
        },
        new String[] {
            "//testFieldScopes",
            "import static java.lang.System.err;",
            "class C {",
            "  int i;",
            "  {",
            "    err.println(i);",  // Field
            "    int i = 1;",
            "    err.println(i);",  // Local
            "  }",
            "}"
        },
        nth(0, with(NodeType.InstanceInitializer)),
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
            "          Identifier.Builtin Object : CLASS",
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
            "                  Identifier.Builtin Object : CLASS",
            "              ClassBody.LcClassBodyDeclarationRc",
        },
        new String[] {
            "class C {",
            "  Object x = new Object() {};",
            "}"
        },

        nth(0, with(NodeType.FieldDeclaration)),
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
            "              Identifier.Builtin java : PACKAGE",
            "            Identifier.Builtin lang : PACKAGE",
            "          Identifier.Builtin Object : CLASS",
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
            "                      Identifier.Builtin java : PACKAGE",
            "                    Identifier.Builtin lang : PACKAGE",
            "                  Identifier.Builtin Object : CLASS",
            "              ClassBody.LcClassBodyDeclarationRc",
        },
        new String[] {
            "class C {",
            "  Object x = new Object() {};",
            "}"
        },

        nth(0, with(NodeType.FieldDeclaration)),
        true,
        TYPE_AND_NAME_DECORATOR);
  }


  @Test
  public static void testInteractionOfInheritanceAndStaticImports() {
    assertDisambiguated(
        new String[][] {
          {
            "Primary.MethodInvocation",
            "  Primary.FieldAccess",
            "    ExpressionAtom.StaticMember",
            "      TypeName.PackageOrTypeNameDotIdentifier : /java/lang/System",
            "        PackageOrTypeName.PackageOrTypeNameDotIdentifier",
            "          PackageOrTypeName.Identifier",
            "            Identifier.Builtin java : PACKAGE",
            "          Identifier.Builtin lang : PACKAGE",
            "        Identifier.Builtin System",
            "    FieldName.Identifier",
            "      Identifier.Builtin err",
            "  MethodName.Identifier",
            "    Identifier.Builtin println",
            "  ArgumentList.ExpressionComExpression",
            "    Expression.ConditionalExpression",
            "      ExpressionAtom.FreeField",
            //       The X below refers to the X inherited from Baz,
            //       not the one imported from Bar.
            "        FieldName.Identifier : /foo/Baz.X",
            "          Identifier.Builtin X",
          },
        },
        new String[][] {
          {
            "//Foo.java",
            "package foo;",
            "import static foo.Bar.X;",
            "",
            "public class Foo extends Baz {",
            "  public static void main(String... argv) {",
            "    System.err.println(X);",
            "  }",
            "}",
          },
          {
            "//Bar.java",
            "package foo;",
            "",
            "public class Bar {",
            "  public static final String X = \"Bar.X\";",
            "}",
          },
          {
            "//Baz.java",
            "package foo;",
            "",
            "public class Baz {",
            "  public static final String X = \"Baz.X\";",
            "}",
          },
        },
        nth(0, with(PrimaryNode.Variant.MethodInvocation)),
        true,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testLocalVariableAndInnerClassInteraction() {
    assertDisambiguated(
        new String[][] {
          {
            "AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
            "  ExpressionAtom.Literal",
            "    Literal.StringLiteral",
            "      StringLiteral.Builtin \"1. \"",
            "  AdditiveOperator.Pls",
            "  Primary.FieldAccess",
            "    ExpressionAtom.FreeField",
            "      FieldName.Identifier : /foo/Foo.x",
            "        Identifier.Builtin x",
            "    FieldName.Identifier",
            "      Identifier.Builtin y",
          },
          {
            "AdditiveExpression.AdditiveExpressionAdditiveOperatorMultiplicativeExpression",
            "  ExpressionAtom.Literal",
            "    Literal.StringLiteral",
            "      StringLiteral.Builtin \"2. \"",
            "  AdditiveOperator.Pls",
            "  Primary.FieldAccess",
            "    ExpressionAtom.FreeField",
            // x is resolves to the field, not the inner class.
            "      FieldName.Identifier : /foo/Foo.x",
            "        Identifier.Builtin x",
            "    FieldName.Identifier",
            "      Identifier.Builtin y",
          }
        },
        new String[][] {
          {
            "//Foo.java",
            "package foo;",
            "import static java.lang.System.err;",
            "",
            "public class Foo {",
            "  static HasY x = new HasY();",
            "",
            "  static class HasY {",
            "    static String y = \"HasY.y\";",
            "  }",
            "",
            "  public static void main(String[] argv) {",
            "    println(\"1. \" + x.y);",  // HasY.y
            "    Inner.run();",
            "  }",
            "",
            "  static class Inner {",
            "    // Despite this introduction of class x",
            "    static class x {",
            "      static final String y = \"Inner.x.y\";",
            "    }",
            "    static void run() {",
            "      // The x here refers to the field.",
            "      err.println(\"2. \" + x.y);",  // HasY.y
            "    }",
            "  }",
            "}",
          },
        },
        all(with(NodeType.AdditiveExpression)),
        false,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testQualifiedNew() {
    for (boolean longNames : new boolean[] { false, true }) {
      assertDisambiguated(
          new String[][] {
            {
              "ArgumentList.ExpressionComExpression",
              "  Expression.ConditionalExpression",
              "    Primary.InnerClassCreation",
              "      ExpressionAtom.FreeField",
              "        FieldName.Identifier : /C.d",
              "          Identifier.Builtin d",
              "      UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody",
              "        ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
              "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
              "            Identifier.Builtin E : CLASS",
            },
          },
          new String[][] {
            {
              "//C",
              "class C {",
              "  D d = new D();",
              "  {",
              "    System.err.println(d.new E());",
              "  }",
              "}"
            },
            {
              "//D",
              "class D {",
              "  class E {",
              "  }",
              "}",
            },
          },
          nth(0, with(NodeType.ArgumentList)),
          longNames,
          TYPE_AND_NAME_DECORATOR);
    }
  }

  static void assertDisambiguated(
      String[] want,
      String[] input,
      NodeMatcher nodeMatcher,
      boolean useLongNames,
      @Nullable Function<? super NodeOrBuilder, ? extends String> decorator,
      String... expectedErrors) {
    assertDisambiguated(
        new String[][] { want },
        new String[][] { input }, nodeMatcher, useLongNames, decorator,
        expectedErrors);
  }

  static void assertDisambiguated(
      String[][] want,
      String[][] inputs,
      NodeMatcher nodeMatcher,
      boolean useLongNames,
      @Nullable Function<? super NodeOrBuilder, ? extends String> decorator,
      String... expectedErrors) {
    ImmutableList<CompilationUnitNode> disambiguated =
        PassTestHelpers.expectErrors(
            new PassTestHelpers
            .LoggableOperation<ImmutableList<CompilationUnitNode>>() {

              @Override
              public ImmutableList<CompilationUnitNode> run(Logger logger) {
                ImmutableList<CompilationUnitNode> cuNodes =
                    PassTestHelpers.parseCompilationUnits(inputs);

                DeclarationPass declarationPass = new DeclarationPass(logger);
                TypeInfoResolver typeInfoResolver =
                    declarationPass.run(cuNodes);

                ExpressionScopePass scopePass = new ExpressionScopePass(
                    typeInfoResolver, logger);
                scopePass.run(cuNodes);

                return new DisambiguationPass(
                        typeInfoResolver, logger, useLongNames)
                    .run(cuNodes);
              }

            },
            expectedErrors);
    ImmutableList.Builder<BaseNode> matches = ImmutableList.builder();
    nodeMatcher.match(disambiguated, matches);

    StringBuilder wantJoined = new StringBuilder();
    for (String[] wantLines : want) {
      if (wantJoined.length() != 0) {
        wantJoined.append("\n\n");
      }
      Joiner.on('\n').appendTo(wantJoined, wantLines);
    }

    StringBuilder got = new StringBuilder();
    for (BaseNode match : matches.build()) {
      if (got.length() != 0) {
        got.append("\n\n");
      }
      got.append(
          match.toAsciiArt(
              "",
              decorator != null ? decorator : Functions.constant(null)));
    }

    assertEquals(
        wantJoined.toString(),
        got.toString());
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

  static Predicate<BaseNode> with(NodeType nt) {
    return new Predicate<BaseNode>() {

      @Override
      public boolean apply(BaseNode node) {
        return node.getNodeType() == nt;
      }

    };
  }

  static Predicate<BaseNode> with(NodeVariant nv) {
    return new Predicate<BaseNode>() {

      @Override
      public boolean apply(BaseNode node) {
        return node.getVariant() == nv;
      }

    };
  }

  static NodeMatcher nth(int n, Predicate<? super BaseNode> p) {
    Preconditions.checkArgument(n >= 0);

    return new NodeMatcher() {
      int remaining = n;

      @Override
      public void match(
          ImmutableList<? extends BaseNode> nodes,
          ImmutableList.Builder<BaseNode> out) {

        for (BaseNode node : nodes) {
          if (remaining < 0) { break; }

          if (p.apply(node)) {
            if (remaining == 0) {
              out.add(node);
            }
            --remaining;
          }

          match(node.getChildren(), out);
        }
      }

    };
  }

  static NodeMatcher all(Predicate<? super BaseNode> p) {
    return new NodeMatcher() {

      @Override
      public void match(
          ImmutableList<? extends BaseNode> nodes,
          ImmutableList.Builder<BaseNode> out) {
        for (BaseNode node : nodes) {
          if (p.apply(node)) {
            out.add(node);
          }
          match(node.getChildren(), out);
        }
      }

    };
  }


  static final Function<NodeOrBuilder, String> TYPE_AND_NAME_DECORATOR =
      new Function<NodeOrBuilder, String>() {

        @Override
        public String apply(NodeOrBuilder n) {
          Name canonName = null;
          if (n instanceof TypeDeclaration) {
            TypeInfo ti = ((TypeDeclaration) n).getDeclaredTypeInfo();
            canonName = ti != null ? ti.canonName : null;
          }
          if (canonName == null && n instanceof TypeReference) {
            TypeInfo ti = ((TypeReference) n).getReferencedTypeInfo();
            canonName = ti != null ? ti.canonName : null;
          }
          if (canonName == null && n instanceof ExpressionNameReference) {
            canonName = ((ExpressionNameReference) n)
                .getReferencedExpressionName();
          }
          Name.Type nt = null;
          if (n instanceof NamePart) {
            NamePart np = (NamePart) n;
            nt = np.getNamePartType();
          }
          return canonName != null
              ? (nt != null ? canonName + " " + nt : canonName.toString())
              : (nt != null ? nt.toString() : null);
        }

      };


  interface NodeMatcher {
    void match(
        ImmutableList<? extends BaseNode> nodes,
        ImmutableList.Builder<BaseNode> out);
  }
}
