package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.NodeTypeTables;

/**
 * A parser input.
 */
public abstract class Input {
  /**
   * True iff the input may contain
   * {@link NodeTypeTables#NONSTANDARD non-standard} productions.
   */
  public final boolean allowNonStandardProductions;

  /**
   * The content to parse.
   */
  public abstract CharSequence content();

  /**
   * Maps (indexIntoContent,
   */
  public final RatPack ratPack = new RatPack();

  /**
   * @param source diagnostic string describing the source of the content.
   */
  private Input(String source, boolean allowNonStandardProductions) {
    this.allowNonStandardProductions = allowNonStandardProductions;
  }


  /**
   * The index after any ignorable tokens like spaces and comments.
   *
   * @param index the start of input or an index just past the end of a token.
   */
  public int indexAfterIgnorables(int index) {
    return Ignorables.scanPastIgnorablesFrom(content(), index, null);
  }

  /**
   * The source position for the characters between the given indices.
   * @param left inclusive index into content.
   * @param right exclusive index into content.
   */
  public abstract SourcePosition getSourcePosition(int left, int right);

  /**
   * Like {@link #getSourcePosition(int, int)} but for a zero-width region.
   */
  public abstract SourcePosition getSourcePosition(int index);

  /**
   * A builder for inputs.
   */
  @SuppressWarnings("synthetic-access")
  public static Builder builder() {
    return new Builder();
  }

  private static final class TextInput extends Input {
    private final DecodedContent content;
    /**
     * The line-level structure of the source file.
     */
    private final LineStarts lineStarts;

    /**
     * @param encodedContent the code to parse.
     */
    @SuppressWarnings("synthetic-access")
    private TextInput(
        String source, String encodedContent,
        boolean allowNonStandardProductions) {
      super(source, allowNonStandardProductions);
      this.content = new DecodedContent(encodedContent);
      this.lineStarts = new LineStarts(source, encodedContent);
    }

    @Override
    public CharSequence content() {
      return content;
    }

    @Override
    public SourcePosition getSourcePosition(int left, int right) {
      return new SourcePosition(
          lineStarts, content.indexInEncoded(left),
          content.indexInEncoded(right));
    }

    @Override
    public SourcePosition getSourcePosition(int index) {
      int indexInEncoded = content.indexInEncoded(index);
      return new SourcePosition(
          lineStarts, indexInEncoded, indexInEncoded);
    }
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
      return new TextInput(source, code, allowNonStandardProductions);
    }
  }
}
