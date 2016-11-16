package com.mikesamuel.cil.format;


/**
 * Describes a gross structural element in a token set, like a pair of matching
 * curly brackets and their contents.
 */
public interface GrossStructure {
  /**
   * The number of lines needed to render the tokens when the given number of
   * columns are available.
   */
  int countLines(int columnsAvailable);
  /**
   * Write the tokens to the sink.
   */
  void appendTokens(TokenSink sink);
}
