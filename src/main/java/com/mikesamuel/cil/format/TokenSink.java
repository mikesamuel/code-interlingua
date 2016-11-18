package com.mikesamuel.cil.format;

import com.google.common.base.Preconditions;

/**
 * An output channel that receives tokens and whitespace hints.
 */
public interface TokenSink {
  /**
   * Appends a token.
   *
   * @param tok a whole non-whitespace token.
   */
  void append(String tok, MultilineAdjust multilineAdjust);
  /**
   * Indent to the given column.
   */
  void indentTo(int column);
  /**
   * @param delta >= 0
   */
  default void indentBy(int delta) {
    Preconditions.checkArgument(delta >= 0);
    indentTo(indentation() + delta);
  }
  /**
   * Undoes the last indent.
   */
  void dedent();
  /** Writes */
  void newline();
  /** Writes a space if the last character written is not a space character. */
  void space();

  /**
   * Writes any pending white-space needed before a token.  This allows
   * querying the {@link #column} and {@link #lineNumber}, and
   * {@link #charInFile} before writing a token to get an accurate picture
   * of the position of the first character in the token.
   * <p>
   * A no-op if the sink is prepared for the next token.
   */
  void prepareForToken();

  /**
   * The column to which new lines will be indented.
   */
  int indentation();

  /**
   * The column (1-indexed) past the last character written or 1
   * if no characters written.
   */
  int column();
  /**
   * 1 + the number of line breaks written.
   */
  int lineNumber();
  /**
   * The number of characters written.
   * <p>
   * This does not take into account any indentation for an
   * un{@linkplain #prepareForToken prepare}d new line.
   */
  int charInFile();

  /**
   * True if no characters have been written or the last character written
   * was part of a line break.
   */
  boolean atStartOfLine();


  /** What to do with multiline tokens. */
  public enum MultilineAdjust {
    /**
     * Line breaks within a token are counted but the token content is appended
     * as-is.
     */
    AS_IS,
    /**
     * Indentation is inserted after a line-break.
     */
    INDENT,

    // TODO: We should probably dedent and then reindent Javadoc comments
    // instead of successively indenting so that formatting is idempotentish.
  }
}
