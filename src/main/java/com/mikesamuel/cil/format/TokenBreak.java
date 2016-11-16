package com.mikesamuel.cil.format;

/**
 * Whether a token break is required, suggested, or optional.
 */
public enum TokenBreak {
  /**
   * A break should not occur between the tokens per usual formatting
   * conventions.
   */
  SHOULD_NOT,
  /**
   * A break may occur if convenient.
   * <p>
   * The difference between SHOULD_NOT and MAY is mostly relevant to line breaks
   * for example to indicate good line wrap point.
   */
  MAY,
  /**
   * A human reader would benefit from a break between the tokens in question.
   */
  SHOULD,
  /**
   * A break must occur between the tokens so that a lexer draws the same
   * conclusions about token boundaries.
   */
  MUST,
  ;
}
