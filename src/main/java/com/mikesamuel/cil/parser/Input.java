package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.io.CharSource;

/**
 * A parser input.
 */
public final class Input {
  /**
   * The content to parse.
   */
  public final String content;
  /**
   * The line-level structure of the source file.
   */
  public final LineStarts lineStarts;

  /**
   * Maps (indexIntoContent,
   */
  public final RatPack ratPack = new RatPack();

  /**
   * @param source diagnostic string describing the source of the content.
   * @param input the code to parse.
   * @throws IOException if there is a failure reading input.
   */
  public Input(String source, CharSource input) throws IOException {
    this.content = input.read();
    this.lineStarts = new LineStarts(source, content);
  }


  private static final long SPACE_BITS =
      (1L << ' ') | (1L << '\t') | (1L << '\f') | (1L << '\r') | (1L << '\n');

  /**
   * The index after any ignorable tokens like spaces and comments.
   *
   * @param index the start of input or an index just past the end of a token.
   */
  public int indexAfterIgnorables(int index) {
    int idx;
    int n = content.length();
    ign_loop:
    for (idx = index; idx < n; ++idx) {
      char ch = content.charAt(idx);
      if (ch < 64) {
        if ((SPACE_BITS & (1L << ch)) != 0) {
          continue;
        } else if (ch == '/' && idx + 1 < n) {
          char ch1 = content.charAt(idx + 1);
          if (ch1 == '/') {
            int commentEnd = idx + 2;
            for (; commentEnd < n; ++commentEnd) {
              char commentChar = content.charAt(commentEnd);
              if (commentChar == '\r' || commentChar == '\n') {
                break;
              }
            }
            idx = commentEnd - 1;  // increment above
            continue;
          } else if (ch1 == '*') {
            int commentEnd = idx + 2;
            for (; commentEnd < n; ++commentEnd) {
              char commentChar = content.charAt(commentEnd);
              if (commentChar == '*' && commentEnd + 1 < n) {
                if ('/' == content.charAt(commentEnd + 1)) {
                  // Incremented past '/' by for loop.
                  idx = commentEnd + (2 - 1);
                  continue ign_loop;
                }
              }
            }
            break;  // Unclosed comment.  TODO: Should error out.
          }
        }
      }
      break;
    }
    return idx;
  }
}
