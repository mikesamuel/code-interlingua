package com.mikesamuel.cil.parser;

import java.util.Arrays;

/**
 * Indices of line starts in an input file from which we can infer the
 * line number and column given a character index into the source.
 */
public final class LineStarts {
  /**
   * File path, URI, or other diagnostic string describing the source of the
   * content.
   */
  public final String source;
  /** Strictly monotonic. */
  private final int[] startsOfLines;

  LineStarts(String source, CharSequence content) {
    this.source = source;
    int n = content.length();
    int[] starts = new int[1 + n / 16];
    int nLines = 1;
    for (int i = 0; i < n; ++i) {
      char ch = content.charAt(i);
      if (ch == '\n' || ch == '\r') {
        int startOfNext = i + 1;
        if (ch == '\r' && startOfNext < n
            && content.charAt(startOfNext) == '\n') {
          ++startOfNext;
        }
        if (nLines == starts.length) {
          int[] larger = new int[nLines * 2];
          System.arraycopy(starts, 0, larger, 0, nLines);
          starts = larger;
        }
        starts[nLines] = startOfNext;
        ++nLines;
      }
    }
    this.startsOfLines = new int[nLines];
    System.arraycopy(starts, 0, this.startsOfLines, 0, nLines);
  }

  /**
   * One less than {@link #getLineNumber}.
   */
  public int getZeroIndexedLineNumber(int charInFile) {
    int ip = Arrays.binarySearch(startsOfLines, charInFile);
    return (ip < 0 ? ~ip - 1 : ip);
  }

  /**
   * The line number in which the character at the given index occurs.
   *
   * @return one-indexed since source code editors typically treat the start
   *    of a file as position 1:1.
   */
  public int getLineNumber(int charInFile) {
    return 1 + getZeroIndexedLineNumber(charInFile);
  }

  /**
   * The column on which the character at the given index occurs.
   *
   * @return one-indexed because text editors typically treat the start of a
   *     file as position 1:1.
   */
  public int charInLine(int charInFile) {
    int ln = getZeroIndexedLineNumber(charInFile);
    return 1 + (charInFile - startsOfLines[ln]);
  }
}
