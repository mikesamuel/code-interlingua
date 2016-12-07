package com.mikesamuel.cil.format;

import java.util.List;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

/**
 * A token sink that collects tokens onto a StringBuilder.
 */
public abstract class AbstractTokenSink implements TokenSink {
  private int line = 1;
  /** Index past last linebreak in the output buffer or 0 if no such index. */
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
   *
   * @param adjustedContent after {@link MultilineAdjust}.
   */
  protected abstract void appendTokenContent(
      String content, String adjustedContent);
  /**
   * Called internally to manage the underlying buffer.
   * Should increment charInFile by count.
   */
  protected abstract void appendSpaceChars(char ch, int count);

  @Override
  public void append(String tok, MultilineAdjust multilineAdjust) {
    Preconditions.checkState(
        tok.length() > 0 && tok.charAt(0) > 0x20,
        "Space should not be treated as tokens");
    this.prepareForToken();
    int startIndex = charInFile();
    String adjustedToken = adjustToken(tok, multilineAdjust);
    appendTokenContent(tok, adjustedToken);
    for (int i = 0, n = adjustedToken.length(); i < n; ++i) {
      char c = adjustedToken.charAt(i);
      if (c == '\n' || c == '\r') {
        ++line;
        if (c == '\r' && i + 1 < n && adjustedToken.charAt(i + 1) == '\n') {
          ++i;
        }
        startOfLine = startIndex + i + 1;
      }
    }
  }

  protected String adjustToken(String token, MultilineAdjust adj) {
    switch (adj) {
      case AS_IS:
        return token;
      case INDENT:
        int n = token.length();
        int indent = this.indentation();
        StringBuilder adjusted = null;
        int i = 0, lineStart = 0;
        while (true) {
          int lineEnd = -1;
          if (i == n) {
            if (lineStart == 0) { return token; }
            lineEnd = n;
          } else {
            char ch = token.charAt(i);
            if (ch == '\r' || ch == '\n') {
              lineEnd = i + 1;
              if (ch == '\r' && i + 1 < n && '\n' == token.charAt(lineEnd)) {
                lineEnd = i + 2;
              }
            }
          }
          if (lineEnd < 0) {
            ++i;
          } else {
            if (adjusted == null) {
              adjusted = new StringBuilder(n * 2);
            }
            adjusted.ensureCapacity(
                adjusted.length() + lineEnd - lineStart + indent);
            if (lineStart != 0) {
              for (int j = indent; --j >= 0;) {
                adjusted.append(' ');
              }
            }
            adjusted.append(token, lineStart, lineEnd);
            lineStart = lineEnd;
            i = lineEnd;
            if (lineEnd == n) {
              return adjusted.toString();
            }
          }
        }
    }
    throw new AssertionError(adj);
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
    }
    if (atStartOfLine()) {
      int indent = this.indentation();
      appendSpaceChars(' ', indent);
      needSpace = false;
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
