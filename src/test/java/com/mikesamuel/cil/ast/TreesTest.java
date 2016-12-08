package com.mikesamuel.cil.ast;

import javax.annotation.Nonnull;

import org.junit.Test;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SourcePosition;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TreesTest extends TestCase {

  @Test
  public static void testTreesOfIdentifier() {
    Input input = Input.builder().source("test-file").code("  foo").build();

    BaseNode got = Trees.of(input, ImmutableList.of(
        Event.push(IdentifierNode.Variant.Builtin),
        Event.content("foo", 2),
        Event.pop()));

    IdentifierNode want =
        IdentifierNode.Variant.Builtin.nodeBuilder()
        .leaf("foo")
        .build();

    assertEquals(want, got);

    assertEquals("test-file", got.getSourcePosition().getSource());
    assertEquals(1, got.getSourcePosition().startLineInFile());
    assertEquals(3, got.getSourcePosition().startCharInLine());
    assertEquals(1, got.getSourcePosition().endLineInFile());
    assertEquals(6, got.getSourcePosition().endCharInLine());
  }

  @Test
  public static void testTreesOfWithInnerNodes() {
    Input input = Input.builder().code("  int[][].class").build();
    BaseNode got = Trees.of(input, ImmutableList.of(
        Event.push(ClassLiteralNode.Variant.NumericTypeDimDotClass),

        Event.push(NumericTypeNode.Variant.IntegralType),
        Event.push(IntegralTypeNode.Variant.Int),
        Event.token("int", 2),
        Event.pop(),
        Event.pop(),

        Event.push(DimNode.Variant.LsRs),
        Event.token("[", 5),
        Event.token("]", 6),
        Event.pop(),
        Event.push(DimNode.Variant.LsRs),
        Event.token("[", 7),
        Event.token("]", 8),
        Event.pop(),

        Event.token(".", 9),
        Event.token("class", 10),
        Event.pop()
        ));

    BaseNode want =
        ClassLiteralNode.Variant.NumericTypeDimDotClass.nodeBuilder()
        .add(
            NumericTypeNode.Variant.IntegralType.nodeBuilder()
            .add(
                IntegralTypeNode.Variant.Int.nodeBuilder()
                .build()
                )
            .build()
            )
        .add(DimNode.Variant.LsRs.nodeBuilder().build())
        .add(DimNode.Variant.LsRs.nodeBuilder().build())
        .build();

    assertEquals(got.toString(), want.toString());
    assertEquals(got, want);
  }

  @Test
  public static final void testSourcePositions() {
    // The unicode escape introduces an index difference between
    // the character indices in the code and the character indices in the
    // decoded string.

    String code = "/** In \\u2124 */\npublic final double x;";
    // CharInFile             1111111 111222222222233333333334
    //             01234567 890123456 789012345678901234567890
    //
    // Line       1111111111111111111 22222222222222222222222
    //
    // CharInLine            11111111          11111111112222
    //            012345678 901234567 12345678901234567890123

    String golden = Joiner.on('\n').join(
        "FieldDeclaration.Declaration : test:1+1 - 2+23",
        "  JavaDocComment.Builtin /** In \u2124 */ : test:1+1-17",
        "  Modifier.Public : test:2+1-7",
        "  Modifier.Final : test:2+8-13",
        "  UnannType.NotAtType : test:2+14-20",
        "    Type.PrimitiveType : test:2+14-20",
        "      PrimitiveType.AnnotationNumericType : test:2+14-20",
        "        NumericType.FloatingPointType : test:2+14-20",
        "          FloatingPointType.Double : test:2+14-20",
        "  VariableDeclaratorList.VariableDeclaratorComVariableDeclarator : test:2+21-22",
        "    VariableDeclarator.VariableDeclaratorIdEqVariableInitializer : test:2+21-22",
        "      VariableDeclaratorId.IdentifierDims : test:2+21-22",
        "        Identifier.Builtin x : test:2+21-22"
        );


    Input input = Input.builder().source("test").code(code).build();
    ParseState start = new ParseState(input);

    ParseResult result = NodeType.FieldDeclaration.getParSer().parse(
        start, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    BaseNode root = null;
    switch (result.synopsis) {
      case FAILURE:
        fail("Parse failed");
        break;
      case SUCCESS:
        root = Trees.of(start.input, result.next().output);
        break;
    }
    Preconditions.checkNotNull(root);
    assertEquals(
        golden,
        root.toAsciiArt(
            "",
            new Function<BaseNode, String>() {
              @Override
              public String apply(@Nonnull BaseNode node) {
                SourcePosition pos = node.getSourcePosition();
                if (pos != null) {
                  return pos.toString();
                }
                return null;
              }
            }));
  }
}
