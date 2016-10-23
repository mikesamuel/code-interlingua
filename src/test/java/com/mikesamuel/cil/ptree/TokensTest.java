package com.mikesamuel.cil.ptree;

import java.util.regex.Pattern;

import org.junit.Test;

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

}
