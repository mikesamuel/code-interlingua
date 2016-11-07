package com.mikesamuel.cil.parser;

import java.io.IOException;

import org.junit.Test;

import com.google.common.io.CharSource;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public class Lookahead1Test extends TestCase {

  @Test
  public void testIdentStart() throws IOException {
    Lookahead1 la1 = Lookahead1.of(
        "!", "\"", "'", ".0-9", "0-9", "@", "\\(", "\\+", "\\-",
        "\\p{javaJavaIdentifierStart}", "~");
    Input input = new Input(getName(), CharSource.wrap("."));
    assertTrue(la1.canFollow(new ParseState(input)));
  }

}
