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


  /**
   * The index after any ignorable tokens like spaces and comments.
   *
   * @param index the start of input or an index just past the end of a token.
   */
  public int indexAfterIgnorables(int index) {
    return Ignorables.scanPastIgnorablesFrom(content, index, null);
  }


  /**
   * Like {@link Input#Input(String, CharSource)} but takes a CharSequence
   * instead of a CharSource does not need to throw an IOException.
   *
   * @param sourceDescription not the string to parse.
   * @param sourceCode the string to parse.
   */
  public static Input fromCharSequence(
      String sourceDescription, CharSequence sourceCode) {
    try {
      return new Input(sourceDescription, CharSource.wrap(sourceCode));
    } catch (IOException ex) {
      throw (AssertionError)
          new AssertionError("CharSource.wrap's result should not throw")
          .initCause(ex);
    }
  }
}
