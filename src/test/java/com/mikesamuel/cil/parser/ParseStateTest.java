package com.mikesamuel.cil.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.base.Optional;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class ParseStateTest extends TestCase {

  private static void assertIndexAfterIgnorables(String content, int idxNext) {
    ParseState ps0 = new ParseState(
        Input.fromCharSequence("test", content));
    assertEquals(content, idxNext, ps0.index);

    ParseState ps1 = new ParseState(
        Input.fromCharSequence("test", "xyz" + content));
    assertEquals(content, idxNext + 3, ps1.advance(3).index);
  }

  @Test
  public static void testIndexAfterIgnorables() {
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
  public static void testStartsWith() {
    ParseState ps = new ParseState(Input.fromCharSequence("test", "foo"));
    assertTrue(ps.startsWith("f", Optional.absent()));
    assertFalse(ps.startsWith("F", Optional.absent()));
    assertTrue(ps.startsWith("fo", Optional.absent()));
    assertFalse(ps.startsWith("food", Optional.absent()));
    assertTrue(ps.advance(1).startsWith("oo", Optional.absent()));
    assertFalse(ps.advance(1).startsWith("f", Optional.absent()));
  }

  @Test
  public static void testMatcherAt() {
    ParseState ps = new ParseState(Input.fromCharSequence("test", "foo"));

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
