package com.mikesamuel.cil.ast.j8;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class IdentifierNodeTest extends TestCase {

  @Test
  public static void testIsValidValue() {
    IdentifierNode id = IdentifierNode.Variant.Builtin.buildNode("x");
    assertTrue(id.isValidValue("y"));
    assertTrue(id.isValidValue("bar"));
    assertTrue(id.isValidValue("Bar"));
    assertTrue(id.isValidValue("y"));
  }
}