package com.mikesamuel.cil.parser;

/**
 * A position within a source file.
 */
public final class SourcePosition {
  private final LineStarts starts;
  private final int charInFile;

  SourcePosition(LineStarts starts, int charInFile) {
    this.starts = starts;
    this.charInFile = charInFile;
  }

  /**
   * File path, URI, or other diagnostic string describing the source of the
   * content.
   */
  public String getSource() { return starts.source; }

  /**
   * Index of the character (UTF-16 offset) in file.
   */
  public int charInFile() { return charInFile; }

  /**
   * Column within the line.
   */
  public int charInLine() {
    return starts.charInLine(charInFile);
  }

  /**
   * Line number within the file.
   */
  public int lineInFile() { return starts.getLineNumber(charInFile); }

  @Override
  public String toString() {
    return getSource() + ":" + lineInFile() + ":" + charInLine();
  }
}
