package com.mikesamuel.cil.parser;

import com.google.common.base.Preconditions;

/**
 * A range of characters within a source file.
 * <p>
 * Starts are inclusive, ends are exclusive.
 */
public final class SourcePosition {
  private final LineStarts starts;
  private final int startCharInFile;
  private final int endCharInFile;

  /** */
  public SourcePosition(
      LineStarts starts, int startCharInFile, int endCharInFile) {
    this.starts = starts;
    this.startCharInFile = startCharInFile;
    this.endCharInFile = endCharInFile;
  }

  /**
   * A source position that starts at the least of the starts of the two
   * given positions and ends at the greater of the ends.
   */
  public static SourcePosition spanning(SourcePosition a, SourcePosition b) {
    Preconditions.checkArgument(a.starts == b.starts);
    return new SourcePosition(
        a.starts,
        Math.min(a.startCharInFile, b.startCharInFile),
        Math.max(a.endCharInFile, b.endCharInFile));
  }

  /**
   * A position that is shifted by the given number of characters within the
   * file.
   */
  public SourcePosition shift(int delta) {
    if (delta == 0) { return this; }
    int newStartCharInFile = startCharInFile + delta;
    Preconditions.checkArgument(newStartCharInFile >= 0);
    return new SourcePosition(
        starts, newStartCharInFile, endCharInFile + delta);
  }

  /**
   * File path, URI, or other diagnostic string describing the source of the
   * content.
   */
  public String getSource() { return starts.source; }

  /**
   * Index of the character (UTF-16 offset) in file of the start.
   */
  public int startCharInFile() { return startCharInFile; }

  /**
   * Column within the line of the start.
   */
  public int startCharInLine() {
    return starts.charInLine(startCharInFile);
  }

  /**
   * Line number within the file of the start.
   */
  public int startLineInFile() { return starts.getLineNumber(startCharInFile); }

  /**
   * Index of the character (UTF-16 offset) in file of the end.
   */
  public int endCharInFile() { return endCharInFile; }

  /**
   * Column within the line of the end.
   */
  public int endCharInLine() {
    return starts.charInLine(endCharInFile);
  }

  /**
   * Line number within the file of the end.
   */
  public int endLineInFile() { return starts.getLineNumber(endCharInFile); }

  /** A zero-width position at the start of this. */
  public SourcePosition start() {
    return startCharInFile == endCharInFile
        ? this
        : new SourcePosition(starts, startCharInFile, startCharInFile);
  }

  /** A zero-width position at the start of this. */
  public SourcePosition end() {
    return startCharInFile == endCharInFile
        ? this
        : new SourcePosition(starts, endCharInFile, endCharInFile);
  }


  @Override
  public String toString() {
    int startLineInFile = startLineInFile();
    int startCharInLine = startCharInLine();
    int endLineInFile = endLineInFile();
    int endCharInLine = endCharInLine();
    StringBuilder sb = new StringBuilder();
    sb.append(getSource())
        .append(":")
        .append(startLineInFile)
        .append("+")
        .append(startCharInLine);
    if (startLineInFile != endLineInFile) {
      sb.append(" - ").append(endLineInFile).append('+').append(endCharInLine);
    } else if (startCharInLine != endCharInLine) {
      sb.append('-').append(endCharInLine);
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + endCharInFile;
    result = prime * result + startCharInFile;
    result = prime * result + ((starts == null) ? 0 : starts.source.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    SourcePosition other = (SourcePosition) obj;
    if (endCharInFile != other.endCharInFile) {
      return false;
    }
    if (startCharInFile != other.startCharInFile) {
      return false;
    }
    if (starts == null) {
      if (other.starts != null) {
        return false;
      }
    } else if (!starts.source.equals(other.starts.source)) {
      return false;
    }
    return true;
  }
}
