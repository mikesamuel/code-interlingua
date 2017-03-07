package com.mikesamuel.cil.parser;

import org.junit.Test;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class LineStartsTest extends TestCase {

  @Test
  public static void test() {
    String code = "foo\nbar\n\nbaz";
    //                          111
    //             0123 4567 8 9012
    int[] lineNumbers = new int[] {
        1, 1, 1, 1,
        2, 2, 2, 2,
        3,
        4, 4, 4,
        // After end of file
        4, 4, 4,
    };
    int[] colNumbers = new int[] {
        0, 1, 2, 3,
        0, 1, 2, 3,
        0,
        0, 1, 2,
        // After end of file
        3, 4, 5,
    };

    LineStarts ls = new LineStarts("foo", code);
    for (int i = 0; i < lineNumbers.length; ++i) {
      assertEquals(
          "char #" + i,
          lineNumbers[i],
          ls.getLineNumber(i));
      assertEquals(
          "char #" + i,
          colNumbers[i],
          ls.charInLine(i));
    }
    assertEquals("foo", ls.source);
  }

}
