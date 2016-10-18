package com.mikesamuel.cil.parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.MatchEvent;

/**
 * State of a parse.
 */
public final class ParseState {
  /** The input to parse. */
  public final Input input;
  /** The position of the parse cursor in the index. */
  public final int index;
  /** The output which can be replayed for a tree builder. */
  public final @Nullable Chain<MatchEvent> output;
  /** Cache the index after ignorable tokens like spaces and comments. */
  private int indexAfterIgnorable = -1;

  /** A parse state at the beginning of input with no output. */
  public ParseState(Input input) {
    this(input, 0, null);
  }

  private ParseState(
      Input input, int index, @Nullable Chain<? extends MatchEvent> output) {
    Preconditions.checkState(0 <= index && index <= input.content.length());
    this.input = input;
    this.index = index;
    this.output = output != null ? Chain.<MatchEvent>copyOf(output) : null;
  }

  /** True if no unprocessed input except for ignorable tokens. */
  public boolean isEmpty() {
    return indexAfterIgnorables() == input.content.length();
  }

  /** A state whose parse point is n chars forward of {@link #index} */
  public ParseState advance(int n) {
    Preconditions.checkArgument(n >= 0);
    if (n == 0) { return this; }
    int newIndex = index + n;
    Preconditions.checkState(newIndex <= input.content.length());
    return new ParseState(input, newIndex, output);
  }

  /** A state like this but with the given event appended. */
  public ParseState appendOutput(MatchEvent e) {
    ParseState ps = new ParseState(input, index, Chain.append(output, e));
    ps.indexAfterIgnorable = this.indexAfterIgnorable;
    return ps;
  }

  /**
   * A state like this but with the given output.
   */
  public ParseState withOutput(Chain<? extends MatchEvent> newOutput) {
    return new ParseState(input, index, newOutput);
  }

  /**
   * True if the next token after any ignorable tokens has the given text.
   * Since this parser is scannerless, this will return true if the given text
   * is a prefix of the next token.
   */
  public boolean startsWith(String text) {
    return input.content.regionMatches(
        indexAfterIgnorables(), text, 0, text.length());
  }

  /**
   * A matcher for the given pattern at the index after any ignorable tokens.
   */
  public Matcher matcherAtStart(Pattern p) {
    Matcher m = p.matcher(input.content);
    m.region(indexAfterIgnorables(), input.content.length());
    m.useTransparentBounds(false);
    m.useAnchoringBounds(true);
    return m;
  }

  private static final long SPACE_BITS =
      (1L << ' ') | (1L << '\t') | (1L << '\f') | (1L << '\r') | (1L << '\n');
  /** The index after any igorable tokens like spaces and comments. */
  public int indexAfterIgnorables() {
    if (this.indexAfterIgnorable < 0) {
      int idx;
      String content = input.content;
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
      indexAfterIgnorable = idx;
    }
    return indexAfterIgnorable;
  }
}
