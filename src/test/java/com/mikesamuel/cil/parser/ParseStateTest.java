package com.mikesamuel.cil.parser;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ParseStateTest extends TestCase {

  private static void assertIndexAfterIgnorables(String content, int idxAfter)
  throws IOException {
    ParseState ps0 = new ParseState(
        new Input("test", CharSource.wrap(content)));
    assertEquals(content, idxAfter, ps0.indexAfterIgnorables());

    ParseState ps1 = new ParseState(
        new Input("test", CharSource.wrap("xyz" + content)));
    assertEquals(
        content, idxAfter + 3, ps1.advance(3, false).indexAfterIgnorables());
  }

  @Test
  public static void testIndexAfterIgnorables() throws Exception {
    assertIndexAfterIgnorables("", 0);
    assertIndexAfterIgnorables("foo", 0);
    assertIndexAfterIgnorables("  ", 2);
    assertIndexAfterIgnorables(" \t\f\n\r ", 6);
    assertIndexAfterIgnorables(" foo ", 1);
    assertIndexAfterIgnorables("//", 2);
    assertIndexAfterIgnorables("/", 0);
    assertIndexAfterIgnorables("///", 3);
    assertIndexAfterIgnorables("///\n", 4);
    assertIndexAfterIgnorables("///\nx", 4);
    assertIndexAfterIgnorables(" /**/", 5);
    assertIndexAfterIgnorables(" /***/", 6);
    assertIndexAfterIgnorables(" /* */", 6);
    assertIndexAfterIgnorables(" /* * */", 8);
    assertIndexAfterIgnorables(" /* **/", 7);
    assertIndexAfterIgnorables(" /*\r\n*/", 7);
    assertIndexAfterIgnorables(" /**\r\n*/", 8);
  }

  @Test
  public static void testStartsWith() throws IOException {
    ParseState ps = new ParseState(new Input("test", CharSource.wrap("foo")));
    assertTrue(ps.startsWith("f", Optional.absent()));
    assertFalse(ps.startsWith("F", Optional.absent()));
    assertTrue(ps.startsWith("fo", Optional.absent()));
    assertFalse(ps.startsWith("food", Optional.absent()));
    assertTrue(ps.advance(1, false).startsWith("oo", Optional.absent()));
    assertFalse(ps.advance(1, false).startsWith("f", Optional.absent()));
  }

  @Test
  public static void testMatcherAt() throws IOException {
    ParseState ps = new ParseState(new Input("test", CharSource.wrap("foo")));

    Matcher mfoo = ps.matcherAtStart(Pattern.compile("^foo"));
    assertTrue(mfoo.find());

    Matcher mFoo = ps.matcherAtStart(Pattern.compile("^Foo"));
    assertFalse(mFoo.find());

    Matcher mbar = ps.matcherAtStart(Pattern.compile("^bar"));
    assertFalse(mbar.find());

    Pattern pZ = Pattern.compile("^\\Z");
    assertFalse(ps.matcherAtStart(pZ).find());
    assertTrue(ps.advance(3, false).matcherAtStart(pZ).find());
  }


  @Test
  public static void testTokenBreaks() throws IOException {
    String code = Joiner.on('\n').join(
        "",
        "package foo.bar.baz;",
        "",
        "  ",
        "// Foo Bar Baz",
        "",
        "/** A jdoc comment */class Foo",
        "extends/**/Bar{",
        "  java.lang .",
        "String s;",
        "}");

    ImmutableList<String> want = ImmutableList.of(
        "#\n",
        "!package",
        "# ",
        "!foo.bar.baz;",
        "#\n\n  \n// Foo Bar Baz\n\n/** A jdoc comment */",
        "!class",
        "# ",
        "!Foo",
        "#\n",
        "!extends",
        "#/**/",
        "!Bar{",
        "#\n  ",
        "!java.lang",
        "# ",
        "!.",
        "#\n",
        "!String",
        "# ",
        "!s;",
        "#\n",
        "!}");

    ImmutableList.Builder<String> gotBuilder = ImmutableList.builder();

    ParseState state = new ParseState(
        new Input("test", CharSource.wrap(code)));
    StringBuilder token = new StringBuilder();
    while (!state.isEmpty()) {
      int idx = state.indexAfterIgnorables();
      int end;
      if (idx != state.index) {
        if (token.length() != 0) {
          gotBuilder.add("!" + token);
          token.setLength(0);
        }
        gotBuilder.add("#" + state.input.content.substring(state.index, idx));
        end = idx;
      } else {
        token.append(state.input.content.charAt(idx));
        end = idx + 1;
      }
      state = state.withIndex(end);
    }
    if (token.length() != 0) {
      gotBuilder.add("!" + token);
      token.setLength(0);
    }

    ImmutableList<String> got = gotBuilder.build();
    if (!want.equals(got)) {
      Joiner j = Joiner.on("\n======\n");
      assertEquals(j.join(want), j.join(got));
      assertEquals(want, got);
    }
  }
}
