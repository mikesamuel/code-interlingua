package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.io.CharSource;

/**
 * A parser input.
 */
public final class Input {
  final String content;
  final LineStarts linebreaks;

  /**
   * @param source diagnostic string describing the source of the content.
   * @param input the code to parse.
   * @throws IOException if there is a failure reading input.
   */
  public Input(String source, CharSource input) throws IOException {
    this.content = input.read();
    this.linebreaks = new LineStarts(source, content);
  }

  /**
   * The content to parse.
   */
  public String getContent() {
    return content;
  }

  /**
   * The line-level structure of the source file.
   */
  public LineStarts getLinebreaks() {
    return linebreaks;
  }
}
