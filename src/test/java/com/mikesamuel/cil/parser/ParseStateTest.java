package com.mikesamuel.cil.parser;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

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
    assertEquals(content, idxAfter + 3, ps1.advance(3).indexAfterIgnorables());
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
    assertTrue(ps.startsWith("f"));
    assertFalse(ps.startsWith("F"));
    assertTrue(ps.startsWith("fo"));
    assertFalse(ps.startsWith("food"));
    assertTrue(ps.advance(1).startsWith("oo"));
    assertFalse(ps.advance(1).startsWith("f"));
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
    assertTrue(ps.advance(3).matcherAtStart(pZ).find());
  }
}
