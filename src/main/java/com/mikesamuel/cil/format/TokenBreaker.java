package com.mikesamuel.cil.format;

import javax.annotation.Nullable;

/**
 * Determines whether to break between tokens.
 *
 * @param <C> type of context hints used to disambiguate token uses.
 */
public interface TokenBreaker<C> {
  /**
   * Whether to break between the given tokens.
   *
   * @param left a token that appears immediately before right in the token
   *     stream.
   * @param leftStack hints about the context of the left stack.
   * @param rightStack hints about the context of the right stack.
   */
  TokenBreak breakBetween(
      String left,  @Nullable C leftContext,
      String right, @Nullable C rightContext);

  /**
   * Whether to break lines between the given tokens.
   */
  TokenBreak lineBetween(
      String left,  @Nullable C leftContext,
      String right, @Nullable C rightContext);
}
