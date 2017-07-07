package com.mikesamuel.cil.ast.passes;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.j8.ContextFreeNameNode;
import com.mikesamuel.cil.ast.j8.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8ExpressionNameReference;
import com.mikesamuel.cil.ast.j8.J8FileNode;
import com.mikesamuel.cil.ast.j8.J8NamePart;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.j8.J8TypeDeclaration;
import com.mikesamuel.cil.ast.j8.J8TypeReference;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.meta.MetadataBridge;
import com.mikesamuel.cil.ast.meta.Name;
import com.mikesamuel.cil.ast.meta.TypeInfo;
import com.mikesamuel.cil.ast.meta.TypeInfoResolver;
import com.mikesamuel.cil.parser.SList;

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
        all(with(J8NodeType.CompilationUnit)),
        Names.SHORT,
        RinseAndRepeat.ONCE,
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
        all(with(J8NodeType.ImportDeclaration)),
        Names.SHORT,
        RinseAndRepeat.ONCE,
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
            "          Identifier.Builtin Math : CLASS",
            "      FieldName.Identifier",
            "        Identifier.Builtin PI : FIELD",
            "    MethodName.Identifier",
            "      Identifier.Builtin hashCode : METHOD",
        },
        new String[] {
            "//ExpressionName",
            "class C {",
            "  int x = Math.PI.hashCode();",
            "}"
        },
        nth(0, with(J8NodeType.Expression)),
        Names.SHORT,
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
            "          Identifier.Builtin Math : CLASS",
            "      FieldName.Identifier",
            "        Identifier.Builtin PI : FIELD",
            "    MethodName.Identifier",
            "      Identifier.Builtin hashCode : METHOD",
        },
        new String[] {
            "//ExpressionName",
            "class C {",
            "  int x = Math.PI.hashCode();",
            "}"
        },
        nth(0, with(J8NodeType.Expression)),
        Names.LONG,
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
            "                      Identifier.Builtin err : FIELD",
            "                  MethodName.Identifier",
            "                    Identifier.Builtin println : METHOD",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      ExpressionAtom.FreeField",
            "                        FieldName.Identifier : /C.i",
            "                          Identifier.Builtin i : FIELD",
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
            "                      Identifier.Builtin err : FIELD",
            "                  MethodName.Identifier",
            "                    Identifier.Builtin println : METHOD",
            "                  ArgumentList.ExpressionComExpression",
            "                    Expression.ConditionalExpression",
            "                      ExpressionAtom.Local",
            "                        LocalName.Identifier : /C.<init>(1):i",
            "                          Identifier.Builtin i : LOCAL",
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
        nth(0, with(J8NodeType.InstanceInitializer)),
        Names.SHORT,
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

        nth(0, with(J8NodeType.FieldDeclaration)),
        Names.SHORT,
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

        nth(0, with(J8NodeType.FieldDeclaration)),
        Names.LONG,
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
            "        Identifier.Builtin System : CLASS",
            "    FieldName.Identifier",
            "      Identifier.Builtin err : FIELD",
            "  MethodName.Identifier",
            "    Identifier.Builtin println : METHOD",
            "  ArgumentList.ExpressionComExpression",
            "    Expression.ConditionalExpression",
            "      ExpressionAtom.FreeField",
            //       The X below refers to the X inherited from Baz,
            //       not the one imported from Bar.
            "        FieldName.Identifier : /foo/Baz.X",
            "          Identifier.Builtin X : FIELD",
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
        Names.LONG,
        RinseAndRepeat.ONCE,
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
            "        Identifier.Builtin x : FIELD",
            "    FieldName.Identifier",
            "      Identifier.Builtin y : FIELD",
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
            "        Identifier.Builtin x : FIELD",
            "    FieldName.Identifier",
            "      Identifier.Builtin y : FIELD",
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
        all(with(J8NodeType.AdditiveExpression)),
        Names.SHORT,
        RinseAndRepeat.ONCE,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testQualifiedNew() {
    for (Names names : Names.values()) {
      assertDisambiguated(
          new String[][] {
            {
              "ArgumentList.ExpressionComExpression",
              "  Expression.ConditionalExpression",
              "    Primary.InnerClassCreation",
              "      ExpressionAtom.FreeField",
              "        FieldName.Identifier : /C.d",
              "          Identifier.Builtin d : FIELD",
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
          nth(0, with(J8NodeType.ArgumentList)),
          names,
          RinseAndRepeat.ONCE,
          TYPE_AND_NAME_DECORATOR);
    }
  }

  @Test
  public static void testQualifiedNewWithDiamond() {
    for (Names names : Names.values()) {
      assertDisambiguated(
          new String[][] {
            {
              "ArgumentList.ExpressionComExpression",
              "  Expression.ConditionalExpression",
              "    Primary.InnerClassCreation",
              "      ExpressionAtom.FreeField",
              "        FieldName.Identifier : /C.d",
              "          Identifier.Builtin d : FIELD",
              "      UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody",
              "        ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
              "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
              "            Identifier.Builtin E : CLASS",
              "          Diamond.LtGt",
            },
          },
          new String[][] {
            {
              "//C",
              "class C {",
              "  D d = new D();",
              "  {",
              "    System.err.println(d.new E<>());",
              "  }",
              "}"
            },
            {
              "//D",
              "class D {",
              "  class E<T> {",
              "  }",
              "}",
            },
          },
          nth(0, with(J8NodeType.ArgumentList)),
          names,
          RinseAndRepeat.ONCE,
          TYPE_AND_NAME_DECORATOR);
    }
  }

  @Test
  public static void testQualifiedNewWithTypeParameters() {
    assertDisambiguated(
        new String[][] {
          {
            "ArgumentList.ExpressionComExpression",
            "  Expression.ConditionalExpression",
            "    Primary.InnerClassCreation",
            "      ExpressionAtom.FreeField",
            "        FieldName.Identifier : /C.d",
            "          Identifier.Builtin d : FIELD",
            "      UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody",
            "        ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
            "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "            Identifier.Builtin E : CLASS",
            "            TypeArguments.LtTypeArgumentListGt",
            "              TypeArgumentList.TypeArgumentComTypeArgument",
            "                TypeArgument.ReferenceType",
            "                  ReferenceType.ClassOrInterfaceType",
            "                    ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Void",
            "                      ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "                        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "                          Identifier.Builtin java : PACKAGE",
            "                        Identifier.Builtin lang : PACKAGE",
            "                      Identifier.Builtin Void : CLASS",
          },
        },
        new String[][] {
          {
            "//C",
            "class C {",
            "  D d = new D();",
            "  {",
            "    System.err.println(d.new E<Void>());",
            "  }",
            "}"
          },
          {
            "//D",
            "class D {",
            "  class E<T> {",
            "  }",
            "}",
          },
        },
        nth(0, with(J8NodeType.ArgumentList)),
        Names.LONG,
        RinseAndRepeat.ONCE,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testParameterizedSubtype() {
    assertDisambiguated(
        new String[][] {
          {
            "ClassDeclaration.NormalClassDeclaration",
            "  NormalClassDeclaration.Declaration : /Foo",
            "    SimpleTypeName.Identifier",
            "      Identifier.Builtin Foo",
            "    Superclass.ExtendsClassType",
            "      ClassType.ClassOrInterfaceType",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Iterable",
            "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "            ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "              Identifier.Builtin java : PACKAGE",
            "            Identifier.Builtin lang : PACKAGE",
            "          Identifier.Builtin Iterable : CLASS",
            "          TypeArguments.LtTypeArgumentListGt",
            "            TypeArgumentList.TypeArgumentComTypeArgument",
            "              TypeArgument.ReferenceType",
            "                ReferenceType.ClassOrInterfaceType",
            "                  ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /Foo",
            "                    Identifier.Builtin Foo : CLASS",
            "    Superinterfaces.ImplementsInterfaceTypeList",
            "      InterfaceTypeList.InterfaceTypeComInterfaceType",
            "        InterfaceType.ClassOrInterfaceType",
            "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/lang/Comparable",
            "            ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "              ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "                Identifier.Builtin java : PACKAGE",
            "              Identifier.Builtin lang : PACKAGE",
            "            Identifier.Builtin Comparable : CLASS",
            "            TypeArguments.LtTypeArgumentListGt",
            "              TypeArgumentList.TypeArgumentComTypeArgument",
            "                TypeArgument.ReferenceType",
            "                  ReferenceType.ClassOrInterfaceType",
            "                    ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /Foo",
            "                      Identifier.Builtin Foo : CLASS",
            "    ClassBody.LcClassBodyDeclarationRc",
            "      ClassBodyDeclaration.ConstructorDeclaration",
            "        ConstructorDeclaration.Declaration",
            "          Modifier.Public",
            "          ConstructorDeclarator.TypeParametersSimpleTypeNameLpFormalParameterListRp",
            "            SimpleTypeName.Identifier",
            "              Identifier.Builtin Foo",
            "          ConstructorBody.LcExplicitConstructorInvocationBlockStatementsRc",
          },
        },
        new String[][] {
          {
            "//Foo",
            "class Foo extends Iterable<Foo> implements Comparable<Foo> {",
            "}"
          },
        },
        nth(0, with(J8NodeType.ClassDeclaration)),
        Names.LONG,
        RinseAndRepeat.ONCE,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testTypeDiamondInConstructor() {
    assertDisambiguated(
        new String[] {
            "UnqualifiedClassInstanceCreationExpression.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody",
            "  ClassOrInterfaceTypeToInstantiate.ClassOrInterfaceTypeDiamond",
            "    ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /java/util/ArrayList",
            "      ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "          Identifier.Builtin java : PACKAGE",
            "        Identifier.Builtin util : PACKAGE",
            "      Identifier.Builtin ArrayList : CLASS",
            // Hoisted into ClassOrInterfaceTypeToInstantiate
            "    Diamond.LtGt",
        },
        new String[] {
          "//C",
          "import java.util.ArrayList;",
          "import java.util.List;",
          "class C {",
          "  List<String> ls;",
          "  {",
          "    ls = new ArrayList<>();",
          "  }",
          "}",
        },
        nth(0, with(J8NodeType.UnqualifiedClassInstanceCreationExpression)),
        Names.LONG,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testEnumConstants() {
    assertDisambiguated(
        new String[] {
            "Primary.FieldAccess",
            "  ExpressionAtom.StaticMember",
            "    TypeName.PackageOrTypeNameDotIdentifier : /C$E",
            "      PackageOrTypeName.Identifier",
            "        Identifier.Builtin C : CLASS",
            "      Identifier.Builtin E : CLASS",
            "  FieldName.Identifier",
            "    Identifier.Builtin B : FIELD",
        },
        new String[] {
          "//C",
          "import static java.lang.System.out;",
          "class C {",
          "  enum E { A, B,; }",
          "  static {",
          "    E x = E.B;",
          "  }",
          "}",
        },
        nth(0, with(J8NodeType.Primary)),
        Names.LONG,
        TYPE_AND_NAME_DECORATOR);
  }

  @Test
  public static void testDoublePass() {
    // Make sure that resolving ContextFreeNames, removing metadata, and
    // rerunning still results in all the metadata required by subsequent
    // passes.
    String[][] inputs = {
      {
        "//C",
        "package foo;",
        "class C {",
        "  E x = E.A;",
        "  static {",
        "    E e;",
        "    e = x;",
        "  }",
        "}",
      },
      {
        "//E",
        "package foo;",
        "enum E {",
        "  A,",
        "  B,",
        "}",
      },
    };
    EnumMap<Names, String[][]> goldens = new EnumMap<>(Names.class);
    goldens.put(
        Names.SHORT,
        new String[][] {
          {
            "FieldDeclaration.Declaration",
            "  UnannType.NotAtType",
            "    Type.ReferenceType",
            "      ReferenceType.ClassOrInterfaceType",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /foo/E",
            "          Identifier.Builtin E : CLASS",
            "  VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
            "    VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
            "      VariableDeclaratorId.IdentifierDims",
            "        Identifier.Builtin x",
            "      VariableInitializer.Expression",
            "        Expression.ConditionalExpression",
            "          Primary.FieldAccess",
            "            ExpressionAtom.StaticMember",
            "              TypeName.Identifier : /foo/E",
            "                Identifier.Builtin E : CLASS",
            "            FieldName.Identifier",
            "              Identifier.Builtin A : FIELD",
          },
          {
            "Assignment.LeftHandSideAssignmentOperatorExpression",
            "  LeftHandSide.Ambiguous",
            "    ExpressionAtom.Local",
            "      LocalName.Identifier : /foo/C.<clinit>(1):e",
            "        Identifier.Builtin e : LOCAL",
            "  AssignmentOperator.Eq",
            "  Expression.ConditionalExpression",
            "    ExpressionAtom.FreeField",
            "      FieldName.Identifier : /foo/C.x",
            "        Identifier.Builtin x : FIELD",
          },
        });
    goldens.put(
        Names.LONG,
        new String[][] {
          {
            "FieldDeclaration.Declaration",
            "  UnannType.NotAtType",
            "    Type.ReferenceType",
            "      ReferenceType.ClassOrInterfaceType",
            "        ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments : /foo/E",
            "          ClassOrInterfaceType.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments",
            "            Identifier.Builtin foo : PACKAGE",
            "          Identifier.Builtin E : CLASS",
            "  VariableDeclaratorList.VariableDeclaratorComVariableDeclarator",
            "    VariableDeclarator.VariableDeclaratorIdEqVariableInitializer",
            "      VariableDeclaratorId.IdentifierDims",
            "        Identifier.Builtin x",
            "      VariableInitializer.Expression",
            "        Expression.ConditionalExpression",
            "          Primary.FieldAccess",
            "            ExpressionAtom.StaticMember",
            "              TypeName.PackageOrTypeNameDotIdentifier : /foo/E",
            "                PackageOrTypeName.Identifier",
            "                  Identifier.Builtin foo : PACKAGE",
            "                Identifier.Builtin E : CLASS",
            "            FieldName.Identifier",
            "              Identifier.Builtin A : FIELD",
          },
          {
            "Assignment.LeftHandSideAssignmentOperatorExpression",
            "  LeftHandSide.Ambiguous",
            "    ExpressionAtom.Local",
            "      LocalName.Identifier : /foo/C.<clinit>(1):e",
            "        Identifier.Builtin e : LOCAL",
            "  AssignmentOperator.Eq",
            "  Expression.ConditionalExpression",
            "    ExpressionAtom.FreeField",
            "      FieldName.Identifier : /foo/C.x",
            "        Identifier.Builtin x : FIELD",
          },
        });
    for (Names names : Names.values()) {
      for (RinseAndRepeat rar : RinseAndRepeat.values()) {
        assertDisambiguated(
            goldens.get(names),
            inputs,
            all(with(J8NodeType.FieldDeclaration, J8NodeType.Assignment)),
            names,
            rar,
            TYPE_AND_NAME_DECORATOR);
      }
    }
  }

  enum Names {
    LONG,
    SHORT,
  }

  enum RinseAndRepeat {
    ONCE,
    RUN_CLEAR_RUN,
  }

  static void assertDisambiguated(
      String[] want,
      String[] input,
      NodeMatcher nodeMatcher,
      Names names,
      @Nullable Function<? super NodeI<?, ?, ?>, ? extends String> decorator,
      String... expectedErrors) {
    assertDisambiguated(
        new String[][] { want },
        new String[][] { input }, nodeMatcher, names, RinseAndRepeat.ONCE,
        decorator, expectedErrors);
  }

  static void assertDisambiguated(
      String[][] want,
      String[][] inputs,
      NodeMatcher nodeMatcher,
      Names names,
      RinseAndRepeat rinseAndRepeat,
      @Nullable Function<? super NodeI<?, ?, ?>, ? extends String> decorator,
      String... expectedErrors) {
    ImmutableList<J8FileNode> disambiguated = PassTestHelpers.expectErrors(
        new PassTestHelpers.LoggableOperation<ImmutableList<J8FileNode>>() {

          ImmutableList<J8FileNode> runOne(
              Logger logger,
              ImmutableList<J8FileNode> fileNodes) {
            DeclarationPass declarationPass = new DeclarationPass(logger);
            TypeInfoResolver typeInfoResolver =
                declarationPass.run(fileNodes).typeInfoResolver;

            ExpressionScopePass scopePass = new ExpressionScopePass(
                typeInfoResolver, logger);
            scopePass.run(fileNodes);

            return new DisambiguationPass(
                typeInfoResolver, logger, Names.LONG == names)
                .run(fileNodes);
          }

          @Override
          public ImmutableList<J8FileNode> run(Logger logger) {
            ImmutableList<J8FileNode> fileNodes =
                PassTestHelpers.parseCompilationUnits(logger, inputs);
            fileNodes = runOne(logger, fileNodes);
            switch (rinseAndRepeat) {
              case ONCE:
                break;
              case RUN_CLEAR_RUN:
                // Rinse
                for (J8FileNode f : fileNodes) {
                  ((J8BaseNode) f).transformMetadata(
                      MetadataBridge.Bridges.ZERO, true);
                }
                // Repeat
                fileNodes = runOne(logger, fileNodes);
                break;
            }
            return fileNodes;
          }

        },
        expectedErrors);
    ImmutableList.Builder<J8BaseNode> matches = ImmutableList.builder();
    nodeMatcher.match(disambiguated, matches);

    StringBuilder wantJoined = new StringBuilder();
    for (String[] wantLines : want) {
      if (wantJoined.length() != 0) {
        wantJoined.append("\n\n");
      }
      Joiner.on('\n').appendTo(wantJoined, wantLines);
    }

    StringBuilder got = new StringBuilder();
    for (J8BaseNode match : matches.build()) {
      if (got.length() != 0) {
        got.append("\n\n");
      }
      got.append(
          match.toAsciiArt(
              "",
              decorator != null ? decorator : Functions.constant(null)));
    }

    assertEquals(
        "names=" + names + ", r&r=" + rinseAndRepeat,
        wantJoined.toString(),
        got.toString());

    for (J8FileNode fn : disambiguated) {
      checkNoContextFreeNames(
          fn.getChildren(), SList.append(null, (J8BaseNode) fn));
    }
  }


  private static void checkNoContextFreeNames(
      Iterable<? extends J8BaseNode> nodes, SList<J8BaseNode> path) {
    for (J8BaseNode node : nodes) {
      SList<J8BaseNode> pathWithNode = SList.append(path, node);
      if (node instanceof ContextFreeNamesNode
          || node instanceof ContextFreeNameNode) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getSourcePosition())
            .append(" : Not disambiguated");
        for (J8BaseNode pathElement : SList.reverseIterable(pathWithNode)) {
          sb.append(" : ").append(pathElement.getVariant());
        }
        fail(sb.toString());
      }
      checkNoContextFreeNames(node.getChildren(), pathWithNode);
    }
  }


  static Function<J8BaseNode, J8BaseNode> withContent(String... content) {
    String delim = ", ";
    String targetTextContent = Joiner.on(delim).join(content);
    return new Function<J8BaseNode, J8BaseNode>() {

      @Override
      public J8BaseNode apply(J8BaseNode node) {
        String tc = node.getTextContent(delim);
        if (targetTextContent.equals(tc)) {
          return node;
        }
        if (tc.contains(targetTextContent)) {
          for (J8BaseNode child : node.getChildren()) {
            J8BaseNode match = apply(child);
            if (match != null) { return match; }
          }
        }
        return null;
      }
    };
  }

  static Predicate<NodeI<?, ?, ?>> with(J8NodeType nt, J8NodeType... nts) {
    return new Predicate<NodeI<?, ?, ?>>() {
      EnumSet<J8NodeType> types = EnumSet.noneOf(J8NodeType.class);
      {
        types.add(nt);
        types.addAll(Arrays.asList(nts));
      }

      @Override
      public boolean apply(NodeI<?, ?, ?> node) {
        return types.contains(node.getNodeType());
      }

    };
  }

  static Predicate<NodeI<?, ?, ?>> with(J8NodeVariant nv) {
    return new Predicate<NodeI<?, ?, ?>>() {

      @Override
      public boolean apply(NodeI<?, ?, ?> node) {
        return node.getVariant() == nv;
      }

    };
  }

  static NodeMatcher nth(int n, Predicate<? super NodeI<?, ?, ?>> p) {
    Preconditions.checkArgument(n >= 0);

    return new NodeMatcher() {
      int remaining = n;

      @Override
      public void match(
          List<? extends NodeI<?, ?, ?>> nodes,
          ImmutableList.Builder<J8BaseNode> out) {

        for (NodeI<?, ?, ?> node : nodes) {
          if (remaining < 0) { break; }

          if (p.apply(node)) {
            if (remaining == 0) {
              out.add((J8BaseNode) node);
            }
            --remaining;
          }

          match(node.getChildren(), out);
        }
      }

    };
  }

  static NodeMatcher all(Predicate<? super NodeI<?, ?, ?>> p) {
    return new NodeMatcher() {

      @Override
      public void match(
          List<? extends NodeI<?, ?, ?>> nodes,
          ImmutableList.Builder<J8BaseNode> out) {
        for (NodeI<?, ?, ?> node : nodes) {
          if (p.apply(node)) {
            out.add((J8BaseNode) node);
          }
          match(node.getChildren(), out);
        }
      }

    };
  }


  static final Function<NodeI<?, ?, ?>, String> TYPE_AND_NAME_DECORATOR =
      new Function<NodeI<?, ?, ?>, String>() {

        @Override
        public String apply(NodeI<?, ?, ?> n) {
          Name canonName = null;
          if (n instanceof J8TypeDeclaration) {
            TypeInfo ti = ((J8TypeDeclaration) n).getDeclaredTypeInfo();
            canonName = ti != null ? ti.canonName : null;
          }
          if (canonName == null && n instanceof J8TypeReference) {
            TypeInfo ti = ((J8TypeReference) n).getReferencedTypeInfo();
            canonName = ti != null ? ti.canonName : null;
          }
          if (canonName == null && n instanceof J8ExpressionNameReference) {
            canonName = ((J8ExpressionNameReference) n)
                .getReferencedExpressionName();
          }
          Name.Type nt = null;
          if (n instanceof J8NamePart) {
            J8NamePart np = (J8NamePart) n;
            nt = np.getNamePartType();
          }
          return canonName != null
              ? (nt != null ? canonName + " " + nt : canonName.toString())
              : (nt != null ? nt.toString() : null);
        }

      };


  interface NodeMatcher {
    void match(
        List<? extends NodeI<?, ?, ?>> nodes,
        ImmutableList.Builder<J8BaseNode> out);
  }
}
