package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.NodeTypeTables;

/**
 * A parser input.
 */
public final class Input {
  /**
   * The content to parse.
   */
  public final DecodedContent content;
  /**
   * The line-level structure of the source file.
   */
  public final LineStarts lineStarts;
  /**
   * True iff the input may contain
   * {@link NodeTypeTables#NONSTANDARD non-standard} productions.
   */
  public final boolean allowNonStandardProductions;


  /**
   * Maps (indexIntoContent,
   */
  public final RatPack ratPack = new RatPack();

  /**
   * @param source diagnostic string describing the source of the content.
   * @param input the code to parse.
   * @throws IOException if there is a failure reading input.
   */
  private Input(
      String source, String encodedContent,
      boolean allowNonStandardProductions) {
    this.content = new DecodedContent(encodedContent);
    this.lineStarts = new LineStarts(source, encodedContent);
    this.allowNonStandardProductions = allowNonStandardProductions;
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
   * The source position for the characters between the given indices.
   * @param left inclusive index into content.
   * @param right exclusive index into content.
   */
  public SourcePosition getSourcePosition(int left, int right) {
    return new SourcePosition(
        lineStarts, content.indexInEncoded(left),
        content.indexInEncoded(right));
  }

  /**
   * Like {@link #getSourcePosition(int, int)} but for a zero-width region.
   */
  public SourcePosition getSourcePosition(int index) {
    int indexInEncoded = content.indexInEncoded(index);
    return new SourcePosition(
        lineStarts, indexInEncoded, indexInEncoded);
  }

  /**
   * A builder for inputs.
   */
  @SuppressWarnings("synthetic-access")
  public static Builder builder() {
    return new Builder();
  }

  /**
   * A builder for inputs.
   */
  public static final class Builder {
    private String source = "unknown";
    private String code = "";
    private boolean allowNonStandardProductions = false;

    private Builder() {
    }

    /**
     * Specifies the code content.  Not additive.
     * @param codeSource read immediately.
     */
    public Builder code(CharSource codeSource) throws IOException {
      this.code = codeSource.read();
      return this;
    }

    /**
     *
     */
    public Builder code(CharSequence codeChars) {
      this.code = codeChars.toString();
      return this;
    }

    /**
     *Specifies a diagnostic string describing the source of the content.
     */
    public Builder source(String sourceName) {
      this.source = Preconditions.checkNotNull(sourceName);
      return this;
    }

    /**
     * Calling with true enables {@link NodeTypeTables#NONSTANDARD non-standard}
     * productions.
     */
    public Builder allowNonStandardProductions(boolean allow) {
      this.allowNonStandardProductions = allow;
      return this;
    }

    /**
     * Returns the built input.
     */
    @SuppressWarnings("synthetic-access")
    public Input build() {
      return new Input(source, code, allowNonStandardProductions);
    }
  }
}
