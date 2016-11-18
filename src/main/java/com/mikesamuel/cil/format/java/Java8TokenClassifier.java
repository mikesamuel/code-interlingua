package com.mikesamuel.cil.format.java;

import com.google.common.base.Preconditions;

/**
 * Broad classification of tokens.
 */
final class Java8TokenClassifier {

  private Java8TokenClassifier() {
    // Static API
  }

  /** The classification of the given token. */
  static Classification classify(String token) {
    Preconditions.checkArgument(!token.isEmpty());
    int cp0 = token.codePointAt(0);
    switch (cp0) {
      case '\'': return Classification.CHAR_LITERAL;
      case '"': return Classification.STRING_LITERAL;
      case '.':
        if (token.length() > 1) {
          char c1 = token.charAt(1);
          if ('0' <= c1 && c1 <= '9') {
            return Classification.NUMBER_LITERAL;
          }
        }
        return Classification.PUNCTUATION;
      case '-': case '+':
        if (token.length() > 1) {
          // Be robust even when the string represents a negative number like
          // "-1" or "-.5" even though that is not technically a valid token.
          return classify(token.substring(1));
        }
        return Classification.PUNCTUATION;
      case '0': case '1': case '2': case '3': case '4': case '5': case '6':
      case '7': case '8': case '9':
        return Classification.NUMBER_LITERAL;
      case '/':
        if (token.length() > 1) {
          switch (token.charAt(1)) {
            case '/': return Classification.LINE_COMMENT;
            case '*': return Classification.BLOCK_COMMENT;
          }
        }
        return Classification.PUNCTUATION;
    }
    if (Character.isJavaIdentifierStart(cp0)) {
      return Classification.IDENTIFIER_CHARS;
    }
    return Classification.PUNCTUATION;
  }

  enum Classification {
    CHAR_LITERAL,
    IDENTIFIER_CHARS,
    NUMBER_LITERAL,
    STRING_LITERAL,
    PUNCTUATION,
    LINE_COMMENT,
    BLOCK_COMMENT,
  }
}
