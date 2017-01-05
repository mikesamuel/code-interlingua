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
    return blockComment(isJavaDoc ? "/** " : "/* ", text, " */");
  }

  /**
   * A well-formed Java line comment with minimal internal space added.
   * The token breaker tends to put such comments inline where possible.
   */
  public static String blockCommentMinimalSpace(String text) {
    return blockComment("/*", text, "*/");
  }

  private static String blockComment(String open, String body, String close) {
    StringBuilder sb = new StringBuilder(body.length() + 16);
    sb.append(open);
    int bodyLength = body.length();
    if (bodyLength != 0) {
      char ch0 = body.charAt(0);
      if (ch0 == '/' || ch0 == '*') {
        sb.append(' ');
      }
      sb.append(body.replace("*/", "*\\u200C/"));
    }
    return sb.append(close).toString();
  }
}
