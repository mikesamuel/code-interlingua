package com.mikesamuel.cil.template;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.ImmutableList;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class TemplatesTest extends TestCase {

  private static final ImmutableList<Integer> ZERO_THROUGH_EIGHT =
      ImmutableList.of(0, 1, 2, 3, 4, 5, 6, 7, 8);

  @Test
  public static void testShift3RightBy2() {
    List<Integer> ls = new ArrayList<>(ZERO_THROUGH_EIGHT);
    Templates.shift(ls, 3, 6, 2);
    assertEquals("[0, 1, 2, 6, 7, 3, 4, 5, 8]", ls.toString());
  }

  @Test
  public static void testShift1RightBy3() {
    List<Integer> ls = new ArrayList<>(ZERO_THROUGH_EIGHT);
    Templates.shift(ls, 3, 4, 3);
    assertEquals("[0, 1, 2, 4, 5, 6, 3, 7, 8]", ls.toString());
  }

  @Test
  public static void testShift3LeftBy2() {
    List<Integer> ls = new ArrayList<>(ZERO_THROUGH_EIGHT);
    Templates.shift(ls, 3, 6, -2);
    assertEquals("[0, 3, 4, 5, 1, 2, 6, 7, 8]", ls.toString());
  }

  @Test
  public static void testShift1LeftBy3() {
    List<Integer> ls = new ArrayList<>(ZERO_THROUGH_EIGHT);
    Templates.shift(ls, 3, 4, -3);
    assertEquals("[3, 0, 1, 2, 4, 5, 6, 7, 8]", ls.toString());
  }

  @Test
  public static void testShift1ToEnd() {
    List<Integer> ls = new ArrayList<>(ZERO_THROUGH_EIGHT);
    Templates.shift(ls, 5, 6, 3);
    assertEquals("[0, 1, 2, 3, 4, 6, 7, 8, 5]", ls.toString());
  }
}
