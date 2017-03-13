package com.mikesamuel.cil.ast.j8;

import static com.mikesamuel.cil.event.Event.content;
import static com.mikesamuel.cil.event.Event.pop;
import static com.mikesamuel.cil.event.Event.push;
import static com.mikesamuel.cil.event.Event.token;

import org.junit.Test;

import com.mikesamuel.cil.ast.AbstractParSerTestCase;
import com.mikesamuel.cil.ptree.PTree;

@SuppressWarnings("javadoc")
public final class PrimaryNodeTest extends AbstractParSerTestCase {

  @Test
  public final void testClassLiteral() {
    assertParsePasses(
        J8NodeType.Primary,
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
  public final void testPreLexPassForUnicodeEscapes() {
    assertParsePasses(
        J8NodeType.Primary,
        "\"\\u0022",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.StringLiteral),
        push(StringLiteralNode.Variant.Builtin),
        content("\"\"", -1),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "\"\\u0022\""  // \u0022 terminates string early.
        );
    assertParsePasses(
        J8NodeType.Primary,
        "// Line Comment\\u000a1",
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
  public final void testStringLiteral() {
    assertParsePasses(
        J8NodeType.Primary,
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
        PTree.complete(J8NodeType.Primary),
        "\"foo"
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "\"foo\\\""
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "\"\n\""
        );
  }

  @Test
  public final void testBooleanLiteral() {
    assertParsePasses(
        J8NodeType.Primary,
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
        J8NodeType.Primary,
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
        J8NodeType.Primary,
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
        J8NodeType.Primary,
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
        J8NodeType.Primary,
        "'\"'",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\"'", -1),
        pop(),
        pop(),
        pop()
        );
    assertParsePasses(
        J8NodeType.Primary,
        "'\\u0022'",
        push(ExpressionAtomNode.Variant.Literal),
        push(LiteralNode.Variant.CharacterLiteral),
        push(CharacterLiteralNode.Variant.Builtin),
        content("'\"'", -1),
        pop(),
        pop(),
        pop()
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "''"
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "'''"
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "'\\'"
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "'xx'"
        );
    assertParseFails(
        PTree.complete(J8NodeType.Primary),
        "'x"
        );
  }

  @Test
  public final void testNullLiteral() {
    assertParsePasses(
        J8NodeType.Primary,
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
        J8NodeType.Primary,
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
        J8NodeType.UnaryExpression,
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
