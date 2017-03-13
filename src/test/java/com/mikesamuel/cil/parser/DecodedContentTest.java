package com.mikesamuel.cil.parser;

import org.junit.Test;

import com.mikesamuel.cil.parser.DecodedContent;

import junit.framework.TestCase;

@SuppressWarnings("javadoc")
public final class DecodedContentTest extends TestCase {

  @Test
  public static void testDecodedInput() {
    DecodedContent di = new DecodedContent(
        "Foo\\u000ABar\\u000D\\u005C\\u005c");
    assertEquals(
        "Foo\nBar\r\\\\u005c", di.toString());

    //                                                  1 1 1 1 1 1
    // 0 1 2 3          4 5 6 7           8           9 0 1 2 3 4 5
    // F o o #          B a r #           #           \ u 0 0 5 c
    // F o o \ u0 0 0 A B a r \ u 0 0 0 D \ u 0 0 5 c \ u 0 0 5 c
    //                     1 1 1 1 1 1 1 1 1 1 2 2 2 2 2 2 2 2 2 2 3
    // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0

    int[][] correspondences = new int[][] {
        { 0, 0 },
        { 1, 1 },
        { 2, 2 },
        { 3, 3 },
        { 4, 9 },
        { 5, 10 },
        { 6, 11 },
        { 7, 12 },
        { 8, 18 },
        { 9, 24 },
        { 10, 25 },
        { 11, 26 },
        { 12, 27 },
        { 13, 28 },
        { 14, 29 },
        { 15, 30 },
    };

    for (int[] corr : correspondences) {
      assertEquals(corr[1], di.indexInEncoded(corr[0]));
    }
  }
}
