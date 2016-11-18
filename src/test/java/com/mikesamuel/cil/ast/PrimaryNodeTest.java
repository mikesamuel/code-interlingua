package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.mikesamuel.cil.ptree.PTree;

import static com.mikesamuel.cil.ast.MatchEvent.content;
import static com.mikesamuel.cil.ast.MatchEvent.pop;
import static com.mikesamuel.cil.ast.MatchEvent.push;
import static com.mikesamuel.cil.ast.MatchEvent.token;

@SuppressWarnings("javadoc")
public final class PrimaryNodeTest extends AbstractParSerTestCase {

  @Test
  public final void testClassLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "Object.class",
        /**/push(ExpressionAtomNode.Variant.ClassLiteral),
        /*..*/push(ClassLiteralNode.Variant.TypeNameDimDotClass),
        /*....*/push(TypeNameNode.Variant.NotAtContextFreeNames),
        /*......*/push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        /*........*/push(ContextFreeNameNode.Variant.Name),
        /*..........*/push(IdentifierNode.Variant.Builtin),
        /*............*/content("Object", -1),
        /*..........*/pop(),
        /*........*/pop(),
        /*......*/pop(),
        /*....*/pop(),
        /*....*/token(".", -1),
        /*....*/token("class", -1),
        /*..*/pop(),
        /**/pop()
        );
  }

  @Test
  public final void testStringLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "\"foo\\nbar\\0\"",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.StringLiteral),
        push(StringLiteralNode.Variant.Builtin),
        content("\"foo\\nbar\\0\"", -1),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"foo"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"foo\\\""
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "\"\n\""
        );
  }

  @Test
  public final void testBooleanLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "false",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.False),
        token("false", -1),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "true",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.BooleanLiteral),
        push(BooleanLiteralNode.Variant.True),
        token("true", -1),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testCharacterLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'x'",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'x'", -1),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'\\r'",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\\r'", -1),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "'\"'",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\"'", -1),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "''"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'''"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'\\'"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'xx'"
        );
    assertParseFails(
        PTree.complete(NodeType.Primary),
        "'x"
        );
  }

  @Test
  public final void testNullLiteral() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "null",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.NullLiteral),
        push(NullLiteralNode.Variant.Null),
        token("null", -1),
        pop(),
        pop(),
        pop()
        );
  }

  @Test
  public final void testNumericLiteralTest() {
    assertParsePasses(
        PTree.complete(NodeType.Primary),
        "1",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.IntegerLiteral),
        push(IntegerLiteralNode.Variant.Builtin),
        content("1", -1),
        pop(),
        pop(),
        pop()
        );
  }


  @Test
  public void testSimpleFieldAccess() {
    this.assertParsePasses(
        PTree.complete(NodeType.UnaryExpression),
        "o.f",
        push(PrimaryNode.Variant.Ambiguous),
        push(ContextFreeNamesNode.Variant.ContextFreeNameDotContextFreeName),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("o", -1),
        pop(),
        pop(),
        token(".", -1),
        push(ContextFreeNameNode.Variant.Name),
        push(IdentifierNode.Variant.Builtin),
        content("f", -1),
        pop(),
        pop(),
        pop(),
        pop());
  }
}
