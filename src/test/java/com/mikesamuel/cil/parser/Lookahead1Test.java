package com.mikesamuel.cil.parser;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class Lookahead1Test extends TestCase {

  @Test
  public void testIdentStart() {
    Lookahead1 la1 = Lookahead1.of(
        "!", "\"", "'", ".0-9", "0-9", "@", "\\(", "\\+", "\\-",
        "\\p{javaJavaIdentifierStart}", "~");
    Input input = Input.fromCharSequence(getName(), ".");
    assertTrue(la1.canFollow(new ParseState(input)));
  }

}
