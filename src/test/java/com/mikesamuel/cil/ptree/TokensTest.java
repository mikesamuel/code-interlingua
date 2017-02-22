package com.mikesamuel.cil.ptree;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.TokenStrings;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TokensTest extends TestCase {

  @Test
  public static void testIntegerLiteral() {
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("1").matches());
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("01").matches());
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("0x1").matches());
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("0b1").matches());
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("123L").matches());
    assertTrue(Tokens.INTEGER_LITERAL.p.matcher("0xA0").matches());
  }

  @Test
  public static void testFloatLiteral() {
    Pattern p = Tokens.FLOATING_POINT_LITERAL.p;
    assertTrue(p.matcher("1.0").matches());
    assertTrue(p.matcher(".5").matches());
    assertTrue(p.matcher("1.05").matches());
    assertTrue(p.matcher(".5e-2").matches());
    assertTrue(p.matcher(".5E23").matches());
    assertTrue(p.matcher(".5E23").matches());
  }

  @Test
  public static void testStringLiteral() {
    Pattern p = Tokens.STRING_LITERAL.p;
    Matcher mTwo = p.matcher("\"foo\" + \"bar\"");
    assertTrue(mTwo.find());
    assertEquals("\"foo\"", mTwo.group());
  }

  @Test
  public static void testKeywordMatching() {
    Pattern p = Pattern.compile(Tokens.KEYWORD_OR_BOOLEAN_OR_NULL);

    for (String word : TokenStrings.RESERVED) {
      Matcher m = p.matcher(word);
      assertTrue(m.find());
      assertEquals(word, m.group());
    }
  }

  @Test
  public static void testPuncSuffixes() {
    assertEquals(
        ImmutableList.of("..."),
        ImmutableList.copyOf(Tokens.punctuationSuffixes(".")));
    assertEquals(
        ImmutableList.of(),
        ImmutableList.copyOf(Tokens.punctuationSuffixes("...")));
    assertEquals(
        ImmutableList.of("++", "+="),
        ImmutableList.copyOf(Tokens.punctuationSuffixes("+")));
    assertEquals(
        ImmutableList.of(">=", ">>", ">>=", ">>>", ">>>="),
        ImmutableList.copyOf(Tokens.punctuationSuffixes(">")));
  }

  @Test
  public static void testIdentifier() {
    assertTrue(
        Tokens.IDENTIFIER.p.matcher("donut").matches());
  }

  @Test
  public static void testIdentiiferKeywordAmbiguity() {
    Pattern ident = Tokens.IDENTIFIER.p;
    for (String word : TokenStrings.RESERVED) {
      assertFalse(word, ident.matcher(word).matches());
      assertTrue(word, ident.matcher(word + "z").matches());
    }
  }

  @Test
  public static void testDecodeChar() {
    String encoded = "foo\\b\\n\\f\\r\\t\\0\\012\\470\\\\\"\\'";
    String decoded = "foo\b\n\f\r\t\0\012\470\\\"\'";

    StringBuilder sb = new StringBuilder();
    for (int i = 0, n = encoded.length(); i < n;) {
      long j = Tokens.decodeChar(encoded, i);
      if (j == -1L) {
        fail("Cannot decode char at " + i);
      }
      i = (int) (j >>> 32);
      sb.append((char) j);
    }

    assertEquals(decoded, sb.toString());

    assertEquals(-1, Tokens.decodeChar("\\", 0));
    assertEquals(2L << 32, Tokens.decodeChar("\\0", 0));

    for (int i = 0; i <= 0377; ++i) {
      String octal = "\\" + Integer.toOctalString(i);
      long j = Tokens.decodeChar(octal, 0);
      assertEquals(octal.length(), j >>> 32);
      assertEquals(i, (int) j);
    }
  }

  @Test
  public static void testDecodeOfEncode() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int[] range : new int[][] {
           { 0, 0x301 }, { 0xFFF, 0xC000 }, { 0xE000, 0x1001F }
         }) {
      int lt = range[0], rt = range[1];
      for (int i = lt; i <= rt; ++i) {
        sb.setLength(0);
        Tokens.encodeCodepointOnto(i, sb);

        String enc = sb.toString();
        sb.setLength(0);
        for (int p = 0, n = enc.length(); p < n;) {
          long d = Tokens.decodeChar(enc, p);
          sb.appendCodePoint((int) (d & 0xffffffffL));
          p = (int) (d >> 32);
        }
        int ii = sb.codePointAt(0);
        assertEquals(enc, i, ii);
        assertEquals(enc, sb.length(), Character.charCount(ii));
      }
    }
  }

}
