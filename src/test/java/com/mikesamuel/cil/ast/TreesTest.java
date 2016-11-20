package com.mikesamuel.cil.ast;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.event.Event;
import com.mikesamuel.cil.parser.Input;
import com.mikesamuel.cil.parser.LineStarts;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TreesTest extends TestCase {

  @Test
  public static void testTreesOfIdentifier() {
    LineStarts starts = (Input.fromCharSequence("test-file", "  foo"))
        .lineStarts;

    BaseNode got = Trees.of(starts, ImmutableList.of(
        Event.push(IdentifierNode.Variant.Builtin),
        Event.content("foo", 2),
        Event.pop()));

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
  public static void testTreesOfWithInnerNodes() {
    LineStarts starts = Input.fromCharSequence("test-file", "  int[][].class")
        .lineStarts;
    BaseNode got = Trees.of(starts, ImmutableList.of(
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
