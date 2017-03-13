package com.mikesamuel.cil.ptree;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.ast.j8.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.j8.ContextFreeNameNode;
import com.mikesamuel.cil.ast.j8.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.j8.IdentifierNode;
import com.mikesamuel.cil.ast.j8.J8NodeType;
import com.mikesamuel.cil.ast.j8.MethodInvocationNode;
import com.mikesamuel.cil.ast.j8.MethodNameNode;
import com.mikesamuel.cil.ast.j8.PackageDeclarationNode;
import com.mikesamuel.cil.ast.j8.PackageNameNode;
import com.mikesamuel.cil.ast.j8.PackageOrTypeNameNode;
import com.mikesamuel.cil.ast.j8.PrimaryNode;
import com.mikesamuel.cil.ast.j8.ReferenceTypeNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentListNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentNode;
import com.mikesamuel.cil.ast.j8.TypeArgumentsNode;
import com.mikesamuel.cil.ast.j8.TypeImportOnDemandDeclarationNode;

@SuppressWarnings("javadoc")
public class MagicDotIdentifierHandlerTest extends AbstractParSerTestCase {

  @Test
  public void testSingleStaticImport() {
    assertParsePasses(
        J8NodeType.TypeImportOnDemandDeclaration,
        "import com.example.Foo.bar.*;",
        /**/push(TypeImportOnDemandDeclarationNode.Variant
                .ImportPackageOrTypeNameDotStrSem),
        /*..*/token("import", -1),
        /*..*/push(PackageOrTypeNameNode.Variant.NotAtContextFreeNames),
        /*....*/push(ContextFreeNamesNode.Variant
                    .ContextFreeNameDotContextFreeName),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),  // child of package
        /*..........*/content("com", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),  // child of package
        /*..........*/content("example", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),  // Child of type name
        /*..........*/content("Foo", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),  // Child of import
        /*..........*/content("bar", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/token("*", -1),
        /*..*/token(";", -1),
        /**/pop());
  }

  @Test
  public void testPackageDeclaration() {
    assertParsePasses(
        J8NodeType.PackageDeclaration,
        "package com.foo.bar;",
        /**/push(PackageDeclarationNode.Variant.Declaration),
        /*..*/token("package", -1),
        // Actually n-ary for no apparent reason so should not trigger.
        /*..*/push(PackageNameNode.Variant.IdentifierDotIdentifier),
        /*....*/push(IdentifierNode.Variant.Builtin),
        /*......*/content("com", -1),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(IdentifierNode.Variant.Builtin),
        /*......*/content("foo", -1),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(IdentifierNode.Variant.Builtin),
        /*......*/content("bar", -1),
        /*....*/pop(),
        /*..*/pop(),
        /*..*/token(";", -1),
        /**/pop());
  }

  @Test
  public void testMethodInvocation() {
    assertParsePasses(
        J8NodeType.MethodInvocation,
        "Foo.f1.f2.method_name()",
        /**/push(MethodInvocationNode.Variant.ExplicitCallee),
        /*..*/push(PrimaryNode.Variant.MethodInvocation),
        /*....*/push(PrimaryNode.Variant.Ambiguous),
        /*......*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("Foo", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("f1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("f2", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(MethodNameNode.Variant.Identifier),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("method_name", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token("(", -1),
        /*....*/token(")", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testMethodInvocationWithTypeParameter() {
    assertParsePasses(
        J8NodeType.MethodInvocation,
        "Foo.f1.f2.<T>method_name()",
        /**/push(MethodInvocationNode.Variant.ExplicitCallee),
        /*..*/push(PrimaryNode.Variant.MethodInvocation),
        /*....*/push(PrimaryNode.Variant.Ambiguous),
        /*......*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("Foo", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("f1", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("f2", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/push(TypeArgumentsNode.Variant.LtTypeArgumentListGt),
        /*......*/token("<", -1),
        /*......*/push(TypeArgumentListNode.Variant.TypeArgumentComTypeArgument),
        /*........*/push(TypeArgumentNode.Variant.ReferenceType),
        //            Lexically ambiguous with TypeParameter
        /*..........*/push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        /*............*/push(ClassOrInterfaceTypeNode.Variant.ContextFreeNames),
        /*..............*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*................*/push(ContextFreeNameNode.Variant.Name),
        /*..................*/push(IdentifierNode.Variant.Builtin),
        /*....................*/content("T", -1),
        /*..................*/pop(),
        /*................*/pop(),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(">", -1),
        /*....*/pop(),
        /*....*/push(MethodNameNode.Variant.Identifier),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("method_name", -1),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token("(", -1),
        /*....*/token(")", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testTypeArgument() {
    // TODO: Are we missing anything here?
    assertParsePasses(
        J8NodeType.ReferenceType,
        "java.lang.Object",
        /**/push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        /*..*/push(ClassOrInterfaceTypeNode.Variant.ContextFreeNames),
        /*....*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("java", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("lang", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("Object", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }
}
