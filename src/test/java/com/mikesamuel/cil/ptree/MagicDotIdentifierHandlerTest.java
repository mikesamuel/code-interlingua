package com.mikesamuel.cil.ptree;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.ast.ClassOrInterfaceTypeNode;
import com.mikesamuel.cil.ast.ClassTypeNode;
import com.mikesamuel.cil.ast.ContextFreeNameNode;
import com.mikesamuel.cil.ast.ContextFreeNamesNode;
import com.mikesamuel.cil.ast.IdentifierNode;
import com.mikesamuel.cil.ast.MethodInvocationNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.PackageDeclarationNode;
import com.mikesamuel.cil.ast.PostOpNode;
import com.mikesamuel.cil.ast.PrimaryNode;
import com.mikesamuel.cil.ast.ReferenceTypeNode;
import com.mikesamuel.cil.ast.SingleStaticImportDeclarationNode;
import com.mikesamuel.cil.ast.TypeArgumentListNode;
import com.mikesamuel.cil.ast.TypeArgumentNode;
import com.mikesamuel.cil.ast.TypeArgumentsNode;
import com.mikesamuel.cil.ast.TypeNameNode;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public class MagicDotIdentifierHandlerTest extends AbstractParSerTestCase {

  @Test
  public void testSingleStaticImport() {
    assertParsePasses(
        NodeType.SingleStaticImportDeclaration,
        "import static com.example.Foo.bar;",
        /**/push(SingleStaticImportDeclarationNode.Variant
                .ImportStaticTypeNameDotIdentifierSem),
        /*..*/token("import", -1),
        /*..*/token("static", -1),
        /*..*/push(TypeNameNode.Variant.NotAtContextFreeNames),
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
        /*....*/pop(),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/push(IdentifierNode.Variant.Builtin),  // Child of import
        /*....*/content("bar", -1),
        /*..*/pop(),
        /*..*/token(";", -1),
        /**/pop());
  }

  @Test
  public void testPackageDeclaration() {
    assertParsePasses(
        NodeType.PackageDeclaration,
        "package com.foo.bar;",
        /**/push(PackageDeclarationNode.Variant
                .PackageModifierPackageIdentifierDotIdentifierSem),
        /*..*/token("package", -1),
        // Actually n-ary for no apparent reason so should not trigger.
        /*..*/push(IdentifierNode.Variant.Builtin),
        /*....*/content("com", -1),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/push(IdentifierNode.Variant.Builtin),
        /*....*/content("foo", -1),
        /*..*/pop(),
        /*..*/token(".", -1),
        /*..*/push(IdentifierNode.Variant.Builtin),
        /*....*/content("bar", -1),
        /*..*/pop(),
        /*..*/token(";", -1),
        /**/pop());
  }

  @Test
  public void testMethodInvocation() {
    assertParsePasses(
        NodeType.MethodInvocation,
        "Foo.f1.f2.method_name()",
        /**/push(MethodInvocationNode.Variant.ExplicitCallee),
        /*..*/push(PrimaryNode.Variant.Ambiguous),
        /*....*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("Foo", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("f1", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("f2", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/push(PostOpNode.Variant.MethodInvocation),
        /*......*/token(".", -1),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("method_name", -1),
        /*......*/pop(),
        /*......*/token("(", -1),
        /*......*/token(")", -1),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testMethodInvocationWithTypeParameter() {
    assertParsePasses(
        NodeType.MethodInvocation,
        "Foo.f1.f2.<T>method_name()",
        /**/push(MethodInvocationNode.Variant.ExplicitCallee),
        /*..*/push(PrimaryNode.Variant.Ambiguous),
        /*....*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("Foo", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("f1", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*......*/token(".", -1),
        /*......*/push(ContextFreeNameNode.Variant.Name),
        /*........*/push(IdentifierNode.Variant.Builtin),
        /*..........*/content("f2", -1),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..  */push(PostOpNode.Variant.MethodInvocation),
        /*......*/token(".", -1),
        /*......*/push(TypeArgumentsNode.Variant.LtTypeArgumentListGt),
        /*........*/token("<", -1),
        /*........*/push(TypeArgumentListNode.Variant.TypeArgumentComTypeArgument),
        /*..........*/push(TypeArgumentNode.Variant.ReferenceType),
        //              Lexically ambiguous with TypeParameter
        /*............*/push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        /*..............*/push(ClassOrInterfaceTypeNode.Variant.ClassType),
        /*................*/push(ClassTypeNode.Variant.ContextFreeNames),
        /*..................*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*....................*/push(ContextFreeNameNode.Variant.Name),
        /*......................*/push(IdentifierNode.Variant.Builtin),
        /*........................*/content("T", -1),
        /*......................*/pop(),
        /*....................*/pop(),
        /*..................*/pop(),
        /*................*/pop(),
        /*..............*/pop(),
        /*............*/pop(),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(">", -1),
        /*......*/pop(),
        /*......*/push(IdentifierNode.Variant.Builtin),
        /*........*/content("method_name", -1),
        /*......*/pop(),
        /*......*/token("(", -1),
        /*......*/token(")", -1),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public void testTypeArgument() {
    // TODO: Are we missing anything here?
    assertParsePasses(
        NodeType.ReferenceType,
        "java.lang.Object",
        /**/push(ReferenceTypeNode.Variant.ClassOrInterfaceType),
        /*..*/push(ClassOrInterfaceTypeNode.Variant.ClassType),
        /*....*/push(ClassTypeNode.Variant.ContextFreeNames),
        /*......*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("java", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("lang", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*........*/token(".", -1),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("Object", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*..*/pop(),
        /**/pop()
        );
  }
}
