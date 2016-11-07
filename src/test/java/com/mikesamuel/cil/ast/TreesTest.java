package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LineStarts;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TreesTest extends TestCase {

  @Test
  public static void testTreesOfIdentifier() throws Exception {
    LineStarts starts = (new Input("test-file", CharSource.wrap("  foo")))
        .lineStarts;

    BaseNode got = Trees.of(starts, ImmutableList.of(
        MatchEvent.push(IdentifierNode.Variant.Builtin),
        MatchEvent.content("foo", 2),
        MatchEvent.pop()));

    IdentifierNode want =
        IdentifierNode.builder(IdentifierNode.Variant.Builtin)
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
  public static void testTreesOfWithInnerNodes() throws Exception {
    LineStarts starts = new Input(
        "test-file", CharSource.wrap("  int[][].class")).lineStarts;
    BaseNode got = Trees.of(starts, ImmutableList.of(
        MatchEvent.push(ClassLiteralNode.Variant.NumericTypeDimDotClass),

        MatchEvent.push(NumericTypeNode.Variant.IntegralType),
        MatchEvent.push(IntegralTypeNode.Variant.Int),
        MatchEvent.token("int", 2),
        MatchEvent.pop(),
        MatchEvent.pop(),

        MatchEvent.push(DimNode.Variant.LsRs),
        MatchEvent.token("[", 5),
        MatchEvent.token("]", 6),
        MatchEvent.pop(),
        MatchEvent.push(DimNode.Variant.LsRs),
        MatchEvent.token("[", 7),
        MatchEvent.token("]", 8),
        MatchEvent.pop(),

        MatchEvent.token(".", 9),
        MatchEvent.token("class", 10),
        MatchEvent.pop()
        ));

    BaseNode want = ClassLiteralNode.builder(
        ClassLiteralNode.Variant.NumericTypeDimDotClass)
        .add(
            NumericTypeNode.Variant.IntegralType.nodeBuilder()
            .add(
                IntegralTypeNode.Variant.Int.nodeBuilder()
                .build()
                )
            .build()
            )
        .add(DimNode.builder(DimNode.Variant.LsRs).build())
        .add(DimNode.builder(DimNode.Variant.LsRs).build())
        .build();

    assertEquals(got.toString(), want.toString());
    assertEquals(got, want);
  }
}
