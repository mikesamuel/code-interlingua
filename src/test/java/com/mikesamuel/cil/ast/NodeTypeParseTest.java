package com.mikesamuel.cil.ast;

import org.junit.Test;

/**
 * A Java language production.
 */
@javax.annotation.Generated("src/main/scripts/generate_parser_helpers.py")
public final class NodeTypeParseTest extends AbstractParSerTestCase {

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testIdentifierBuiltin() {
    parseSanityCheck(
        IdentifierNode.Variant.Builtin,
        "foo"
        );
  }

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testIdentifierCharsBuiltin() {
    parseSanityCheck(
        IdentifierCharsNode.Variant.Builtin,
        "foo"
        );
  }

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testIntegerLiteralBuiltin() {
    parseSanityCheck(
        IntegerLiteralNode.Variant.Builtin,
        "123"
        );
  }

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testFloatingPointLiteralBuiltin() {
    parseSanityCheck(
        FloatingPointLiteralNode.Variant.Builtin,
        "123.456"
        );
  }

  /**
   * <pre>"false"</pre>
   */
  @Test
  public void testBooleanLiteralFalse() {
    parseSanityCheck(
        BooleanLiteralNode.Variant.False,
        "false"
        );
  }

  /**
   * <pre>"true"</pre>
   */
  @Test
  public void testBooleanLiteralTrue() {
    parseSanityCheck(
        BooleanLiteralNode.Variant.True,
        "true"
        );
  }

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testCharacterLiteralBuiltin() {
    parseSanityCheck(
        CharacterLiteralNode.Variant.Builtin,
        "'c'"
        );
  }

  /**
   * <pre>builtin</pre>
   */
  @Test
  public void testStringLiteralBuiltin() {
    parseSanityCheck(
        StringLiteralNode.Variant.Builtin,
        "\"\""
        );
  }

  /**
   * <pre>"null"</pre>
   */
  @Test
  public void testNullLiteralNull() {
    parseSanityCheck(
        NullLiteralNode.Variant.Null,
        "null"
        );
  }

