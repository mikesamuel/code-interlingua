package com.mikesamuel.cil.ast;

/**
 * Allows formatting comment tokens.
 * <p>
 * This is mostly of interest to {@link Trees.Decorator}s.
 */
public final class Java8Comments {
  private Java8Comments() {
    // Static API
  }

  /** A well-formed Java line comment. */
  public static String lineComment(String text) {
    StringBuilder sb = new StringBuilder(text.length() + 16);
    sb.append("// ");
    int pos = 0;
    int end = text.length();
    for (int i = 0; i < end; ++i) {
      char ch = text.charAt(i);
      if (ch == '\n' || ch == '\r') {
        int lineEnd = i + 1;
        if (ch == '\r' && lineEnd < end && text.charAt(lineEnd) == '\n') {
          ++lineEnd;
        }
        sb.append(text, pos, lineEnd);
        pos = lineEnd;
        sb.append("// ");
      }
    }
    sb.append(text, pos, end).append('\n');
    return sb.toString();
  }

  /** A well-formed Java line comment. */
  public static String blockComment(String text, boolean isJavaDoc) {
    return new StringBuilder(text.length() + 16)
        .append(isJavaDoc ? "/** " : "/* ")
        .append(text.replace("*/", "*\\u200C/"))
        .append(" */")
        .toString();
  }
}
