package com.mikesamuel.cil.format;


/**
 * Describes a gross structural element in a token set, like a pair of matching
 * curly brackets and their contents.
 */
public interface GrossStructure {
  /**
   * Write the tokens to the sink.
   */
  void appendTokens(TokenSink sink, int softColumnLimit);
}
