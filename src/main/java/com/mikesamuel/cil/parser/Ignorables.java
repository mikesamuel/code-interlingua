package com.mikesamuel.cil.parser;

import javax.annotation.Nullable;

/**
 * A hand-crafted scanner that recognizes ignorable tokens.
 */
public final class Ignorables {
  private static final long SPACE_BITS =
      (1L << ' ') | (1L << '\t') | (1L << '\f') | (1L << '\r') | (1L << '\n');

  /**
   * The index after any ignorable tokens like spaces and comments.
   *
   * @param content the string to scan.
   * @param startIndex the start of input or an index just past the end of a token.
   * @param commentReceiver if not null, called for each comment
   *      encountered in left-to-right order.
   * @return the index of the first character after start that is not part of an ignorable
   *      token
   */
  public static int scanPastIgnorablesFrom(
      String content,
      int startIndex, @Nullable CommentReceiver commentReceiver) {
    int idx;
    int n = content.length();
    ign_loop:
    for (idx = startIndex; idx < n; ++idx) {
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
            if (commentReceiver != null) {
              commentReceiver.comment(idx, content.substring(idx, commentEnd));
            }
            idx = commentEnd - 1;  // Adjust for increment above
            continue;
          } else if (ch1 == '*') {
            int commentEnd = idx + 2;
            for (; commentEnd < n; ++commentEnd) {
              char commentChar = content.charAt(commentEnd);
              if (commentChar == '*' && commentEnd + 1 < n) {
                if ('/' == content.charAt(commentEnd + 1)) {
                  commentEnd += 2;  // Step past */
                  if (commentReceiver != null) {
                    commentReceiver.comment(
                        idx, content.substring(idx, commentEnd));
                  }
                  idx = commentEnd - 1;  // Adjust for incement above.
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


  /**
   * Allows introspection over comment tokens.
   */
  public interface CommentReceiver {
    /**
     * @param startIndex the startIndex of content in the input.
     * @param text the text of the comment including any delimiters.
     */
    void comment(int startIndex, String text);
  }

}
