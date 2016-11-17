package com.mikesamuel.cil.format;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A token sink that collects tokens onto a StringBuilder.
 */
public abstract class AbstractTokenSink implements TokenSink {
  private int line = 1;
  /** Index past last linebreak in sb or 0 if no such index. */
  private int startOfLine;
  private final List<Integer> indentStack = Lists.newArrayList();
  private boolean needSpace, needNewline;

  protected AbstractTokenSink() {
    indentStack.add(0);
  }

  /** Clone constructor. */
  protected AbstractTokenSink(AbstractTokenSink original) {
    resetTo(original);
  }

  protected void resetTo(AbstractTokenSink original) {
    this.line = original.line;
    this.startOfLine = original.startOfLine;

    this.indentStack.clear();
    this.indentStack.addAll(original.indentStack);

    this.needSpace = original.needSpace;
    this.needNewline = original.needNewline;
  }

  /**
   * Called internally to manage the underlying buffer.
   * Should increment charInFile by content.length().
   */
  protected abstract void appendTokenContent(String content);
  /**
   * Called internally to manage the underlying buffer.
   * Should increment charInFile by count.
   */
  protected abstract void appendSpaceChars(char ch, int count);

  @Override
  public void append(String tok) {
    Preconditions.checkState(
        tok.length() > 0 && tok.charAt(0) > 0x20,
        "Space should not be treated as tokens");
    this.prepareForToken();
    int startIndex = charInFile();
    appendTokenContent(tok);
    for (int i = 0, n = tok.length(); i < n; ++i) {
      char c = tok.charAt(i);
      if (c == '\n' || c == '\r') {
        ++line;
        if (c == '\r' && i + 1 < n && tok.charAt(i + 1) == '\n') {
          ++i;
        }
        startOfLine = startIndex + i + 1;
      }
    }
  }

  @Override
  public void indentTo(int column) {
    Preconditions.checkState(column >= indentation());
    indentStack.add(column);
  }

  @Override
  public void dedent() {
    int n = indentStack.size();
    Preconditions.checkState(n > 1);
    indentStack.remove(n - 1);
  }

  @Override
  public void newline() {
    this.needNewline = true;
  }

  @Override
  public void space() {
    this.needSpace = true;
  }

  @Override
  public void prepareForToken() {
    if (needNewline) {
      if (!atStartOfLine()) {
        appendSpaceChars('\n', 1);
        startOfLine = charInFile();
        ++line;
      }
      needNewline = false;
      needSpace = false;
      int indent = this.indentation();
      appendSpaceChars(' ', indent);
    }
    if (needSpace) {
      if (!atStartOfLine()) {
        appendSpaceChars(' ', 1);
      }
      needSpace = false;
    }
  }

  @Override
  public int indentation() {
    return indentStack.get(indentStack.size() - 1);
  }

  @Override
  public int column() {
    return charInFile() - startOfLine + 1;
  }

  @Override
  public int lineNumber() {
    return line;
  }

  @Override
  public boolean atStartOfLine() {
    return startOfLine == charInFile();
  }
}