  /**
   * <pre>IntegerLiteral</pre>
   */
  @Test
  public void testLiteralIntegerLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.IntegerLiteral,
        "0xA0"
        );
  }

  /**
   * <pre>FloatingPointLiteral</pre>
   */
  @Test
  public void testLiteralFloatingPointLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.FloatingPointLiteral,
        ".5e-2"
        );
  }

  /**
   * <pre>BooleanLiteral</pre>
   */
  @Test
  public void testLiteralBooleanLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.BooleanLiteral,
        "false"
        );
  }

  /**
   * <pre>CharacterLiteral</pre>
   */
  @Test
  public void testLiteralCharacterLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.CharacterLiteral,
        "'\\n'"
        );
  }

  /**
   * <pre>StringLiteral</pre>
   */
  @Test
  public void testLiteralStringLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.StringLiteral,
        "\"str\""
        );
  }

  /**
   * <pre>NullLiteral</pre>
   */
  @Test
  public void testLiteralNullLiteral() {
    parseSanityCheck(
        LiteralNode.Variant.NullLiteral,
        " null "
        );
  }

  /**
   * <pre>PrimitiveType</pre>
   */
  @Test
  public void testTypePrimitiveType() {
    parseSanityCheck(
        TypeNode.Variant.PrimitiveType,
        "int"
        );
  }

  /**
   * <pre>ReferenceType</pre>
   */
  @Test
  public void testTypeReferenceType() {
    parseSanityCheck(
        TypeNode.Variant.ReferenceType,
        "Object"
        );
  }

  /**
   * <pre>{ Annotation } NumericType</pre>
   */
  @Test
  public void testPrimitiveTypeAnnotationNumericType() {
    parseSanityCheck(
        PrimitiveTypeNode.Variant.AnnotationNumericType,
        "@Foo long"
        );
  }

  /**
   * <pre>{ Annotation } "boolean"</pre>
   */
  @Test
  public void testPrimitiveTypeAnnotationBoolean() {
    parseSanityCheck(
        PrimitiveTypeNode.Variant.AnnotationBoolean,
        "boolean"
        );
  }

  /**
   * <pre>IntegralType</pre>
   */
  @Test
  public void testNumericTypeIntegralType() {
    parseSanityCheck(
        NumericTypeNode.Variant.IntegralType,
        "int"
        );
  }

  /**
   * <pre>FloatingPointType</pre>
   */
  @Test
  public void testNumericTypeFloatingPointType() {
    parseSanityCheck(
        NumericTypeNode.Variant.FloatingPointType,
        "float"
        );
  }

  /**
   * <pre>"byte"</pre>
   */
  @Test
  public void testIntegralTypeByte() {
    parseSanityCheck(
        IntegralTypeNode.Variant.Byte,
        "byte"
        );
  }

  /**
   * <pre>"short"</pre>
   */
  @Test
  public void testIntegralTypeShort() {
    parseSanityCheck(
        IntegralTypeNode.Variant.Short,
        "short"
        );
  }

  /**
   * <pre>"int"</pre>
   */
  @Test
  public void testIntegralTypeInt() {
    parseSanityCheck(
        IntegralTypeNode.Variant.Int,
        "int"
        );
  }

  /**
   * <pre>"long"</pre>
   */
  @Test
  public void testIntegralTypeLong() {
    parseSanityCheck(
        IntegralTypeNode.Variant.Long,
        "long"
        );
  }

  /**
   * <pre>"char"</pre>
   */
  @Test
  public void testIntegralTypeChar() {
    parseSanityCheck(
        IntegralTypeNode.Variant.Char,
        "char"
        );
  }

  /**
   * <pre>"float"</pre>
   */
  @Test
  public void testFloatingPointTypeFloat() {
    parseSanityCheck(
        FloatingPointTypeNode.Variant.Float,
        "float"
        );
  }

  /**
   * <pre>"double"</pre>
   */
  @Test
  public void testFloatingPointTypeDouble() {
    parseSanityCheck(
        FloatingPointTypeNode.Variant.Double,
        "double"
        );
  }

  /**
   * <pre>ClassOrInterfaceType</pre>
   */
  @Test
  public void testReferenceTypeClassOrInterfaceType() {
    parseSanityCheck(
        ReferenceTypeNode.Variant.ClassOrInterfaceType,
        "Object"
        );
  }

  /**
   * <pre>TypeVariable</pre>
   */
  @Test
  public void testReferenceTypeTypeVariable() {
    parseSanityCheck(
        ReferenceTypeNode.Variant.TypeVariable,
        "FOO",
        // Cannot lexically distinguish TypeVariable from ClassOrInterfaceType
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>ArrayType</pre>
   */
  @Test
  public void testReferenceTypeArrayType() {
    parseSanityCheck(
        ReferenceTypeNode.Variant.ArrayType,
        "int[]"
        );
  }

  /**
   * <pre>ClassType</pre>
   */
  @Test
  public void testClassOrInterfaceTypeClassType() {
    parseSanityCheck(
        ClassOrInterfaceTypeNode.Variant.ClassType,
        "@Nonnull String",
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>InterfaceType</pre>
   *
  @Test
  public void testClassOrInterfaceTypeInterfaceType() {
    parseSanityCheck(
        ClassOrInterfaceTypeNode.Variant.InterfaceType,
        "Comparable<Integer>",
        // Can't lexically distinguish ClassType from InterfaceType
        Fuzz.SAME_VARIANT
        );
  }*/

  /**
   * <pre>{ Annotation } Identifier [ TypeArguments ]</pre>
   */
  @Test
  public void testClassTypeAnnotationIdentifierTypeArguments() {
    parseSanityCheck(
        ClassTypeNode.Variant.AnnotationIdentifierTypeArguments,
        "@Nonnull String",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>ClassOrInterfaceType "." { Annotation } Identifier [ TypeArguments ]</pre>
   */
  @Test
  public void testClassTypeClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments() {
    parseSanityCheck(
        ClassTypeNode.Variant.ClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments,
        "ImmutableList.Builder<String>",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>ClassType</pre>
   */
  @Test
  public void testInterfaceTypeClassType() {
    parseSanityCheck(
        InterfaceTypeNode.Variant.ClassType,
        "Comparable"
        );
  }

  /**
   * <pre>{ Annotation } Identifier</pre>
   */
  @Test
  public void testTypeVariableAnnotationIdentifier() {
    parseSanityCheck(
        TypeVariableNode.Variant.AnnotationIdentifier,
        "@Nonnull T"
        );
  }

  /**
   * <pre>PrimitiveType Dims</pre>
   */
  @Test
  public void testArrayTypePrimitiveTypeDims() {
    parseSanityCheck(
        ArrayTypeNode.Variant.PrimitiveTypeDims,
        "int[][]"
        );
  }

  /**
   * <pre>ClassOrInterfaceType Dims</pre>
   */
  @Test
  public void testArrayTypeClassOrInterfaceTypeDims() {
    parseSanityCheck(
        ArrayTypeNode.Variant.ClassOrInterfaceTypeDims,
        "Object[ ]"
        );
  }

  /**
   * <pre>TypeVariable Dims</pre>
   */
  @Test
  public void testArrayTypeTypeVariableDims() {
    parseSanityCheck(
        ArrayTypeNode.Variant.TypeVariableDims,
        "T[]",
        // Can't lexically distinguish TypeVariable from ClassOrInterfaceType
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>{ Annotation } Dim { { Annotation } Dim }</pre>
   */
  @Test
  public void testDimsAnnotationDimAnnotationDim() {
    parseSanityCheck(
        DimsNode.Variant.AnnotationDimAnnotationDim,
        "[][]"
        );
  }

  /**
   * <pre>{ TypeParameterModifier } Identifier [ TypeBound ]</pre>
   */
  @Test
  public void testTypeParameterTypeParameterModifierIdentifierTypeBound() {
    parseSanityCheck(
        TypeParameterNode.Variant.TypeParameter,
        "@Nullable Foo extends Bar"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testTypeParameterModifierAnnotation() {
    parseSanityCheck(
        TypeParameterModifierNode.Variant.Annotation,
        "@Nullable"
        );
  }

  /**
   * <pre>"extends" TypeVariable</pre>
   */
  @Test
  public void testTypeBoundExtendsTypeVariable() {
    parseSanityCheck(
        TypeBoundNode.Variant.ExtendsTypeVariable,
        "extends T",
        // TypeVariable is lexically ambiguous with ClassOrInterfaceType
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>"extends" ClassOrInterfaceType { AdditionalBound }</pre>
   */
  @Test
  public void testTypeBoundExtendsClassOrInterfaceTypeAdditionalBound() {
    parseSanityCheck(
        TypeBoundNode.Variant.ExtendsClassOrInterfaceTypeAdditionalBound,
        "extends A & B"
        );
  }

  /**
   * <pre>"&amp;" InterfaceType</pre>
   */
  @Test
  public void testAdditionalBoundAmpInterfaceType() {
    parseSanityCheck(
        AdditionalBoundNode.Variant.AmpInterfaceType,
        "& B"
        );
  }

  /**
   * <pre>"&lt;" TypeArgumentList "&gt;"</pre>
   */
  @Test
  public void testTypeArgumentsLtTypeArgumentListGt() {
    parseSanityCheck(
        TypeArgumentsNode.Variant.LtTypeArgumentListGt,
        "<String, List<Integer>>"
        );
  }

  /**
   * <pre>TypeArgument { "," TypeArgument }</pre>
   */
  @Test
  public void testTypeArgumentListTypeArgumentComTypeArgument() {
    parseSanityCheck(
        TypeArgumentListNode.Variant.TypeArgumentComTypeArgument,
        "String, List<Integer>"
        );
  }

  /**
   * <pre>ReferenceType</pre>
   */
  @Test
  public void testTypeArgumentReferenceType() {
    parseSanityCheck(
        TypeArgumentNode.Variant.ReferenceType,
        "java.lang.Object"
        );
  }

  /**
   * <pre>Wildcard</pre>
   */
  @Test
  public void testTypeArgumentWildcard() {
    parseSanityCheck(
        TypeArgumentNode.Variant.Wildcard,
        "? extends CharSequence"
        );
  }

  /**
   * <pre>{ Annotation } "?" [ WildcardBounds ]</pre>
   */
  @Test
  public void testWildcardAnnotationQmWildcardBounds() {
    parseSanityCheck(
        WildcardNode.Variant.AnnotationQmWildcardBounds,
        "@Nonnull ? super ImmutableList<String>"
        );
  }

  /**
   * <pre>"extends" ReferenceType</pre>
   */
  @Test
  public void testWildcardBoundsExtendsReferenceType() {
    parseSanityCheck(
        WildcardBoundsNode.Variant.ExtendsReferenceType,
        "extends Foo"
        );
  }

  /**
   * <pre>"super" ReferenceType</pre>
   */
  @Test
  public void testWildcardBoundsSuperReferenceType() {
    parseSanityCheck(
        WildcardBoundsNode.Variant.SuperReferenceType,
        "super Foo"
        );
  }

  /**
   * <pre>PackageOrTypeName "." Identifier</pre>
   */
  @Test
  public void testTypeNamePackageOrTypeNameDotIdentifier() {
    parseSanityCheck(
        TypeNameNode.Variant.PackageOrTypeNameDotIdentifier,
        "foo.Bar",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testTypeNameIdentifier() {
    parseSanityCheck(
        TypeNameNode.Variant.Identifier,
        "Foo",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>PackageOrTypeName "." Identifier</pre>
   */
  @Test
  public void testPackageOrTypeNamePackageOrTypeNameDotIdentifier() {
    parseSanityCheck(
        PackageOrTypeNameNode.Variant.PackageOrTypeNameDotIdentifier,
        "foo.Bar",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testPackageOrTypeNameIdentifier() {
    parseSanityCheck(
        PackageOrTypeNameNode.Variant.Identifier,
        "bar",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>ExpressionName "." Identifier ! "("</pre>
   */
  @Test
  public void testExpressionNameExpressionNameDotIdentifierNotLp() {
    parseSanityCheck(
        ExpressionNameNode.Variant.AmbiguousNameDotIdentifier,
        "foo.bar",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testExpressionNameIdentifier() {
    parseSanityCheck(
        ExpressionNameNode.Variant.NotAtContextFreeNames,
        "foo"
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testMethodNameIdentifier() {
    parseSanityCheck(
        MethodNameNode.Variant.Identifier,
        "foo"
        );
  }

  /**
   * <pre>PackageName "." Identifier</pre>
   */
  @Test
  public void testPackageNamePackageNameDotIdentifier() {
    parseSanityCheck(
        PackageNameNode.Variant.IdentifierDotIdentifier,
        "foo.bar.baz"
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testPackageNameIdentifier() {
    parseSanityCheck(
        PackageNameNode.Variant.IdentifierDotIdentifier,
        "com"
        );
  }

  /**
   * <pre>AmbiguousName "." Identifier</pre>
   */
  @Test
  public void testAmbiguousNameAmbiguousNameDotIdentifier() {
    parseSanityCheck(
        AmbiguousNameNode.Variant.AmbiguousNameDotIdentifier,
        "foo.bar",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testAmbiguousNameIdentifier() {
    parseSanityCheck(
        AmbiguousNameNode.Variant.NotAtContextFreeNames,
        "ambiguous",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>[ PackageDeclaration ] { ImportDeclaration } { TypeDeclaration }</pre>
   */
  @Test
  public void testCompilationUnitPackageDeclarationImportDeclarationTypeDeclaration() {
    parseSanityCheck(
        CompilationUnitNode.Variant
        .PackageDeclarationImportDeclarationTypeDeclaration,
        "package foo.bar;import com.*;import org.foo;class C{}"
        );
    parseSanityCheck(
        CompilationUnitNode.Variant
        .PackageDeclarationImportDeclarationTypeDeclaration,
        ""
        );
  }

  /**
   * <pre>{ PackageModifier } "package" Identifier { "." Identifier } ";"</pre>
   */
  @Test
  public void testPackageDeclarationPackageModifierPackageIdentifierDotIdentifierSem() {
    parseSanityCheck(
        PackageDeclarationNode.Variant.Declaration,
        "@javax.annotations.ParametersAreNonnullByDefault package foo;"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testPackageModifierAnnotation() {
    parseSanityCheck(
        PackageModifierNode.Variant.Annotation,
        "@ParametersAreNonnullByDefault"
        );
  }

  /**
   * <pre>SingleTypeImportDeclaration</pre>
   */
  @Test
  public void testImportDeclarationSingleTypeImportDeclaration() {
    parseSanityCheck(
        ImportDeclarationNode.Variant.SingleTypeImportDeclaration,
        "import foo.Bar;"
        );
  }

  /**
   * <pre>TypeImportOnDemandDeclaration</pre>
   */
  @Test
  public void testImportDeclarationTypeImportOnDemandDeclaration() {
    parseSanityCheck(
        ImportDeclarationNode.Variant.TypeImportOnDemandDeclaration,
        "import foo.*;"
        );
  }

  /**
   * <pre>SingleStaticImportDeclaration</pre>
   */
  @Test
  public void testImportDeclarationSingleStaticImportDeclaration() {
    parseSanityCheck(
        ImportDeclarationNode.Variant.SingleStaticImportDeclaration,
        "import static foo.Bar.BAZ;"
        );
  }

  /**
   * <pre>StaticImportOnDemandDeclaration</pre>
   */
  @Test
  public void testImportDeclarationStaticImportOnDemandDeclaration() {
    parseSanityCheck(
        ImportDeclarationNode.Variant.StaticImportOnDemandDeclaration,
        "import static foo.Bar.*;"
        );
  }

  /**
   * <pre>"import" TypeName ";"</pre>
   */
  @Test
  public void testSingleTypeImportDeclarationImportTypeNameSem() {
    parseSanityCheck(
        SingleTypeImportDeclarationNode.Variant.ImportTypeNameSem,
        "import foo.Bar;"
        );
  }

  /**
   * <pre>"import" PackageOrTypeName "." "&#42;" ";"</pre>
   */
  @Test
  public void testTypeImportOnDemandDeclarationImportPackageOrTypeNameDotStrSem() {
    parseSanityCheck(
        TypeImportOnDemandDeclarationNode.Variant.ImportPackageOrTypeNameDotStrSem,
        "import foo.bar.Baz.*;"
        );
  }

  /**
   * <pre>"import" "static" TypeName "." Identifier ";"</pre>
   */
  @Test
  public void testSingleStaticImportDeclarationImportStaticTypeNameDotIdentifierSem() {
    parseSanityCheck(
        SingleStaticImportDeclarationNode.Variant.ImportStaticTypeNameDotIdentifierSem,
        "import static java.lang.Math.PI;"
        );
  }

  /**
   * <pre>"import" "static" TypeName "." "&#42;" ";"</pre>
   */
  @Test
  public void testStaticImportOnDemandDeclarationImportStaticTypeNameDotStrSem() {
    parseSanityCheck(
        StaticImportOnDemandDeclarationNode.Variant.ImportStaticTypeNameDotStrSem,
        "import static java.lang.StrictMath.*;"
        );
  }

  /**
   * <pre>ClassDeclaration</pre>
   */
  @Test
  public void testTypeDeclarationClassDeclaration() {
    parseSanityCheck(
        TypeDeclarationNode.Variant.ClassDeclaration,
        "class C{}"
        );
  }

  /**
   * <pre>InterfaceDeclaration</pre>
   */
  @Test
  public void testTypeDeclarationInterfaceDeclaration() {
    parseSanityCheck(
        TypeDeclarationNode.Variant.InterfaceDeclaration,
        "interface I extends Comparable<I>{ void foo(); }"
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testTypeDeclarationSem() {
    parseSanityCheck(
        TypeDeclarationNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>NormalClassDeclaration</pre>
   */
  @Test
  public void testClassDeclarationNormalClassDeclaration() {
    parseSanityCheck(
        ClassDeclarationNode.Variant.NormalClassDeclaration,
        "public final class Foo { Foo() {} }"
        );
  }

  /**
   * <pre>EnumDeclaration</pre>
   */
  @Test
  public void testClassDeclarationEnumDeclaration() {
    parseSanityCheck(
        ClassDeclarationNode.Variant.EnumDeclaration,
        "public enum E { X(), Y(), Z(),; } "
        );
    parseSanityCheck(
        ClassDeclarationNode.Variant.EnumDeclaration,
        "public enum E { X(), Y, Z() {}; } ",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>{ ClassModifier } "class" Identifier [ TypeParameters ] [ Superclass ] [ Superinterfaces ] ClassBody</pre>
   */
  @Test
  public void testNormalClassDeclarationClassModifierClassIdentifierTypeParametersSuperclassSuperinterfacesClassBody() {
    parseSanityCheck(
        NormalClassDeclarationNode.Variant.Declaration,
        "abstract class AbstractC<T> extends Object implements Serializable {}"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testClassModifierAnnotation() {
    parseSanityCheck(
        ClassModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testClassModifierPublic() {
    parseSanityCheck(
        ClassModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"protected"</pre>
   */
  @Test
  public void testClassModifierProtected() {
    parseSanityCheck(
        ClassModifierNode.Variant.Protected,
        "protected"
        );
  }

  /**
   * <pre>"private"</pre>
   */
  @Test
  public void testClassModifierPrivate() {
    parseSanityCheck(
        ClassModifierNode.Variant.Private,
        "private"
        );
  }

  /**
   * <pre>"abstract"</pre>
   */
  @Test
  public void testClassModifierAbstract() {
    parseSanityCheck(
        ClassModifierNode.Variant.Abstract,
        "abstract"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testClassModifierStatic() {
    parseSanityCheck(
        ClassModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"final"</pre>
   */
  @Test
  public void testClassModifierFinal() {
    parseSanityCheck(
        ClassModifierNode.Variant.Final,
        "final"
        );
  }

  /**
   * <pre>"strictfp"</pre>
   */
  @Test
  public void testClassModifierStrictfp() {
    parseSanityCheck(
        ClassModifierNode.Variant.Strictfp,
        "strictfp"
        );
  }

  /**
   * <pre>"&lt;" TypeParameterList "&gt;"</pre>
   */
  @Test
  public void testTypeParametersLtTypeParameterListGt() {
    parseSanityCheck(
        TypeParametersNode.Variant.LtTypeParameterListGt,
        "<X, Y>"
        );
  }

  /**
   * <pre>TypeParameter { "," TypeParameter }</pre>
   */
  @Test
  public void testTypeParameterListTypeParameterComTypeParameter() {
    parseSanityCheck(
        TypeParameterListNode.Variant.TypeParameterComTypeParameter,
        "X, Y"
        );
  }

  /**
   * <pre>"extends" ClassType</pre>
   */
  @Test
  public void testSuperclassExtendsClassType() {
    parseSanityCheck(
        SuperclassNode.Variant.ExtendsClassType,
        "extends Comparable<BAR>"
        );
  }

  /**
   * <pre>"implements" InterfaceTypeList</pre>
   */
  @Test
  public void testSuperinterfacesImplementsInterfaceTypeList() {
    parseSanityCheck(
        SuperinterfacesNode.Variant.ImplementsInterfaceTypeList,
        "implements A, B, C<D>"
        );
  }

  /**
   * <pre>InterfaceType { "," InterfaceType }</pre>
   */
  @Test
  public void testInterfaceTypeListInterfaceTypeComInterfaceType() {
    parseSanityCheck(
        InterfaceTypeListNode.Variant.InterfaceTypeComInterfaceType,
        "IFoo"
        );
  }

  /**
   * <pre>"{" { ClassBodyDeclaration } "}"</pre>
   */
  @Test
  public void testClassBodyLcClassBodyDeclarationRc() {
    parseSanityCheck(
        ClassBodyNode.Variant.LcClassBodyDeclarationRc,
        "{ public static void main(String...argv) { } }"
        );
  }

  /**
   * <pre>ClassMemberDeclaration</pre>
   */
  @Test
  public void testClassBodyDeclarationClassMemberDeclaration() {
    parseSanityCheck(
        ClassBodyDeclarationNode.Variant.ClassMemberDeclaration,
        "abstract void foo();"
        );
  }

  /**
   * <pre>InstanceInitializer</pre>
   */
  @Test
  public void testClassBodyDeclarationInstanceInitializer() {
    parseSanityCheck(
        ClassBodyDeclarationNode.Variant.InstanceInitializer,
        "{ init(); }"
        );
  }

  /**
   * <pre>StaticInitializer</pre>
   */
  @Test
  public void testClassBodyDeclarationStaticInitializer() {
    parseSanityCheck(
        ClassBodyDeclarationNode.Variant.StaticInitializer,
        "static { X = 42; }"
        );
  }

  /**
   * <pre>ConstructorDeclaration</pre>
   */
  @Test
  public void testClassBodyDeclarationConstructorDeclaration() {
    parseSanityCheck(
        ClassBodyDeclarationNode.Variant.ConstructorDeclaration,
        "Ctor() { super(); }"
        );
  }

  /**
   * <pre>FieldDeclaration</pre>
   */
  @Test
  public void testClassMemberDeclarationFieldDeclaration() {
    parseSanityCheck(
        ClassMemberDeclarationNode.Variant.FieldDeclaration,
        "private int x;"
        );
  }

  /**
   * <pre>MethodDeclaration</pre>
   */
  @Test
  public void testClassMemberDeclarationMethodDeclaration() {
    parseSanityCheck(
        ClassMemberDeclarationNode.Variant.MethodDeclaration,
        "@Override public String toString() { return \"\"; }"
        );
  }

  /**
   * <pre>ClassDeclaration</pre>
   */
  @Test
  public void testClassMemberDeclarationClassDeclaration() {
    parseSanityCheck(
        ClassMemberDeclarationNode.Variant.ClassDeclaration,
        "static class Inner { }"
        );
  }

  /**
   * <pre>InterfaceDeclaration</pre>
   */
  @Test
  public void testClassMemberDeclarationInterfaceDeclaration() {
    parseSanityCheck(
        ClassMemberDeclarationNode.Variant.InterfaceDeclaration,
        "interface IInner {}"
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testClassMemberDeclarationSem() {
    parseSanityCheck(
        ClassMemberDeclarationNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>{ FieldModifier } UnannType VariableDeclaratorList ";"</pre>
   */
  @Test
  public void testFieldDeclarationFieldModifierUnannTypeVariableDeclaratorListSem() {
    parseSanityCheck(
        FieldDeclarationNode.Variant.Declaration,
        "private final int x, y = 2, z;"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testFieldModifierAnnotation() {
    parseSanityCheck(
        FieldModifierNode.Variant.Annotation,
        "@VisibleForTesting"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testFieldModifierPublic() {
    parseSanityCheck(
        FieldModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"protected"</pre>
   */
  @Test
  public void testFieldModifierProtected() {
    parseSanityCheck(
        FieldModifierNode.Variant.Protected,
        "protected"
        );
  }

  /**
   * <pre>"private"</pre>
   */
  @Test
  public void testFieldModifierPrivate() {
    parseSanityCheck(
        FieldModifierNode.Variant.Private,
        "private"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testFieldModifierStatic() {
    parseSanityCheck(
        FieldModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"final"</pre>
   */
  @Test
  public void testFieldModifierFinal() {
    parseSanityCheck(
        FieldModifierNode.Variant.Final,
        "final"
        );
  }

  /**
   * <pre>"transient"</pre>
   */
  @Test
  public void testFieldModifierTransient() {
    parseSanityCheck(
        FieldModifierNode.Variant.Transient,
        "transient"
        );
  }

  /**
   * <pre>"volatile"</pre>
   */
  @Test
  public void testFieldModifierVolatile() {
    parseSanityCheck(
        FieldModifierNode.Variant.Volatile,
        "volatile"
        );
  }

  /**
   * <pre>VariableDeclarator { "," VariableDeclarator }</pre>
   */
  @Test
  public void testVariableDeclaratorListVariableDeclaratorComVariableDeclarator() {
    parseSanityCheck(
        VariableDeclaratorListNode.Variant.VariableDeclaratorComVariableDeclarator,
        "x, y"
        );
  }

  /**
   * <pre>VariableDeclaratorId [ "=" VariableInitializer ]</pre>
   */
  @Test
  public void testVariableDeclaratorVariableDeclaratorIdEqVariableInitializer() {
    parseSanityCheck(
        VariableDeclaratorNode.Variant.VariableDeclaratorIdEqVariableInitializer,
        "variable = 42"
        );
  }

  /**
   * <pre>Identifier [ Dims ]</pre>
   */
  @Test
  public void testVariableDeclaratorIdIdentifierDims() {
    parseSanityCheck(
        VariableDeclaratorIdNode.Variant.IdentifierDims,
        "arr[]"
        );
  }

  /**
   * <pre>Expression</pre>
   */
  @Test
  public void testVariableInitializerExpression() {
    parseSanityCheck(
        VariableInitializerNode.Variant.Expression,
        "null"
        );
  }

  /**
   * <pre>ArrayInitializer</pre>
   */
  @Test
  public void testVariableInitializerArrayInitializer() {
    parseSanityCheck(
        VariableInitializerNode.Variant.ArrayInitializer,
        "{ 0, 1, 2, }"
        );
  }

  /**
   * <pre>UnannPrimitiveType</pre>
   */
  @Test
  public void testUnannTypeUnannPrimitiveType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "short"
        );
  }

  /**
   * <pre>UnannReferenceType</pre>
   */
  @Test
  public void testUnannTypeUnannReferenceType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "Object"
        );
  }

  /**
   * <pre>NumericType</pre>
   */
  @Test
  public void testUnannPrimitiveTypeNumericType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "short"
        );
  }

  /**
   * <pre>"boolean"</pre>
   */
  @Test
  public void testUnannPrimitiveTypeBoolean() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "boolean"
        );
  }

  /**
   * <pre>UnannClassOrInterfaceType</pre>
   */
  @Test
  public void testUnannReferenceTypeUnannClassOrInterfaceType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "Object"
        );
  }

  /**
   * <pre>UnannTypeVariable</pre>
   */
  @Test
  public void testUnannReferenceTypeUnannTypeVariable() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "ELEMENT"
        // Cannot lexically distinguish type variables from ClassOrInterfaceType
        );
  }

  /**
   * <pre>UnannArrayType</pre>
   */
  @Test
  public void testUnannReferenceTypeUnannArrayType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "double[][]"
        );
  }

  /**
   * <pre>UnannClassType</pre>
   */
  @Test
  public void testUnannClassOrInterfaceTypeUnannClassType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "Foo"
        );
  }

  /**
   * <pre>UnannInterfaceType</pre>
   */
  @Test
  public void testUnannClassOrInterfaceTypeUnannInterfaceType() {
    parseSanityCheck(
        UnannClassTypeNode.Variant.NotAtClassType,
        "Bar<B>",
        // Cannot lexically distinguish Class types from interface types.
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>Identifier [ TypeArguments ]</pre>
   */
  @Test
  public void testUnannClassTypeIdentifierTypeArguments() {
    parseSanityCheck(
        UnannClassTypeNode.Variant.NotAtClassType,
        "List<String>"
        );
  }

  /**
   * <pre>UnannClassOrInterfaceType "." { Annotation } Identifier [ TypeArguments ]</pre>
   */
  @Test
  public void testUnannClassTypeUnannClassOrInterfaceTypeDotAnnotationIdentifierTypeArguments() {
    parseSanityCheck(
        UnannClassTypeNode.Variant.NotAtClassType,
        "ImmutableList.@Awesome Builder<T>",
        Fuzz.SAME_VARIANT  // Context free names
        );
  }

  /**
   * <pre>UnannClassType</pre>
   */
  @Test
  public void testUnannInterfaceTypeUnannClassType() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "Object"
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testUnannTypeVariableIdentifier() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "TYP"
        );
  }

  /**
   * <pre>UnannPrimitiveType Dims</pre>
   */
  @Test
  public void testUnannArrayTypeUnannPrimitiveTypeDims() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "boolean[]"
        );
  }

  /**
   * <pre>UnannClassOrInterfaceType Dims</pre>
   */
  @Test
  public void testUnannArrayTypeUnannClassOrInterfaceTypeDims() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "Object[][]"
        );
  }

  /**
   * <pre>UnannTypeVariable Dims</pre>
   */
  @Test
  public void testUnannArrayTypeUnannTypeVariableDims() {
    parseSanityCheck(
        UnannTypeNode.Variant.NotAtType,
        "TYP[][][]"
        );
  }

  /**
   * <pre>{ MethodModifier } MethodHeader MethodBody</pre>
   */
  @Test
  public void testMethodDeclarationMethodModifierMethodHeaderMethodBody() {
    parseSanityCheck(
        MethodDeclarationNode.Variant.Declaration,
        "public abstract void m(int i, long... j) throws Throwable;"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testMethodModifierAnnotation() {
    parseSanityCheck(
        MethodModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testMethodModifierPublic() {
    parseSanityCheck(
        MethodModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"protected"</pre>
   */
  @Test
  public void testMethodModifierProtected() {
    parseSanityCheck(
        MethodModifierNode.Variant.Protected,
        "protected"
        );
  }

  /**
   * <pre>"private"</pre>
   */
  @Test
  public void testMethodModifierPrivate() {
    parseSanityCheck(
        MethodModifierNode.Variant.Private,
        "private"
        );
  }

  /**
   * <pre>"abstract"</pre>
   */
  @Test
  public void testMethodModifierAbstract() {
    parseSanityCheck(
        MethodModifierNode.Variant.Abstract,
        "abstract"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testMethodModifierStatic() {
    parseSanityCheck(
        MethodModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"final"</pre>
   */
  @Test
  public void testMethodModifierFinal() {
    parseSanityCheck(
        MethodModifierNode.Variant.Final,
        "final"
        );
  }

  /**
   * <pre>"synchronized"</pre>
   */
  @Test
  public void testMethodModifierSynchronized() {
    parseSanityCheck(
        MethodModifierNode.Variant.Synchronized,
        "synchronized"
        );
  }

  /**
   * <pre>"native"</pre>
   */
  @Test
  public void testMethodModifierNative() {
    parseSanityCheck(
        MethodModifierNode.Variant.Native,
        "native"
        );
  }

  /**
   * <pre>"strictfp"</pre>
   */
  @Test
  public void testMethodModifierStrictfp() {
    parseSanityCheck(
        MethodModifierNode.Variant.Strictfp,
        "strictfp"
        );
  }

  /**
   * <pre>Result MethodDeclarator [ Throws ]</pre>
   */
  @Test
  public void testMethodHeaderResultMethodDeclaratorThrows() {
    parseSanityCheck(
        MethodHeaderNode.Variant.TypeParametersAnnotationResultMethodDeclaratorThrows,
        "int f(int n) throws java.lang.ArithmeticException"
        );
  }

  /**
   * <pre>TypeParameters { Annotation } Result MethodDeclarator [ Throws ]</pre>
   */
  @Test
  public void testMethodHeaderTypeParametersAnnotationResultMethodDeclaratorThrows() {
    parseSanityCheck(
        MethodHeaderNode.Variant.TypeParametersAnnotationResultMethodDeclaratorThrows,
        "<TYP extends CharSequence> @Nonnull String toString(TYP[] arr)"
        +"throws ClassCastException"
        );
  }

  /**
   * <pre>UnannType</pre>
   */
  @Test
  public void testResultUnannType() {
    parseSanityCheck(
        ResultNode.Variant.UnannType,
        "int"
        );
  }

  /**
   * <pre>"void"</pre>
   */
  @Test
  public void testResultVoid() {
    parseSanityCheck(
        ResultNode.Variant.Void,
        "void"
        );
  }

  /**
   * <pre>Identifier "(" [ FormalParameterList ] ")" [ Dims ]</pre>
   */
  @Test
  public void testMethodDeclaratorIdentifierLpFormalParameterListRpDims() {
    parseSanityCheck(
        MethodDeclaratorNode.Variant.MethodNameLpFormalParameterListRpDims,
        "foo()[]"
        );
  }

  /**
   * <pre>FormalParameters "," LastFormalParameter</pre>
   */
  @Test
  public void testFormalParameterListFormalParametersComLastFormalParameter() {
    parseSanityCheck(
        FormalParameterListNode.Variant.FormalParametersComLastFormalParameter,
        "int foo, double... bar"
        );
  }

  /**
   * <pre>LastFormalParameter</pre>
   */
  @Test
  public void testFormalParameterListLastFormalParameter() {
    parseSanityCheck(
        FormalParameterListNode.Variant.LastFormalParameter,
        "String... foo"
        );
  }

  /**
   * <pre>FormalParameter { "," FormalParameter }</pre>
   */
  @Test
  public void testFormalParametersFormalParameterComFormalParameter() {
    parseSanityCheck(
        FormalParametersNode.Variant.FormalParameterComFormalParameter,
        "int foo, double bar, Object baz"
        );
  }

  /**
   * <pre>ReceiverParameter { "," FormalParameter }</pre>
   */
  @Test
  public void testFormalParametersReceiverParameterComFormalParameter() {
    parseSanityCheck(
        FormalParametersNode.Variant.ReceiverParameterComFormalParameter,
        "@GuardedBy(\"g\") C this, int i"
        );
  }

  /**
   * <pre>{ VariableModifier } UnannType VariableDeclaratorId</pre>
   */
  @Test
  public void testFormalParameterVariableModifierUnannTypeVariableDeclaratorId() {
    parseSanityCheck(
        FormalParameterNode.Variant.Declaration,
        "@GuardedBy(\"g\") List<? super T> output"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testVariableModifierAnnotation() {
    parseSanityCheck(
        VariableModifierNode.Variant.Annotation,
        "@GuardedBy(\"g\")"
        );
  }

  /**
   * <pre>"final"</pre>
   */
  @Test
  public void testVariableModifierFinal() {
    parseSanityCheck(
        VariableModifierNode.Variant.Final,
        "final"
        );
  }

  /**
   * <pre>{ VariableModifier } UnannType { Annotation } "..." VariableDeclaratorId</pre>
   */
  @Test
  public void testLastFormalParameterVariableModifierUnannTypeAnnotationEllipVariableDeclaratorId() {
    parseSanityCheck(
        LastFormalParameterNode.Variant.Variadic,
        "final int... nums"
        );
  }

  /**
   * <pre>FormalParameter</pre>
   */
  @Test
  public void testLastFormalParameterFormalParameter() {
    parseSanityCheck(
        LastFormalParameterNode.Variant.FormalParameter,
        "int x"
        );
  }

  /**
   * <pre>{ Annotation } UnannType [ Identifier "." ] "this"</pre>
   */
  @Test
  public void testReceiverParameterAnnotationUnannTypeIdentifierDotThis() {
    parseSanityCheck(
        ReceiverParameterNode.Variant.AnnotationUnannTypeSimpleTypeNameDotThis,
        "@GuardedBy(\"lockName\") Enclosing Outer.this"
        );
  }

  /**
   * <pre>"throws" ExceptionTypeList</pre>
   */
  @Test
  public void testThrowsThrowsExceptionTypeList() {
    parseSanityCheck(
        ThrowsNode.Variant.ThrowsExceptionTypeList,
        "throws This, That"
        );
  }

  /**
   * <pre>ExceptionType { "," ExceptionType }</pre>
   */
  @Test
  public void testExceptionTypeListExceptionTypeComExceptionType() {
    parseSanityCheck(
        ExceptionTypeListNode.Variant.ExceptionTypeComExceptionType,
        "IOException, InterruptedException"
        );
  }

  /**
   * <pre>ClassType</pre>
   */
  @Test
  public void testExceptionTypeClassType() {
    parseSanityCheck(
        ExceptionTypeNode.Variant.ClassType,
        "IOException"
        );
  }

  /**
   * <pre>TypeVariable</pre>
   */
  @Test
  public void testExceptionTypeTypeVariable() {
    parseSanityCheck(
        ExceptionTypeNode.Variant.TypeVariable,
        "FAILURE_MODE",
        // Cannot lexically distinguish TypeVariable from ClassType
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>Block</pre>
   */
  @Test
  public void testMethodBodyBlock() {
    parseSanityCheck(
        MethodBodyNode.Variant.Block,
        "{ return; }"
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testMethodBodySem() {
    parseSanityCheck(
        MethodBodyNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>Block</pre>
   */
  @Test
  public void testInstanceInitializerBlock() {
    parseSanityCheck(
        InstanceInitializerNode.Variant.Block,
        "{ FOO = computeFoo(); }"
        );
  }

  /**
   * <pre>"static" Block</pre>
   */
  @Test
  public void testStaticInitializerStaticBlock() {
    parseSanityCheck(
        StaticInitializerNode.Variant.StaticBlock,
        "static {}"
        );
  }

  /**
   * <pre>{ ConstructorModifier } ConstructorDeclarator [ Throws ] ConstructorBody</pre>
   */
  @Test
  public void testConstructorDeclarationConstructorModifierConstructorDeclaratorThrowsConstructorBody() {
    parseSanityCheck(
        ConstructorDeclarationNode.Variant.Declaration,
        "public Ctor(int x) throws IllegalArgumentException { this.x = x; }"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testConstructorModifierAnnotation() {
    parseSanityCheck(
        ConstructorModifierNode.Variant.Annotation,
        "@VisibleForTesting"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testConstructorModifierPublic() {
    parseSanityCheck(
        ConstructorModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"protected"</pre>
   */
  @Test
  public void testConstructorModifierProtected() {
    parseSanityCheck(
        ConstructorModifierNode.Variant.Protected,
        "protected"
        );
  }

  /**
   * <pre>"private"</pre>
   */
  @Test
  public void testConstructorModifierPrivate() {
    parseSanityCheck(
        ConstructorModifierNode.Variant.Private,
        "private"
        );
  }

  /**
   * <pre>[ TypeParameters ] SimpleTypeName "(" [ FormalParameterList ] ")"</pre>
   */
  @Test
  public void testConstructorDeclaratorTypeParametersSimpleTypeNameLpFormalParameterListRp() {
    parseSanityCheck(
        ConstructorDeclaratorNode.Variant.TypeParametersSimpleTypeNameLpFormalParameterListRp,
        "<T> EnclosingClass(X x, T... y)"
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testSimpleTypeNameIdentifier() {
    parseSanityCheck(
        SimpleTypeNameNode.Variant.Identifier,
        "Object"
        );
  }

  /**
   * <pre>"{" [ ExplicitConstructorInvocation ] [ BlockStatements ] "}"</pre>
   */
  @Test
  public void testConstructorBodyLcExplicitConstructorInvocationBlockStatementsRc() {
    parseSanityCheck(
        ConstructorBodyNode.Variant.LcExplicitConstructorInvocationBlockStatementsRc,
        "{ super(42, x); this.y = y; }"
        );
  }

  /**
   * <pre>[ TypeArguments ] "this" "(" [ ArgumentList ] ")" ";"</pre>
   */
  @Test
  public void testExplicitConstructorInvocationTypeArgumentsThisLpArgumentListRpSem() {
    parseSanityCheck(
        ExplicitConstructorInvocationNode.Variant.TypeArgumentsThisLpArgumentListRpSem,
        "<T>this(x, null, null);"
        );
  }

  /**
   * <pre>[ TypeArguments ] "super" "(" [ ArgumentList ] ")" ";"</pre>
   */
  @Test
  public void testExplicitConstructorInvocationTypeArgumentsSuperLpArgumentListRpSem() {
    parseSanityCheck(
        ExplicitConstructorInvocationNode.Variant.TypeArgumentsSuperLpArgumentListRpSem,
        "super(x, y);"
        );
  }

  /**
   * <pre>ExpressionName "." [ TypeArguments ] "super" "(" [ ArgumentList ] ")" ";"</pre>
   */
  @Test
  public void testExplicitConstructorInvocationExpressionNameDotTypeArgumentsSuperLpArgumentListRpSem() {
    parseSanityCheck(
        ExplicitConstructorInvocationNode.Variant.PrimaryDotTypeArgumentsSuperLpArgumentListRpSem,
        "foo.<T>super (x , y);"
        );
  }

  /**
   * <pre>Primary "." [ TypeArguments ] "super" "(" [ ArgumentList ] ")" ";"</pre>
   */
  @Test
  public void testExplicitConstructorInvocationPrimaryDotTypeArgumentsSuperArgumentsSem() {
    parseSanityCheck(
        ExplicitConstructorInvocationNode.Variant.PrimaryDotTypeArgumentsSuperLpArgumentListRpSem,
        "(OuterInstanceFactory.get()).super(x);"
        );
  }

  /**
   * <pre>{ ClassModifier } "enum" Identifier [ Superinterfaces ] EnumBody</pre>
   */
  @Test
  public void testEnumDeclarationClassModifierEnumIdentifierSuperinterfacesEnumBody() {
    parseSanityCheck(
        EnumDeclarationNode.Variant.Declaration,
        "public enum Colors implements InappropriateUseOfEnum { Red, Blue }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>"{" [ EnumConstantList ] [ "," ] [ EnumBodyDeclarations ] "}"</pre>
   */
  @Test
  public void testEnumBodyLcEnumConstantListComEnumBodyDeclarationsRc() {
    parseSanityCheck(
        EnumBodyNode.Variant.LcEnumConstantListComEnumBodyDeclarationsRc,
        "{ FOO(), BAR(), }"
        );
    parseSanityCheck(
        EnumBodyNode.Variant.LcEnumConstantListComEnumBodyDeclarationsRc,
        "{ FOO, BAR }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>EnumConstant { "," EnumConstant }</pre>
   */
  @Test
  public void testEnumConstantListEnumConstantComEnumConstant() {
    parseSanityCheck(
        EnumConstantListNode.Variant.EnumConstantComEnumConstant,
        "Foo, Bar, Baz",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>{ EnumConstantModifier } Identifier [ "(" [ ArgumentList ] ")" ] [ ClassBody ]</pre>
   */
  @Test
  public void testEnumConstantEnumConstantModifierIdentifierArgumentsClassBody() {
    parseSanityCheck(
        EnumConstantNode.Variant.Declaration,
        "@Deprecated Foo(42) { @Override int f(int x) { return -x; }\n}"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testEnumConstantModifierAnnotation() {
    parseSanityCheck(
        EnumConstantModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>";" { ClassBodyDeclaration }</pre>
   */
  @Test
  public void testEnumBodyDeclarationsSemClassBodyDeclaration() {
    parseSanityCheck(
        EnumBodyDeclarationsNode.Variant.SemClassBodyDeclaration,
        ";\n  abstract public void foo();"
        );
  }

  /**
   * <pre>NormalInterfaceDeclaration</pre>
   */
  @Test
  public void testInterfaceDeclarationNormalInterfaceDeclaration() {
    parseSanityCheck(
        InterfaceDeclarationNode.Variant.NormalInterfaceDeclaration,
        "interface Collection<T> extends Iterable<T> { int size(); }"
        );
  }

  /**
   * <pre>AnnotationTypeDeclaration</pre>
   */
  @Test
  public void testInterfaceDeclarationAnnotationTypeDeclaration() {
    parseSanityCheck(
        InterfaceDeclarationNode.Variant.AnnotationTypeDeclaration,
        "@Retention(Retention.RUNTIME)\npublic @interface Foo { }"
        );
  }

  /**
   * <pre>{ InterfaceModifier } "interface" Identifier [ TypeParameters ] [ ExtendsInterfaces ] InterfaceBody</pre>
   */
  @Test
  public void testNormalInterfaceDeclarationInterfaceModifierInterfaceIdentifierTypeParametersExtendsInterfacesInterfaceBody() {
    parseSanityCheck(
        NormalInterfaceDeclarationNode.Variant.Declaration,
        "interface IFace<T> {}"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testInterfaceModifierAnnotation() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testInterfaceModifierPublic() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"protected"</pre>
   */
  @Test
  public void testInterfaceModifierProtected() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Protected,
        "protected"
        );
  }

  /**
   * <pre>"private"</pre>
   */
  @Test
  public void testInterfaceModifierPrivate() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Private,
        "private"
        );
  }

  /**
   * <pre>"abstract"</pre>
   */
  @Test
  public void testInterfaceModifierAbstract() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Abstract,
        "abstract"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testInterfaceModifierStatic() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"strictfp"</pre>
   */
  @Test
  public void testInterfaceModifierStrictfp() {
    parseSanityCheck(
        InterfaceModifierNode.Variant.Strictfp,
        "strictfp"
        );
  }

  /**
   * <pre>"extends" InterfaceTypeList</pre>
   */
  @Test
  public void testExtendsInterfacesExtendsInterfaceTypeList() {
    parseSanityCheck(
        ExtendsInterfacesNode.Variant.ExtendsInterfaceTypeList,
        "extends Comparable<ThisType<T>>"
        );
  }

  /**
   * <pre>"{" { InterfaceMemberDeclaration } "}"</pre>
   */
  @Test
  public void testInterfaceBodyLcInterfaceMemberDeclarationRc() {
    parseSanityCheck(
        InterfaceBodyNode.Variant.LcInterfaceMemberDeclarationRc,
        "{ void foo(); }"
        );
  }

  /**
   * <pre>ConstantDeclaration</pre>
   */
  @Test
  public void testInterfaceMemberDeclarationConstantDeclaration() {
    parseSanityCheck(
        InterfaceMemberDeclarationNode.Variant.ConstantDeclaration,
        "public static final double PI = 3.14;"
        );
  }

  /**
   * <pre>InterfaceMethodDeclaration</pre>
   */
  @Test
  public void testInterfaceMemberDeclarationInterfaceMethodDeclaration() {
    parseSanityCheck(
        InterfaceMemberDeclarationNode.Variant.InterfaceMethodDeclaration,
        "void foo();"
        );
  }

  /**
   * <pre>ClassDeclaration</pre>
   */
  @Test
  public void testInterfaceMemberDeclarationClassDeclaration() {
    parseSanityCheck(
        InterfaceMemberDeclarationNode.Variant.ClassDeclaration,
        "static final class Builders { /* ... */ }"
        );
  }

  /**
   * <pre>InterfaceDeclaration</pre>
   */
  @Test
  public void testInterfaceMemberDeclarationInterfaceDeclaration() {
    parseSanityCheck(
        InterfaceMemberDeclarationNode.Variant.InterfaceDeclaration,
        "interface IInner {}"
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testInterfaceMemberDeclarationSem() {
    parseSanityCheck(
        InterfaceMemberDeclarationNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>{ ConstantModifier } UnannType VariableDeclaratorList ";"</pre>
   */
  @Test
  public void testConstantDeclarationConstantModifierUnannTypeVariableDeclaratorListSem() {
    parseSanityCheck(
        ConstantDeclarationNode.Variant.Declaration,
        "static final int FOUR = 4, FIVE = 5, SIX = 6;"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testConstantModifierAnnotation() {
    parseSanityCheck(
        ConstantModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testConstantModifierPublic() {
    parseSanityCheck(
        ConstantModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testConstantModifierStatic() {
    parseSanityCheck(
        ConstantModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"final"</pre>
   */
  @Test
  public void testConstantModifierFinal() {
    parseSanityCheck(
        ConstantModifierNode.Variant.Final,
        "final"
        );
  }

  /**
   * <pre>{ InterfaceMethodModifier } MethodHeader MethodBody</pre>
   */
  @Test
  public void testInterfaceMethodDeclarationInterfaceMethodModifierMethodHeaderMethodBody() {
    parseSanityCheck(
        InterfaceMethodDeclarationNode.Variant.Declaration,
        "public void foo();"
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testInterfaceMethodModifierAnnotation() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Annotation,
        "@Deprecated"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testInterfaceMethodModifierPublic() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"abstract"</pre>
   */
  @Test
  public void testInterfaceMethodModifierAbstract() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Abstract,
        "abstract"
        );
  }

  /**
   * <pre>"default"</pre>
   */
  @Test
  public void testInterfaceMethodModifierDefault() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Default,
        "default"
        );
  }

  /**
   * <pre>"static"</pre>
   */
  @Test
  public void testInterfaceMethodModifierStatic() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Static,
        "static"
        );
  }

  /**
   * <pre>"strictfp"</pre>
   */
  @Test
  public void testInterfaceMethodModifierStrictfp() {
    parseSanityCheck(
        InterfaceMethodModifierNode.Variant.Strictfp,
        "strictfp"
        );
  }

  /**
   * <pre>{ InterfaceModifier } "@" "interface" Identifier AnnotationTypeBody</pre>
   */
  @Test
  public void testAnnotationTypeDeclarationInterfaceModifierAtInterfaceIdentifierAnnotationTypeBody() {
    parseSanityCheck(
        AnnotationTypeDeclarationNode.Variant.Declaration,
        "@interface AnnType { String text(); }"
        );
  }

  /**
   * <pre>"{" { AnnotationTypeMemberDeclaration } "}"</pre>
   */
  @Test
  public void testAnnotationTypeBodyLcAnnotationTypeMemberDeclarationRc() {
    parseSanityCheck(
        AnnotationTypeBodyNode.Variant.LcAnnotationTypeMemberDeclarationRc,
        "{ MyEnum myEnum() default MyEnum.DEFAULT; }"
        );
  }

  /**
   * <pre>AnnotationTypeElementDeclaration</pre>
   */
  @Test
  public void testAnnotationTypeMemberDeclarationAnnotationTypeElementDeclaration() {
    parseSanityCheck(
        AnnotationTypeMemberDeclarationNode.Variant.AnnotationTypeElementDeclaration,
        "MyEnum myEnum() default MyEnum.DEFAULT;"
        );
  }

  /**
   * <pre>ConstantDeclaration</pre>
   */
  @Test
  public void testAnnotationTypeMemberDeclarationConstantDeclaration() {
    parseSanityCheck(
        AnnotationTypeMemberDeclarationNode.Variant.ConstantDeclaration,
        "static final int CONSTANT = 0;"
        );
  }

  /**
   * <pre>ClassDeclaration</pre>
   */
  @Test
  public void testAnnotationTypeMemberDeclarationClassDeclaration() {
    parseSanityCheck(
        AnnotationTypeMemberDeclarationNode.Variant.ClassDeclaration,
        "@SuppressWarnings(\"static\") static final class C { private C() {} }"
        );
  }

  /**
   * <pre>InterfaceDeclaration</pre>
   */
  @Test
  public void testAnnotationTypeMemberDeclarationInterfaceDeclaration() {
    parseSanityCheck(
        AnnotationTypeMemberDeclarationNode.Variant.InterfaceDeclaration,
        "interface IFace {}"
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testAnnotationTypeMemberDeclarationSem() {
    parseSanityCheck(
        AnnotationTypeMemberDeclarationNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>{ AnnotationTypeElementModifier } UnannType Identifier "(" ")" [ Dims ] [ DefaultValue ] ";"</pre>
   */
  @Test
  public void testAnnotationTypeElementDeclarationAnnotationTypeElementModifierUnannTypeIdentifierLpRpDimsDefaultValueSem() {
    parseSanityCheck(
        AnnotationTypeElementDeclarationNode.Variant.Declaration,
        "String str() [] default {,};"
        );
    parseSanityCheck(
        AnnotationTypeElementDeclarationNode.Variant.Declaration,
        "String str() [] default {};",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testAnnotationTypeElementModifierAnnotation() {
    parseSanityCheck(
        AnnotationTypeElementModifierNode.Variant.Annotation,
        "@Retention(retention=RUNTIME)"
        );
  }

  /**
   * <pre>"public"</pre>
   */
  @Test
  public void testAnnotationTypeElementModifierPublic() {
    parseSanityCheck(
        AnnotationTypeElementModifierNode.Variant.Public,
        "public"
        );
  }

  /**
   * <pre>"abstract"</pre>
   */
  @Test
  public void testAnnotationTypeElementModifierAbstract() {
    parseSanityCheck(
        AnnotationTypeElementModifierNode.Variant.Abstract,
        "abstract"
        );
  }

  /**
   * <pre>"default" ElementValue</pre>
   */
  @Test
  public void testDefaultValueDefaultElementValue() {
    parseSanityCheck(
        DefaultValueNode.Variant.DefaultElementValue,
        "default 42"
        );
  }

  /**
   * <pre>NormalAnnotation</pre>
   */
  @Test
  public void testAnnotationNormalAnnotation() {
    parseSanityCheck(
        AnnotationNode.Variant.NormalAnnotation,
        "@Normal()"
        );
  }

  /**
   * <pre>MarkerAnnotation</pre>
   */
  @Test
  public void testAnnotationMarkerAnnotation() {
    parseSanityCheck(
        AnnotationNode.Variant.MarkerAnnotation,
        "@Marker"
        );
  }

  /**
   * <pre>SingleElementAnnotation</pre>
   */
  @Test
  public void testAnnotationSingleElementAnnotation() {
    parseSanityCheck(
        AnnotationNode.Variant.SingleElementAnnotation,
        "@SingleValue(VALUE)"
        );
  }

  /**
   * <pre>"@" TypeName "(" [ ElementValuePairList ] ")"</pre>
   */
  @Test
  public void testNormalAnnotationAtTypeNameLpElementValuePairListRp() {
    parseSanityCheck(
        NormalAnnotationNode.Variant.AtTypeNameLpElementValuePairListRp,
        "@Ann(foo=1,bar=2)"
        );
  }

  /**
   * <pre>ElementValuePair { "," ElementValuePair }</pre>
   */
  @Test
  public void testElementValuePairListElementValuePairComElementValuePair() {
    parseSanityCheck(
        ElementValuePairListNode.Variant.ElementValuePairComElementValuePair,
        "x=42, y=false, z=MyEnum.MEMBER"
        );
  }

  /**
   * <pre>Identifier "=" ElementValue</pre>
   */
  @Test
  public void testElementValuePairIdentifierEqElementValue() {
    parseSanityCheck(
        ElementValuePairNode.Variant.IdentifierEqElementValue,
        "x=42"
        );
  }

  /**
   * <pre>ConditionalExpression</pre>
   */
  @Test
  public void testElementValueConditionalExpression() {
    parseSanityCheck(
        ElementValueNode.Variant.ConditionalExpression,
        "a ? b : c"
        );
  }

  /**
   * <pre>ElementValueArrayInitializer</pre>
   */
  @Test
  public void testElementValueElementValueArrayInitializer() {
    parseSanityCheck(
        ElementValueNode.Variant.ElementValueArrayInitializer,
        "{ \"foo\", \"bar\", \"baz\", }"
        );
    parseSanityCheck(
        ElementValueNode.Variant.ElementValueArrayInitializer,
        "{ \"foo\", \"bar\", \"baz\" }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>Annotation</pre>
   */
  @Test
  public void testElementValueAnnotation() {
    parseSanityCheck(
        ElementValueNode.Variant.Annotation,
        "@Annot"
        );
  }

  /**
   * <pre>"{" [ ElementValueList ] [ "," ] "}"</pre>
   */
  @Test
  public void testElementValueArrayInitializerLcElementValueListComRc() {
    parseSanityCheck(
        ElementValueArrayInitializerNode.Variant.LcElementValueListComRc,
        "{ 1, 2, 3, }"
        );
  }

  /**
   * <pre>ElementValue { "," ElementValue }</pre>
   */
  @Test
  public void testElementValueListElementValueComElementValue() {
    parseSanityCheck(
        ElementValueListNode.Variant.ElementValueComElementValue,
        "1.0, 2.0, -3.0"
        );
  }

  /**
   * <pre>"@" TypeName</pre>
   */
  @Test
  public void testMarkerAnnotationAtTypeName() {
    parseSanityCheck(
        MarkerAnnotationNode.Variant.AtTypeName,
        "@AnnotationName"
        );
  }

  /**
   * <pre>"@" TypeName "(" ElementValue ")"</pre>
   */
  @Test
  public void testSingleElementAnnotationAtTypeNameLpElementValueRp() {
    parseSanityCheck(
        SingleElementAnnotationNode.Variant.AtTypeNameLpElementValueRp,
        "@Single(42)"
        );
  }

  /**
   * <pre>"{" [ VariableInitializerList ] [ "," ] "}"</pre>
   */
  @Test
  public void testArrayInitializerLcVariableInitializerListComRc() {
    parseSanityCheck(
        ArrayInitializerNode.Variant.LcVariableInitializerListComRc,
        "{ { }, { 123 } }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>VariableInitializer { "," VariableInitializer }</pre>
   */
  @Test
  public void testVariableInitializerListVariableInitializerComVariableInitializer() {
    parseSanityCheck(
        VariableInitializerListNode.Variant.VariableInitializerComVariableInitializer,
        "123, 456"
        );
  }

  /**
   * <pre>"{" [ BlockStatements ] "}"</pre>
   */
  @Test
  public void testBlockLcBlockStatementsRc() {
    parseSanityCheck(
        BlockNode.Variant.LcBlockStatementsRc,
        "{ break; }"
        );
  }

  /**
   * <pre>BlockStatement { BlockStatement }</pre>
   */
  @Test
  public void testBlockStatementsBlockStatementBlockStatement() {
    parseSanityCheck(
        BlockStatementsNode.Variant.BlockStatementBlockStatementBlockTypeScope,
        "foo(); bar(); baz();"
        );
  }

  /**
   * <pre>LocalVariableDeclarationStatement</pre>
   */
  @Test
  public void testBlockStatementLocalVariableDeclarationStatement() {
    parseSanityCheck(
        BlockStatementNode.Variant.LocalVariableDeclarationStatement,
        "int x;"
        );
  }

  /**
   * <pre>ClassDeclaration</pre>
   */
  @Test
  public void testBlockStatementsClassDeclaration() {
    parseSanityCheck(
        BlockStatementsNode.Variant.BlockTypeScope,
        "class Inner {}"
        );
  }

  /**
   * <pre>Statement</pre>
   */
  @Test
  public void testBlockStatementStatement() {
    parseSanityCheck(
        BlockStatementNode.Variant.Statement,
        "throw new Foo();"
        );
  }

  /**
   * <pre>LocalVariableDeclaration ";"</pre>
   */
  @Test
  public void testLocalVariableDeclarationStatementLocalVariableDeclarationSem() {
    parseSanityCheck(
        LocalVariableDeclarationStatementNode.Variant.LocalVariableDeclarationSem,
        "int x = 42;"
        );
  }

  /**
   * <pre>{ VariableModifier } UnannType VariableDeclaratorList</pre>
   */
  @Test
  public void testLocalVariableDeclarationVariableModifierUnannTypeVariableDeclaratorList() {
    parseSanityCheck(
        LocalVariableDeclarationNode.Variant.Declaration,
        "final String s = foo(), t = bar()"
        );
  }


  /**
   * <pre>LabeledStatement</pre>
   */
  @Test
  public void testStatementLabeledStatement() {
    parseSanityCheck(
        StatementNode.Variant.LabeledStatement,
        "foo: while (true) { if (bar()) { break foo; }; }"
        );
  }

  /**
   * <pre>IfThenStatement</pre>
   */
  @Test
  public void testStatementIfThenStatement() {
    parseSanityCheck(
        StatementNode.Variant.IfStatement,
        "if (foo()) bar();"
        );
  }

  /**
   * <pre>IfThenElseStatement</pre>
   */
  @Test
  public void testStatementIfThenElseStatement() {
    parseSanityCheck(
        StatementNode.Variant.IfStatement,
        "if (c) then(); else alt();"
        );
  }

  /**
   * <pre>WhileStatement</pre>
   */
  @Test
  public void testStatementWhileStatement() {
    parseSanityCheck(
        StatementNode.Variant.WhileStatement,
        "while (cond) if (otherCond) body();"
        );
  }

  /**
   * <pre>ForStatement</pre>
   */
  @Test
  public void testStatementForStatement() {
    parseSanityCheck(
        StatementNode.Variant.ForStatement,
        "for (int i = 0; i < n; ++i) op();"
        );
  }

  /**
   * <pre>StatementWithoutTrailingSubstatement</pre>
   */
  @Test
  public void testStatementNoShortIfStatementWithoutTrailingSubstatement() {
    parseSanityCheck(
        StatementNode.Variant.SynchronizedStatement,
        "synchronized (mutex) { critSec(); }"
        );
  }

  /**
   * <pre>LabeledStatementNoShortIf</pre>
   */
  @Test
  public void testStatementNoShortIfLabeledStatementNoShortIf() {
    parseSanityCheck(
        StatementNode.Variant.LabeledStatement,
        "foo: { if (x) break foo; bar(); }"
        );
  }

  /**
   * <pre>IfThenElseStatementNoShortIf</pre>
   */
  @Test
  public void testStatementNoShortIfIfThenElseStatementNoShortIf() {
    parseSanityCheck(
        StatementNode.Variant.IfStatement,
        "if (cond) foo(); else bar();"
        );
  }

  /**
   * <pre>WhileStatementNoShortIf</pre>
   */
  @Test
  public void testStatementNoShortIfWhileStatementNoShortIf() {
    parseSanityCheck(
        StatementNode.Variant.WhileStatement,
        "while (a != b) { op(); }"
        );
  }

  /**
   * <pre>ForStatementNoShortIf</pre>
   */
  @Test
  public void testStatementNoShortIfForStatementNoShortIf() {
    parseSanityCheck(
        StatementNode.Variant.ForStatement,
        "for (;;) { op(); }"
        );
  }

  /**
   * <pre>Block</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementBlock() {
    parseSanityCheck(
        StatementNode.Variant.Block,
        "{ b(); l(); o(); c(); k(); }"
        );
  }

  /**
   * <pre>EmptyStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementEmptyStatement() {
    parseSanityCheck(
        StatementNode.Variant.EmptyStatement,
        ";"
        );
  }

  /**
   * <pre>ExpressionStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementExpressionStatement() {
    parseSanityCheck(
        StatementNode.Variant.ExpressionStatement,
        "a = b;"
        );
  }

  /**
   * <pre>AssertStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementAssertStatement() {
    parseSanityCheck(
        StatementNode.Variant.AssertStatement,
        "assert cond : message;"
        );
  }

  /**
   * <pre>SwitchStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementSwitchStatement() {
    parseSanityCheck(
        StatementNode.Variant.SwitchStatement,
        "switch (foo) {\n"
        + "  case \"foo\": case \"FOO\": foo(); break;\n"
        + "  case \"BAR\":               bar(); break;\n"
        + "  default: bar();\n"
        + "}"
        );
  }

  /**
   * <pre>DoStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementDoStatement() {
    parseSanityCheck(
        StatementNode.Variant.DoStatement,
        "do it(); while (cond);"
        );
  }

  /**
   * <pre>BreakStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementBreakStatement() {
    parseSanityCheck(
        StatementNode.Variant.BreakStatement,
        "break label;"
        );
  }

  /**
   * <pre>ContinueStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementContinueStatement() {
    parseSanityCheck(
        StatementNode.Variant.ContinueStatement,
        "continue label;"
        );
  }

  /**
   * <pre>ReturnStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementReturnStatement() {
    parseSanityCheck(
        StatementNode.Variant.ReturnStatement,
        "return expr;"
        );
  }

  /**
   * <pre>SynchronizedStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementSynchronizedStatement() {
    parseSanityCheck(
        StatementNode.Variant.SynchronizedStatement,
        "synchronized (this) { this.op(); }"
        );
  }

  /**
   * <pre>ThrowStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementThrowStatement() {
    parseSanityCheck(
        StatementNode.Variant.ThrowStatement,
        "throw myException;"
        );
  }

  /**
   * <pre>TryStatement</pre>
   */
  @Test
  public void testStatementWithoutTrailingSubstatementTryStatement() {
    parseSanityCheck(
        StatementNode.Variant.TryStatement,
        "try (InputStream in = open(it)) { useResource(in); }",
        Fuzz.IMPLIED_TOKENS  // semi after open(it)
        );
  }

  /**
   * <pre>";"</pre>
   */
  @Test
  public void testEmptyStatementSem() {
    parseSanityCheck(
        EmptyStatementNode.Variant.Sem,
        ";"
        );
  }

  /**
   * <pre>Identifier ":" Statement</pre>
   */
  @Test
  public void testLabeledStatementIdentifierClnStatement() {
    parseSanityCheck(
        LabeledStatementNode.Variant.LabelClnStatement,
        "label: if (danger) panic();"
        );
  }

  /**
   * <pre>Identifier ":" StatementNoShortIf</pre>
   */
  @Test
  public void testLabeledStatementNoShortIfIdentifierClnStatementNoShortIf() {
    parseSanityCheck(
        LabeledStatementNode.Variant.LabelClnStatement,
        "label: if (danger) { panic(); } else {}"
        );
  }

  /**
   * <pre>StatementExpression ";"</pre>
   */
  @Test
  public void testExpressionStatementStatementExpressionSem() {
    parseSanityCheck(
        ExpressionStatementNode.Variant.StatementExpressionSem,
        "foo();"
        );
  }

  /**
   * <pre>Assignment</pre>
   */
  @Test
  public void testStatementExpressionAssignment() {
    parseSanityCheck(
        StatementExpressionNode.Variant.Assignment,
        "a = b"
        );
  }

  /**
   * <pre>PreExpression</pre>
   */
  @Test
  public void testStatementExpressionPreExpression() {
    parseSanityCheck(
        StatementExpressionNode.Variant.PreExpression,
        "++x"
        );
  }

  /**
   * <pre>PostExpression</pre>
   */
  @Test
  public void testStatementExpressionPostExpression() {
    parseSanityCheck(
        StatementExpressionNode.Variant.PostExpression,
        "arr[i]--"
        );
  }

  /**
   * <pre>MethodInvocation</pre>
   */
  @Test
  public void testStatementExpressionMethodInvocation() {
    parseSanityCheck(
        StatementExpressionNode.Variant.MethodInvocation,
        "foo.bar(baz)"
        );
  }

  /**
   * <pre>ClassInstanceCreationExpression</pre>
   */
  @Test
  public void testStatementExpressionClassInstanceCreationExpression() {
    parseSanityCheck(
        StatementExpressionNode.Variant.ClassInstanceCreationExpression,
        "new Foo()"
        );
  }

  /**
   * <pre>"if" "(" Expression ")" Statement</pre>
   */
  @Test
  public void testIfThenStatementIfLpExpressionRpStatement() {
    parseSanityCheck(
        IfStatementNode.Variant.IfLpExpressionRpStatementNotElse,
        "if (cond) foo();"
        );
  }

  /**
   * <pre>"if" "(" Expression ")" StatementNoShortIf "else" Statement</pre>
   */
  @Test
  public void testIfThenElseStatementIfLpExpressionRpStatementNoShortIfElseStatement() {
    parseSanityCheck(
        IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement,
        "if (cond) foo(); else if (bar()) baz();"
        );
  }

  /**
   * <pre>"if" "(" Expression ")" StatementNoShortIf "else" StatementNoShortIf</pre>
   */
  @Test
  public void testIfThenElseStatementNoShortIfIfLpExpressionRpStatementNoShortIfElseStatementNoShortIf() {
    parseSanityCheck(
        IfStatementNode.Variant.IfLpExpressionRpStatementElseStatement,
        "if (cond) foo(); else {}"
        );
  }

  /**
   * <pre>"assert" Expression [ ":" Expression ] ";"</pre>
   */
  @Test
  public void testAssertStatementAssertExpressionClnExpressionSem() {
    parseSanityCheck(
        AssertStatementNode.Variant.AssertExpressionClnExpressionSem,
        "assert cond;"
        );
  }

  /**
   * <pre>"switch" "(" Expression ")" SwitchBlock</pre>
   */
  @Test
  public void testSwitchStatementSwitchLpExpressionRpSwitchBlock() {
    parseSanityCheck(
        SwitchStatementNode.Variant.SwitchLpExpressionRpSwitchBlock,
        "switch (e) { case Foo: foo(); break; case Bar: bar(); }"
        );
  }

  /**
   * <pre>"{" { SwitchBlockStatementGroup } { SwitchLabel } "}"</pre>
   */
  @Test
  public void testSwitchBlockLcSwitchBlockStatementGroupSwitchLabelRc() {
    parseSanityCheck(
        SwitchBlockNode.Variant.LcSwitchBlockStatementGroupRc,
        "{ default: d(); break outer; case 1: case 2: case 3: c(); }"
        );
  }

  /**
   * <pre>SwitchLabels BlockStatements</pre>
   */
  @Test
  public void testSwitchBlockStatementGroupSwitchLabelsBlockStatements() {
    parseSanityCheck(
        SwitchBlockStatementGroupNode.Variant.SwitchLabelsBlockStatements,
        "case 1: case 2: case 3: foo(); bar(); if (baz()) boo();"
        );
  }

  /**
   * <pre>SwitchLabel { SwitchLabel }</pre>
   */
  @Test
  public void testSwitchLabelsSwitchLabelSwitchLabel() {
    parseSanityCheck(
        SwitchLabelsNode.Variant.SwitchLabelSwitchLabel,
        "case 1: case 2: case 3:"
        );
  }

  /**
   * <pre>"case" ConstantExpression ":"</pre>
   */
  @Test
  public void testSwitchLabelCaseConstantExpressionCln() {
    parseSanityCheck(
        SwitchLabelNode.Variant.CaseCaseValueCln,
        "case '\\n':"
        );
  }

  /**
   * <pre>"case" EnumConstantName ":"</pre>
   */
  @Test
  public void testSwitchLabelCaseEnumConstantNameCln() {
    parseSanityCheck(
        SwitchLabelNode.Variant.CaseCaseValueCln,
        // Lexically ambiguous between CaseConstant and ConstantExpression
        "case Foo:"
        );
  }

  /**
   * <pre>"default" ":"</pre>
   */
  @Test
  public void testSwitchLabelDefaultCln() {
    parseSanityCheck(
        SwitchLabelNode.Variant.DefaultCln,
        "default :"
        );
  }

  /**
   * <pre>Identifier</pre>
   */
  @Test
  public void testEnumConstantNameIdentifier() {
    parseSanityCheck(
        EnumConstantNameNode.Variant.FieldName,
        "MyEnumMember"
        );
  }

  /**
   * <pre>"while" "(" Expression ")" Statement</pre>
   */
  @Test
  public void testWhileStatementWhileLpExpressionRpStatement() {
    parseSanityCheck(
        WhileStatementNode.Variant.WhileLpExpressionRpStatement,
        "while (cond) if (othercond) action();"
        );
  }

  /**
   * <pre>"while" "(" Expression ")" StatementNoShortIf</pre>
   */
  @Test
  public void testWhileStatementNoShortIfWhileLpExpressionRpStatementNoShortIf() {
    parseSanityCheck(
        WhileStatementNode.Variant.WhileLpExpressionRpStatement,
        "while (cond) if (othercond) { action(); } else ;"
        );
  }

  /**
   * <pre>"do" Statement "while" "(" Expression ")" ";"</pre>
   */
  @Test
  public void testDoStatementDoStatementWhileLpExpressionRpSem() {
    parseSanityCheck(
        DoStatementNode.Variant.DoStatementWhileLpExpressionRpSem,
        "do body(); while (cond);"
        );
  }

  /**
   * <pre>BasicForStatement</pre>
   */
  @Test
  public void testForStatementBasicForStatement() {
    parseSanityCheck(
        ForStatementNode.Variant.BasicForStatement,
        "for (;;) sideeff();"
        );
  }

  /**
   * <pre>EnhancedForStatement</pre>
   */
  @Test
  public void testForStatementEnhancedForStatement() {
    parseSanityCheck(
        ForStatementNode.Variant.EnhancedForStatement,
        "for (T el : iterable) if (cond(el)) op(el);"
        );
  }

  /**
   * <pre>BasicForStatementNoShortIf</pre>
   */
  @Test
  public void testForStatementNoShortIfBasicForStatementNoShortIf() {
    parseSanityCheck(
        ForStatementNode.Variant.BasicForStatement,
        "for (i = 0, n = els.length; i < n; ++i)"
        + " { if (cond(els[i])) op(el[i]); }"
        );
  }

  /**
   * <pre>EnhancedForStatementNoShortIf</pre>
   */
  @Test
  public void testForStatementNoShortIfEnhancedForStatementNoShortIf() {
    parseSanityCheck(
        ForStatementNode.Variant.EnhancedForStatement,
        "for (T el : iterable) { if (cond(el)) op(el); }"
        );
  }

  /**
   * <pre>"for" "(" [ ForInit ] ";" [ Expression ] ";" [ ForUpdate ] ")" Statement</pre>
   */
  @Test
  public void testBasicForStatementForLpForInitSemExpressionSemForUpdateRpStatement() {
    parseSanityCheck(
        BasicForStatementNode.Variant.ForLpForInitSemExpressionSemForUpdateRpStatement,
        "for (;;) if (cond) doOne();"
        );
  }

  /**
   * <pre>"for" "(" [ ForInit ] ";" [ Expression ] ";" [ ForUpdate ] ")" StatementNoShortIf</pre>
   */
  @Test
  public void testBasicForStatementNoShortIfForLpForInitSemExpressionSemForUpdateRpStatementNoShortIf() {
    parseSanityCheck(
        BasicForStatementNode.Variant.ForLpForInitSemExpressionSemForUpdateRpStatement,
        "for (;;) foo();"
        );
  }

  /**
   * <pre>StatementExpressionList</pre>
   */
  @Test
  public void testForInitStatementExpressionList() {
    parseSanityCheck(
        ForInitNode.Variant.StatementExpressionList,
        "a = b, c = d, e++"
        );
  }

  /**
   * <pre>LocalVariableDeclaration</pre>
   */
  @Test
  public void testForInitLocalVariableDeclaration() {
    parseSanityCheck(
        ForInitNode.Variant.LocalVariableDeclaration,
        "int i = 0, n = arr.length"
        );
  }

  /**
   * <pre>StatementExpressionList</pre>
   */
  @Test
  public void testForUpdateStatementExpressionList() {
    parseSanityCheck(
        ForUpdateNode.Variant.StatementExpressionList,
        "i++, --j"
        );
  }

  /**
   * <pre>StatementExpression { "," StatementExpression }</pre>
   */
  @Test
  public void testStatementExpressionListStatementExpressionComStatementExpression() {
    parseSanityCheck(
        StatementExpressionListNode.Variant.StatementExpressionComStatementExpression,
        "++i, j += 2"
        );
  }

  /**
   * <pre>"for" "(" { VariableModifier } UnannType VariableDeclaratorId ":" Expression ")" Statement</pre>
   */
  @Test
  public void testEnhancedForStatementForLpVariableModifierUnannTypeVariableDeclaratorIdClnExpressionRpStatement() {
    parseSanityCheck(
        EnhancedForStatementNode.Variant.EnhancedForStatement,
        "for (final T el : iterable) if (filter(el)) use(el);"
        );
  }

  /**
   * <pre>"for" "(" { VariableModifier } UnannType VariableDeclaratorId ":" Expression ")" StatementNoShortIf</pre>
   */
  @Test
  public void testEnhancedForStatementNoShortIfForLpVariableModifierUnannTypeVariableDeclaratorIdClnExpressionRpStatementNoShortIf() {
    parseSanityCheck(
        EnhancedForStatementNode.Variant.EnhancedForStatement,
        "for (final T el : iterable) { if (filter(el)) use(el); }"
        );
  }

  /**
   * <pre>"break" [ Identifier ] ";"</pre>
   */
  @Test
  public void testBreakStatementBreakIdentifierSem() {
    parseSanityCheck(
        BreakStatementNode.Variant.BreakLabelSem,
        "break out;"
        );
  }

  /**
   * <pre>"continue" [ Identifier ] ";"</pre>
   */
  @Test
  public void testContinueStatementContinueIdentifierSem() {
    parseSanityCheck(
        ContinueStatementNode.Variant.ContinueLabelSem,
        "continue;"
        );
  }

  /**
   * <pre>"return" [ Expression ] ";"</pre>
   */
  @Test
  public void testReturnStatementReturnExpressionSem() {
    parseSanityCheck(
        ReturnStatementNode.Variant.ReturnExpressionSem,
        "return;"
        );
  }

  /**
   * <pre>"throw" Expression ";"</pre>
   */
  @Test
  public void testThrowStatementThrowExpressionSem() {
    parseSanityCheck(
        ThrowStatementNode.Variant.ThrowExpressionSem,
        "throw ex;"
        );
  }

  /**
   * <pre>"synchronized" "(" Expression ")" Block</pre>
   */
  @Test
  public void testSynchronizedStatementSynchronizedLpExpressionRpBlock() {
    parseSanityCheck(
        SynchronizedStatementNode.Variant.SynchronizedLpExpressionRpBlock,
        "synchronized( mutex) { mutex.wait(); }"
        );
  }

  /**
   * <pre>"try" Block Catches</pre>
   */
  @Test
  public void testTryStatementTryBlockCatches() {
    parseSanityCheck(
        TryStatementNode.Variant.TryBlockCatches,
        "try { foo(); }"
        + " catch (E e) { handle(e); }"
        + " catch (F | G f) { handle(f); }"
        );
  }

  /**
   * <pre>"try" Block [ Catches ] Finally</pre>
   */
  @Test
  public void testTryStatementTryBlockCatchesFinally() {
    parseSanityCheck(
        TryStatementNode.Variant.TryBlockCatchesFinally,
        "try { b(); } catch (E e) { handle(e); } finally { recover(); }"
        );
  }

  /**
   * <pre>TryWithResourcesStatement</pre>
   */
  @Test
  public void testTryStatementTryWithResourcesStatement() {
    parseSanityCheck(
        TryStatementNode.Variant.TryWithResourcesStatement,
        "try (Closeable c = allocate();) { useInScope(s); }"
        );
    parseSanityCheck(
        TryStatementNode.Variant.TryWithResourcesStatement,
        "try (Closeable c = allocate()) { useInScope(s); }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>CatchClause { CatchClause }</pre>
   */
  @Test
  public void testCatchesCatchClauseCatchClause() {
    parseSanityCheck(
        CatchesNode.Variant.CatchClauseCatchClause,
        "catch (FileNotFound f) { h(f); } catch (IOException e) { h(e); }"
        );
  }

  /**
   * <pre>"catch" "(" CatchFormalParameter ")" Block</pre>
   */
  @Test
  public void testCatchClauseCatchLpCatchFormalParameterRpBlock() {
    parseSanityCheck(
        CatchClauseNode.Variant.CatchLpCatchFormalParameterRpBlock,
        "catch (IOException e) { log(e); throw e; }"
        );
  }

  /**
   * <pre>{ VariableModifier } CatchType VariableDeclaratorId</pre>
   */
  @Test
  public void testCatchFormalParameterVariableModifierCatchTypeVariableDeclaratorId() {
    parseSanityCheck(
        CatchFormalParameterNode.Variant.CatchFormalParameter,
        "final IOException ex"
        );
  }

  /**
   * <pre>UnannClassType { "|" ClassType }</pre>
   */
  @Test
  public void testCatchTypeUnannClassTypePipClassType() {
    parseSanityCheck(
        CatchTypeNode.Variant.UnannClassTypePipClassType,
        "FileNotFoundException | NotSerializableException"
        );
  }

  /**
   * <pre>"finally" Block</pre>
   */
  @Test
  public void testFinallyFinallyBlock() {
    parseSanityCheck(
        FinallyNode.Variant.FinallyBlock,
        "finally { if (in != null) in.close(); }"
        );
  }

  /**
   * <pre>"try" ResourceSpecification Block [ Catches ] [ Finally ]</pre>
   */
  @Test
  public void testTryWithResourcesStatementTryResourceSpecificationBlockCatchesFinally() {
    parseSanityCheck(
        TryWithResourcesStatementNode.Variant.TryResourceSpecificationBlockCatchesFinally,
        "try (Closeable c = open();) { u(c); }"
        + " catch (IOException e) { h(c); }"
        + " finally { logend(); }"
        );
  }

  /**
   * <pre>"(" ResourceList [ ";" ] ")"</pre>
   */
  @Test
  public void testResourceSpecificationLpResourceListSemRp() {
    parseSanityCheck(
        ResourceSpecificationNode.Variant.LpResourceListSemRp,
        "(InputStream in = openInp(); OutputStream out = openOut();)"
        );
  }

  /**
   * <pre>Resource { ";" Resource }</pre>
   */
  @Test
  public void testResourceListResourceSemResource() {
    parseSanityCheck(
        ResourceListNode.Variant.ResourceSemResource,
        "InputStream in = openInp(); OutputStream out = openOut()"
        );
  }

  /**
   * <pre>{ VariableModifier } UnannType VariableDeclaratorId "=" Expression</pre>
   */
  @Test
  public void testResourceVariableModifierUnannTypeVariableDeclaratorIdEqExpression() {
    parseSanityCheck(
        ResourceNode.Variant.Resource,
        "final InputStream in = openInp()"
        );
  }

  /**
   * <pre>ArrayCreationExpression</pre>
   */
  @Test
  public void testPrimaryArrayCreationExpression() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.ArrayCreationExpression,
        "new int[] { 1, 2, 3, }"
        );
  }

  /**
   * <pre>Literal</pre>
   */
  @Test
  public void testExpressionAtomLiteral() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.Literal,
        "\"foo\""
        );
  }

  /**
   * <pre>ClassLiteral</pre>
   */
  @Test
  public void testExpressionAtomClassLiteral() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.ClassLiteral,
        "String.class"
        );
  }

  /**
   * <pre>"this"</pre>
   */
  @Test
  public void testExpressionAtomThis() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.This,
        "this"
        );
  }

  /**
   * <pre>TypeName "." "this"</pre>
   */
  @Test
  public void testExpressionAtomTypeNameDotThis() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.This,
        "Outer.this"
        );
  }

  /**
   * <pre>"(" Expression ")"</pre>
   */
  @Test
  public void testExpressionAtomLpExpressionRp() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.Parenthesized,
        "(c ? t : e)"
        );
  }

  /**
   * <pre>ClassInstanceCreationExpression</pre>
   */
  @Test
  public void testPrimaryClassInstanceCreationExpression() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.UnqualifiedClassInstanceCreationExpression,
        "new Foo()"
        );
  }

  /**
   * <pre>FieldAccess</pre>
   */
  @Test
  public void testPrimaryFieldAccess() {
    parseSanityCheck(
        PrimaryNode.Variant.FieldAccess,
        "((Foo) foo).bar"
        );
  }

  /**
   * <pre>ArrayAccess</pre>
   */
  @Test
  public void testPrimaryArrayAccess() {
    parseSanityCheck(
        PrimaryNode.Variant.ArrayAccess,
        "arr[i]"
        );
  }

  /**
   * <pre>MethodInvocation</pre>
   */
  @Test
  public void testPrimaryMethodInvocation() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodInvocation,
        "x.toString()"
        );
  }

  /**
   * <pre>MethodReference</pre>
   */
  @Test
  public void testPrimaryMethodReference() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "x::toString"
        );
  }

  /**
   * <pre>TypeName { Dim } "." "class"</pre>
   */
  @Test
  public void testClassLiteralTypeNameDimDotClass() {
    parseSanityCheck(
        ClassLiteralNode.Variant.TypeNameDimDotClass,
        "Boolean[].class"
        );
  }

  /**
   * <pre>NumericType { Dim } "." "class"</pre>
   */
  @Test
  public void testClassLiteralNumericTypeDimDotClass() {
    parseSanityCheck(
        ClassLiteralNode.Variant.NumericTypeDimDotClass,
        "int[].class"
        );
  }

  /**
   * <pre>"boolean" { Dim } "." "class"</pre>
   */
  @Test
  public void testClassLiteralBooleanDimDotClass() {
    parseSanityCheck(
        ClassLiteralNode.Variant.BooleanDimDotClass,
        "boolean.class"
        );
  }

  /**
   * <pre>"void" "." "class"</pre>
   */
  @Test
  public void testClassLiteralVoidDotClass() {
    parseSanityCheck(
        ClassLiteralNode.Variant.VoidDotClass,
        "void . class"
        );
  }

  /**
   * <pre>UnqualifiedClassInstanceCreationExpression</pre>
   */
  @Test
  public void testClassInstanceCreationExpressionUnqualifiedClassInstanceCreationExpression() {
    parseSanityCheck(
        ClassInstanceCreationExpressionNode.Variant.UnqualifiedClassInstanceCreationExpression,
        "new StringBuilder(len)"
        );
  }

  /**
   * <pre>ExpressionName "." UnqualifiedClassInstanceCreationExpression</pre>
   */
  @Test
  public void testClassInstanceCreationExpressionExpressionNameDotUnqualifiedClassInstanceCreationExpression() {
    parseSanityCheck(
        ClassInstanceCreationExpressionNode.Variant.QualifiedClassInstanceCreationExpression,
        "Outer.new Inner()"
        );
  }

  /**
   * <pre>Primary "." UnqualifiedClassInstanceCreationExpression</pre>
   */
  @Test
  public void testClassInstanceCreationExpressionPrimaryDotUnqualifiedClassInstanceCreationExpression() {
    parseSanityCheck(
        ClassInstanceCreationExpressionNode.Variant.QualifiedClassInstanceCreationExpression,
        "(getOuter()).new Inner(x)"
        );
  }

  /**
   * <pre>"new" [ TypeArguments ] ClassOrInterfaceTypeToInstantiate "(" [ ArgumentList ] ")" [ ClassBody ]</pre>
   */
  @Test
  public void testUnqualifiedClassInstanceCreationExpressionNewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody() {
    parseSanityCheck(
        UnqualifiedClassInstanceCreationExpressionNode.Variant.NewTypeArgumentsClassOrInterfaceTypeToInstantiateLpArgumentListRpClassBody,
        "new <X>MyClass(1, 2)"
        );
  }

  /**
   * <pre>{ Annotation } Identifier { "." { Annotation } Identifier } [ TypeArgumentsOrDiamond ]</pre>
   */
  @Test
  public void testClassOrInterfaceTypeToInstantiateAnnotationIdentifierDotAnnotationIdentifierTypeArgumentsOrDiamond() {
    parseSanityCheck(
        ClassOrInterfaceTypeToInstantiateNode.Variant.ClassOrInterfaceTypeDiamond,
        "@A Outer. @B Inner. @C Inner<>"
        );
  }

  /**
   * <pre>TypeArguments</pre>
   */
  @Test
  public void testTypeArgumentsOrDiamondTypeArguments() {
    parseSanityCheck(
        TypeArgumentsOrDiamondNode.Variant.TypeArguments,
        "<String, Number>"
        );
  }

  /**
   * <pre>"&lt;&gt;"</pre>
   */
  @Test
  public void testTypeArgumentsOrDiamondLtGt() {
    parseSanityCheck(
        TypeArgumentsOrDiamondNode.Variant.Diamond,
        "<>"
        );
    parseSanityCheck(
        TypeArgumentsOrDiamondNode.Variant.Diamond,
        "< >"
        );
  }

  /**
   * <pre>Primary "." Identifier</pre>
   */
  @Test
  public void testPrimaryDotIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.FieldAccess,
        "(x).foo"
        );
  }

  /**
   * <pre>"super" "." Identifier</pre>
   */
  @Test
  public void testPrimarySuperDotIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.FieldAccess,
        "super.foo"
        );
  }

  /**
   * <pre>TypeName "." "super" "." Identifier</pre>
   */
  @Test
  public void testPrimaryTypeNameDotSuperDotIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.FieldAccess,
        "Outer.super.field"
        );
  }

  /**
   * <pre>ExpressionName "[" Expression "]"</pre>
   */
  @Test
  public void testArrayAccessExpressionNameLsExpressionRs() {
    parseSanityCheck(
        PrimaryNode.Variant.ArrayAccess,
        "arr[i++]"
        );
  }

  /**
   * <pre>Primary "[" Expression "]"</pre>
   */
  @Test
  public void testArrayAccessPrimaryLsExpressionRs() {
    parseSanityCheck(
        PrimaryNode.Variant.ArrayAccess,
        "getArr()[i]"
        );
  }

  /**
   * <pre>MethodName "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocation() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ImplicitCallee,
        "foo(bar, baz)"
        );
  }

  /**
   * <pre>ExpressionName "." [ TypeArguments ] Identifier "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocationExpressionNameDotTypeArgumentsIdentifierArguments() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ExplicitCallee,
        "ImmutableList.<String>copyOf(item0, item1, item2)"
        );
  }

  /**
   * <pre>TypeName "." [ TypeArguments ] Identifier "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocationTypeNameDotTypeArgumentsIdentifierArguments() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ExplicitCallee,
        "com.google.common.collect.ImmutableList.<String>copyOf(e0, e1, e2)",
        // Cannot lexically distinguish TypeName from ExpressionName
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>TypeName "." "super" "." [ TypeArguments ] Identifier "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocationTypeNameDotSuperDotTypeArgumentsIdentifierArguments() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ExplicitCallee,
        "Outer.super.<T>method()"
        );
  }

  /**
   * <pre>"super" "." [ TypeArguments ] Identifier "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocationSuperDotTypeArgumentsIdentifierArguments() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ExplicitCallee,
        "super.method(foo)"
        );
  }

  /**
   * <pre>Primary "." [ TypeArguments ] Identifier "(" [ ArgumentList ] ")"</pre>
   */
  @Test
  public void testMethodInvocationPrimaryDotTypeArgumentsIdentifierArguments() {
    parseSanityCheck(
        MethodInvocationNode.Variant.ExplicitCallee,
        "(o).<A, B, C<D>>method(arg)"
        );
  }

  /**
   * <pre>Expression { "," Expression }</pre>
   */
  @Test
  public void testArgumentListExpressionComExpression() {
    parseSanityCheck(
        ArgumentListNode.Variant.ExpressionComExpression,
        "1, 2, 3"
        );
  }

  /**
   * <pre>ExpressionName "::" [ TypeArguments ] Identifier</pre>
   */
  @Test
  public void testMethodReferenceExpressionNameClnClnTypeArgumentsIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "foo::<String>bar",
        // TODO: can't lexically distinguish ExpressionName from TypeReference.
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>ReferenceType "::" [ TypeArguments ] Identifier</pre>
   */
  @Test
  public void testMethodReferenceReferenceTypeClnClnTypeArgumentsIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "int[]::clone"
        );
  }

  /**
   * <pre>Primary "::" [ TypeArguments ] Identifier</pre>
   */
  @Test
  public void testMethodReferencePrimaryClnClnTypeArgumentsIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "(outer)::<T>foo"
        );
  }

  /**
   * <pre>"super" "::" [ TypeArguments ] Identifier</pre>
   */
  @Test
  public void testMethodReferenceSuperClnClnTypeArgumentsIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "super::foo"
        );
  }

  /**
   * <pre>TypeName "." "super" "::" [ TypeArguments ] Identifier</pre>
   */
  @Test
  public void testMethodReferenceTypeNameDotSuperClnClnTypeArgumentsIdentifier() {
    parseSanityCheck(
        PrimaryNode.Variant.MethodReference,
        "Outer.super::foo"
        );
  }

  /**
   * <pre>ClassType "::" [ TypeArguments ] "new"</pre>
   */
  @Test
  public void testMethodReferenceClassTypeClnClnTypeArgumentsNew() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.ConstructorReference,
        "Foo::new"
        );
  }

  /**
   * <pre>ArrayType "::" "new"</pre>
   */
  @Test
  public void testMethodReferenceArrayTypeClnClnNew() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.ArrayConstructorReference,
        "Object[]::new"
        );
  }

  /**
   * <pre>"new" PrimitiveType DimExprs [ Dims ]</pre>
   */
  @Test
  public void testArrayCreationExpressionNewPrimitiveTypeDimExprsDims() {
    parseSanityCheck(
        ArrayCreationExpressionNode.Variant.NewPrimitiveTypeDimExprsDimsNotLs,
        "new int[3][4]"
        );
  }

  /**
   * <pre>"new" ClassOrInterfaceType DimExprs [ Dims ]</pre>
   */
  @Test
  public void testArrayCreationExpressionNewClassOrInterfaceTypeDimExprsDims() {
    parseSanityCheck(
        ArrayCreationExpressionNode.Variant.NewClassOrInterfaceTypeDimExprsDimsNotLs,
        "new Foo[3][4]"
        );
  }

  /**
   * <pre>"new" PrimitiveType Dims ArrayInitializer</pre>
   */
  @Test
  public void testArrayCreationExpressionNewPrimitiveTypeDimsArrayInitializer() {
    parseSanityCheck(
        ArrayCreationExpressionNode.Variant.NewPrimitiveTypeDimsArrayInitializerNotLs,
        "new int[][] { { 3, 4, 5, }, }"
        );
  }

  /**
   * <pre>"new" ClassOrInterfaceType Dims ArrayInitializer</pre>
   */
  @Test
  public void testArrayCreationExpressionNewClassOrInterfaceTypeDimsArrayInitializer() {
    parseSanityCheck(
        ArrayCreationExpressionNode.Variant.NewClassOrInterfaceTypeDimsArrayInitializerNotLs,
        "new String[][] { { str, }, }"
        );
    parseSanityCheck(
        ArrayCreationExpressionNode.Variant.NewClassOrInterfaceTypeDimsArrayInitializerNotLs,
        "new String[][] { { str } }",
        Fuzz.IMPLIED_TOKENS
        );
  }

  /**
   * <pre>DimExpr { DimExpr }</pre>
   */
  @Test
  public void testDimExprsDimExprDimExpr() {
    parseSanityCheck(
        DimExprsNode.Variant.DimExprDimExpr,
        "[3][4]"
        );
  }

  /**
   * <pre>{ Annotation } "[" Expression "]"</pre>
   */
  @Test
  public void testDimExprAnnotationLsExpressionRs() {
    parseSanityCheck(
        DimExprNode.Variant.AnnotationLsExpressionRs,
        "@Nonnull [3]"
        );
  }

  /**
   * <pre>AssignmentExpression</pre>
   */
  @Test
  public void testExpressionAssignmentExpression() {
    parseSanityCheck(
        ExpressionNode.Variant.Assignment,
        "a -= b"
        );
  }

  /**
   * <pre>Identifier { "," Identifier }</pre>
   */
  @Test
  public void testInferredFormalParameterListIdentifierComIdentifier() {
    parseSanityCheck(
        InferredFormalParameterListNode.Variant.IdentifierComIdentifier,
        "a, b, c"
        );
  }

  /**
   * <pre>ConditionalExpression</pre>
   */
  @Test
  public void testExpressionConditionalExpression() {
    parseSanityCheck(
        ExpressionNode.Variant.ConditionalExpression,
        "a ? b : c"
        );
  }

  /**
   * <pre>Assignment</pre>
   */
  @Test
  public void testExpressionAssignment() {
    parseSanityCheck(
        ExpressionNode.Variant.Assignment,
        "a = b"
        );
  }

  /**
   * <pre>LeftHandSide AssignmentOperator Expression</pre>
   */
  @Test
  public void testAssignmentLeftHandSideAssignmentOperatorExpression() {
    parseSanityCheck(
        AssignmentNode.Variant.LeftHandSideAssignmentOperatorExpression,
        "x /= y"
        );
  }

  /**
   * <pre>ExpressionName</pre>
   */
  @Test
  public void testLeftHandSideExpressionName() {
    parseSanityCheck(
        LeftHandSideNode.Variant.Ambiguous,
        "foo"
        );
  }

  /**
   * <pre>FieldAccess</pre>
   */
  @Test
  public void testLeftHandSideFieldAccess() {
    parseSanityCheck(
        LeftHandSideNode.Variant.FieldAccess,
        "((T) t).f",
        Fuzz.SAME_VARIANT
        );
  }

  /**
   * <pre>ArrayAccess</pre>
   */
  @Test
  public void testLeftHandSideArrayAccess() {
    parseSanityCheck(
        LeftHandSideNode.Variant.ArrayAccess,
        "arr[i]"
        );
  }

  /**
   * <pre>"="</pre>
   */
  @Test
  public void testAssignmentOperatorEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.Eq,
        "="
        );
  }

  /**
   * <pre>"&#42;="</pre>
   */
  @Test
  public void testAssignmentOperatorStrEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.StrEq,
        "*="
        );
  }

  /**
   * <pre>"/="</pre>
   */
  @Test
  public void testAssignmentOperatorFwdEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.FwdEq,
        "/="
        );
  }

  /**
   * <pre>"%="</pre>
   */
  @Test
  public void testAssignmentOperatorPctEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.PctEq,
        "%="
        );
  }

  /**
   * <pre>"+="</pre>
   */
  @Test
  public void testAssignmentOperatorPlsEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.PlsEq,
        "+="
        );
  }

  /**
   * <pre>"-="</pre>
   */
  @Test
  public void testAssignmentOperatorDshEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.DshEq,
        "-="
        );
  }

  /**
   * <pre>"&lt;&lt;="</pre>
   */
  @Test
  public void testAssignmentOperatorLt2Eq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.Lt2Eq,
        "<<="
        );
  }

  /**
   * <pre>"&gt;&gt;="</pre>
   */
  @Test
  public void testAssignmentOperatorGt2Eq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.Gt2Eq,
        ">>="
        );
  }

  /**
   * <pre>"&gt;&gt;&gt;="</pre>
   */
  @Test
  public void testAssignmentOperatorGt3Eq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.Gt3Eq,
        ">>>="
        );
  }

  /**
   * <pre>"&amp;="</pre>
   */
  @Test
  public void testAssignmentOperatorAmpEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.AmpEq,
        "&="
        );
  }

  /**
   * <pre>"^="</pre>
   */
  @Test
  public void testAssignmentOperatorHatEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.HatEq,
        "^="
        );
  }

  /**
   * <pre>"|="</pre>
   */
  @Test
  public void testAssignmentOperatorPipEq() {
    parseSanityCheck(
        AssignmentOperatorNode.Variant.PipEq,
        "|="
        );
  }

  /**
   * <pre>ConditionalOrExpression "?" Expression ":" ConditionalExpression</pre>
   */
  @Test
  public void testConditionalExpressionConditionalOrExpressionQmExpressionClnConditionalExpression() {
    parseSanityCheck(
        ConditionalExpressionNode.Variant.ConditionalOrExpressionQmExpressionClnConditionalExpression,
        "cond ? a() : --b"
        );
  }

  /**
   * <pre>ConditionalOrExpression</pre>
   */
  @Test
  public void testConditionalExpressionConditionalOrExpression() {
    parseSanityCheck(
        ConditionalExpressionNode.Variant.ConditionalOrExpression,
        "a || b"
        );
  }

  /**
   * <pre>ConditionalOrExpression "||" ConditionalAndExpression</pre>
   */
  @Test
  public void testConditionalOrExpressionConditionalOrExpressionPip2ConditionalAndExpression() {
    parseSanityCheck(
        ConditionalOrExpressionNode.Variant.ConditionalOrExpressionPip2ConditionalAndExpression,
        "a || b && c"
        );
  }

  /**
   * <pre>ConditionalAndExpression</pre>
   */
  @Test
  public void testConditionalOrExpressionConditionalAndExpression() {
    parseSanityCheck(
        ConditionalOrExpressionNode.Variant.ConditionalAndExpression,
        "a && b && c"
        );
  }

  /**
   * <pre>ConditionalAndExpression "&amp;&amp;" InclusiveOrExpression</pre>
   */
  @Test
  public void testConditionalAndExpressionConditionalAndExpressionAmp2InclusiveOrExpression() {
    parseSanityCheck(
        ConditionalAndExpressionNode.Variant.ConditionalAndExpressionAmp2InclusiveOrExpression,
        "a && b | c"
        );
  }

  /**
   * <pre>InclusiveOrExpression</pre>
   */
  @Test
  public void testConditionalAndExpressionInclusiveOrExpression() {
    parseSanityCheck(
        ConditionalAndExpressionNode.Variant.InclusiveOrExpression,
        "a | b | c | d"
        );
  }

  /**
   * <pre>InclusiveOrExpression "|" ExclusiveOrExpression</pre>
   */
  @Test
  public void testInclusiveOrExpressionInclusiveOrExpressionPipExclusiveOrExpression() {
    parseSanityCheck(
        InclusiveOrExpressionNode.Variant.InclusiveOrExpressionPipExclusiveOrExpression,
        "a | b ^ c | d"
        );
  }

  /**
   * <pre>ExclusiveOrExpression</pre>
   */
  @Test
  public void testInclusiveOrExpressionExclusiveOrExpression() {
    parseSanityCheck(
        InclusiveOrExpressionNode.Variant.ExclusiveOrExpression,
        "a ^ b"
        );
  }

  /**
   * <pre>ExclusiveOrExpression "^" AndExpression</pre>
   */
  @Test
  public void testExclusiveOrExpressionExclusiveOrExpressionHatAndExpression() {
    parseSanityCheck(
        ExclusiveOrExpressionNode.Variant.ExclusiveOrExpressionHatAndExpression,
        "a ^ b & c"
        );
  }

  /**
   * <pre>AndExpression</pre>
   */
  @Test
  public void testExclusiveOrExpressionAndExpression() {
    parseSanityCheck(
        ExclusiveOrExpressionNode.Variant.AndExpression,
        "a & b"
        );
  }

  /**
   * <pre>AndExpression "&amp;" EqualityExpression</pre>
   */
  @Test
  public void testAndExpressionAndExpressionAmpEqualityExpression() {
    parseSanityCheck(
        AndExpressionNode.Variant.AndExpressionAmpEqualityExpression,
        "a == b & c != d"
        );
  }

  /**
   * <pre>EqualityExpression</pre>
   */
  @Test
  public void testAndExpressionEqualityExpression() {
    parseSanityCheck(
        AndExpressionNode.Variant.EqualityExpression,
        "a != b"
        );
  }

  /**
   * <pre>EqualityExpression EqualityOperator RelationalExpression</pre>
   */
  @Test
  public void testEqualityExpressionEqualityExpressionEqualityOperatorRelationalExpression() {
    parseSanityCheck(
        EqualityExpressionNode.Variant.EqualityExpressionEqualityOperatorRelationalExpression,
        "a == b"
        );
  }

  /**
   * <pre>RelationalExpression</pre>
   */
  @Test
  public void testEqualityExpressionRelationalExpression() {
    parseSanityCheck(
        EqualityExpressionNode.Variant.RelationalExpression,
        "a <= b"
        );
  }

  /**
   * <pre>"=="</pre>
   */
  @Test
  public void testEqualityOperatorEqEq() {
    parseSanityCheck(
        EqualityOperatorNode.Variant.EqEq,
        "=="
        );
  }

  /**
   * <pre>"!="</pre>
   */
  @Test
  public void testEqualityOperatorBngEq() {
    parseSanityCheck(
        EqualityOperatorNode.Variant.BngEq,
        "!="
        );
  }

  /**
   * <pre>RelationalExpression RelationalOperator ShiftExpression</pre>
   */
  @Test
  public void testRelationalExpressionRelationalExpressionRelationalOperatorShiftExpression() {
    parseSanityCheck(
        RelationalExpressionNode.Variant.RelationalExpressionRelationalOperatorShiftExpression,
        "a > b"
        );
  }

  /**
   * <pre>RelationalExpression "instanceof" ReferenceType</pre>
   */
  @Test
  public void testRelationalExpressionRelationalExpressionInstanceofReferenceType() {
    parseSanityCheck(
        RelationalExpressionNode.Variant.RelationalExpressionInstanceofReferenceType,
        "o instanceof Type"
        );
  }

  /**
   * <pre>ShiftExpression</pre>
   */
  @Test
  public void testRelationalExpressionShiftExpression() {
    parseSanityCheck(
        RelationalExpressionNode.Variant.ShiftExpression,
        "a << 1"
        );
  }

  /**
   * <pre>"&lt;="</pre>
   */
  @Test
  public void testRelationalOperatorLtEq() {
    parseSanityCheck(
        RelationalOperatorNode.Variant.LtEq,
        "<="
        );
  }

  /**
   * <pre>"&gt;="</pre>
   */
  @Test
  public void testRelationalOperatorGtEq() {
    parseSanityCheck(
        RelationalOperatorNode.Variant.GtEq,
        ">="
        );
  }

  /**
   * <pre>"&lt;"</pre>
   */
  @Test
  public void testRelationalOperatorLt() {
    parseSanityCheck(
        RelationalOperatorNode.Variant.Lt,
        "<"
        );
  }

  /**
   * <pre>"&gt;"</pre>
   */
  @Test
  public void testRelationalOperatorGt() {
    parseSanityCheck(
        RelationalOperatorNode.Variant.Gt,
        ">"
        );
  }

  /**
   * <pre>ShiftExpression ShiftOperator AdditiveExpression</pre>
   */
  @Test
  public void testShiftExpressionShiftExpressionShiftOperatorAdditiveExpression() {
    parseSanityCheck(
        ShiftExpressionNode.Variant.ShiftExpressionShiftOperatorAdditiveExpression,
        "a << b >>> c"
        );
  }

  /**
   * <pre>AdditiveExpression</pre>
   */
  @Test
  public void testShiftExpressionAdditiveExpression() {
    parseSanityCheck(
        ShiftExpressionNode.Variant.AdditiveExpression,
        "a + b"
        );
  }

  /**
   * <pre>"&gt;&gt;&gt;"</pre>
   */
  @Test
  public void testShiftOperatorGt3() {
    parseSanityCheck(
        ShiftOperatorNode.Variant.Gt3,
        ">>>"
        );
  }

  /**
   * <pre>"&lt;&lt;"</pre>
   */
  @Test
  public void testShiftOperatorLt2() {
    parseSanityCheck(
        ShiftOperatorNode.Variant.Lt2,
        "<<"
        );
  }

  /**
   * <pre>"&gt;&gt;"</pre>
   */
  @Test
  public void testShiftOperatorGt2() {
    parseSanityCheck(
        ShiftOperatorNode.Variant.Gt2,
        ">>"
        );
  }

  /**
   * <pre>AdditiveExpression AdditiveOperator MultiplicativeExpression</pre>
   */
  @Test
  public void testAdditiveExpressionAdditiveExpressionAdditiveOperatorMultiplicativeExpression() {
    parseSanityCheck(
        AdditiveExpressionNode.Variant.AdditiveExpressionAdditiveOperatorMultiplicativeExpression,
        "a + b * c"
        );
  }

  /**
   * <pre>MultiplicativeExpression</pre>
   */
  @Test
  public void testAdditiveExpressionMultiplicativeExpression() {
    parseSanityCheck(
        AdditiveExpressionNode.Variant.MultiplicativeExpression,
        "a * b / c"
        );
  }

  /**
   * <pre>"+"</pre>
   */
  @Test
  public void testAdditiveOperatorPls() {
    parseSanityCheck(
        AdditiveOperatorNode.Variant.Pls,
        "+"
        );
  }

  /**
   * <pre>"-"</pre>
   */
  @Test
  public void testAdditiveOperatorDsh() {
    parseSanityCheck(
        AdditiveOperatorNode.Variant.Dsh,
        "-"
        );
  }

  /**
   * <pre>MultiplicativeExpression MultiplicativeOperator UnaryExpression</pre>
   */
  @Test
  public void testMultiplicativeExpressionMultiplicativeExpressionMultiplicativeOperatorUnaryExpression() {
    parseSanityCheck(
        MultiplicativeExpressionNode.Variant.MultiplicativeExpressionMultiplicativeOperatorUnaryExpression,
        "a / -b"
        );
  }

  /**
   * <pre>UnaryExpression</pre>
   */
  @Test
  public void testMultiplicativeExpressionUnaryExpression() {
    parseSanityCheck(
        MultiplicativeExpressionNode.Variant.UnaryExpression,
        "~b"
        );
  }

  /**
   * <pre>"&#42;"</pre>
   */
  @Test
  public void testMultiplicativeOperatorStr() {
    parseSanityCheck(
        MultiplicativeOperatorNode.Variant.Str,
        "*"
        );
  }

  /**
   * <pre>"/"</pre>
   */
  @Test
  public void testMultiplicativeOperatorFwd() {
    parseSanityCheck(
        MultiplicativeOperatorNode.Variant.Fwd,
        "/"
        );
  }

  /**
   * <pre>"%"</pre>
   */
  @Test
  public void testMultiplicativeOperatorPct() {
    parseSanityCheck(
        MultiplicativeOperatorNode.Variant.Pct,
        "%"
        );
  }

  /**
   * <pre>UnaryExpressionNotPlusMinus</pre>
   */
  @Test
  public void testUnaryExpressionUnaryExpressionNotPlusMinus() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression,
        "!cond"
        );
  }

  /**
   * <pre>PreExpression</pre>
   */
  @Test
  public void testUnaryExpressionPreExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PreExpression,
        "--foo"
        );
  }

  /**
   * <pre>"+" UnaryExpression</pre>
   */
  @Test
  public void testUnaryExpressionPlsUnaryExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression,
        "+arr[i]"
        );
  }

  /**
   * <pre>"-" UnaryExpression</pre>
   */
  @Test
  public void testUnaryExpressionDshUnaryExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression,
        "-(o.f)"
        );
  }

  /**
   * <pre>IncrDecrOperator UnaryExpression</pre>
   */
  @Test
  public void testPreExpressionIncrDecrOperatorUnaryExpression() {
    parseSanityCheck(
        PreExpressionNode.Variant.IncrDecrOperatorLeftHandSideExpression,
        "--x"
        );
  }

  /**
   * <pre>"++"</pre>
   */
  @Test
  public void testIncrDecrOperatorPlsPls() {
    parseSanityCheck(
        IncrDecrOperatorNode.Variant.PlsPls,
        "++"
        );
  }

  /**
   * <pre>"--"</pre>
   */
  @Test
  public void testIncrDecrOperatorDshDsh() {
    parseSanityCheck(
        IncrDecrOperatorNode.Variant.DshDsh,
        "--"
        );
  }

  /**
   * <pre>PostfixExpression</pre>
   */
  @Test
  public void testUnaryExpressionNotPlusMinusPostfixExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PostExpression,
        "x++"
        );
  }

  /**
   * <pre>"~" UnaryExpression</pre>
   */
  @Test
  public void testUnaryExpressionNotPlusMinusTldUnaryExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression,
        "~~u"
        );
  }

  /**
   * <pre>"!" UnaryExpression</pre>
   */
  @Test
  public void testUnaryExpressionNotPlusMinusBngUnaryExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.PrefixOperatorUnaryExpression,
        "!(a instanceof T)"
        );
  }

  /**
   * <pre>CastExpression</pre>
   */
  @Test
  public void testUnaryExpressionNotPlusMinusCastExpression() {
    parseSanityCheck(
        UnaryExpressionNode.Variant.CastExpression,
        "(String) foo"
        );
  }

  /**
   * <pre>PostExpression</pre>
   */
  @Test
  public void testPostfixExpressionPostExpression() {
    parseSanityCheck(
        PostExpressionNode.Variant.LeftHandSideExpressionIncrDecrOperator,
        "x--"
        );
  }

  /**
   * <pre>"(" Expression ")"</pre>
   */
  @Test
  public void testPrimaryExpressionParenthesized() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.Parenthesized,
        "(a ? b : c)"
        );
  }

  /**
   * <pre>ExpressionName</pre>
   */
  @Test
  public void testExpressionAtomExpressionName() {
    parseSanityCheck(
        ExpressionAtomNode.Variant.ContextFreeNames,
        "foo"
        );
  }

  /**
   * <pre>Primary IncrDecrOperator</pre>
   */
  @Test
  public void testPostExpressionPrimaryIncrDecrOperator() {
    parseSanityCheck(
        PostExpressionNode.Variant.LeftHandSideExpressionIncrDecrOperator,
        "(arr[i])++"
        );
  }

  /**
   * <pre>ExpressionName IncrDecrOperator</pre>
   */
  @Test
  public void testPostExpressionExpressionNameIncrDecrOperator() {
    parseSanityCheck(
        PostExpressionNode.Variant.LeftHandSideExpressionIncrDecrOperator,
        "foo--"
        );
  }

  /**
   * <pre>"(" PrimitiveType ")" UnaryExpression</pre>
   */
  @Test
  public void testCastExpressionLpPrimitiveTypeRpUnaryExpression() {
    parseSanityCheck(
        CastExpressionNode.Variant.Expression,
        "(int) str.charAt(i)"
        );
  }

  /**
   * <pre>"(" ReferenceType { AdditionalBound } ")" UnaryExpressionNotPlusMinus</pre>
   */
  @Test
  public void testCastExpressionLpReferenceTypeAdditionalBoundRpUnaryExpressionNotPlusMinus() {
    parseSanityCheck(
        CastExpressionNode.Variant.Expression,
        "(Object[]) myArr.clone()"
        );
  }

  /**
   * <pre>Expression</pre>
   */
  @Test
  public void testConstantExpressionExpression() {
    parseSanityCheck(
        ConstantExpressionNode.Variant.Expression,
        "\"foo\" + \"bar\"",
        // The grammar does not lexically distinguish ConstantExpression from
        // Expression.
        Fuzz.SAME_VARIANT
        );
  }

}
