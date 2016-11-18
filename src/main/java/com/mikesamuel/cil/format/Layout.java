package com.mikesamuel.cil.format;

/**
 * Given a sequence of tokens, arranges them out.
 */
public interface Layout<C> {
  /**
   * @return A gross structure that writes the tokens given in order with
   *     enough white-space between tokens to avoid lexical ambiguities.
   */
  GrossStructure layout(
      Iterable<? extends Formatter.DecoratedToken<C>> tokens,
      int softColumnLimit);
}
