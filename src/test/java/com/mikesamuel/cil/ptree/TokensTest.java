package com.mikesamuel.cil.ptree;

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

}
