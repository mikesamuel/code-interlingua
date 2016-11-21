package com.mikesamuel.cil.parser;

/**
 * Detects token merge hazards.
 *
 * Since the parser is scannerless, a naive substring match might match a prefix
 * of a full token.
 */
public interface TokenMergeGuard {
  /**
   * True iff there is a marge hazard following the token
   * {@code content.substring(start, end)} in content.
   */
  boolean isHazard(CharSequence content, int start, int end);
}
