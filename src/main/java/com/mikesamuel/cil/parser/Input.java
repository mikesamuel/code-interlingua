package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.io.CharSource;

/**
 * A parser input.
 */
public final class Input {
  /**
   * The content to parse.
   */
  public final String content;
  /**
   * The line-level structure of the source file.
   */
  public final LineStarts lineStarts;

  /**
   * Maps (indexIntoContent,
   */
  public final RatPack ratPack = new RatPack();

  /**
   * @param source diagnostic string describing the source of the content.
   * @param input the code to parse.
   * @throws IOException if there is a failure reading input.
   */
  public Input(String source, CharSource input) throws IOException {
    this.content = input.read();
    this.lineStarts = new LineStarts(source, content);
  }
}
