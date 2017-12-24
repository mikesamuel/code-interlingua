package com.mikesamuel.cil.parser;

import java.io.IOException;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.event.Event;

/**
 * A parser input.
 */
public abstract class Input {
  /**
   * True iff the input may contain
   * {@link NodeType#isNonStandard non-standard} productions.
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
  private Input(boolean allowNonStandardProductions) {
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


  /** An input used to reapply the parser to already decoded fragments. */
  private static final class PredecodedInput extends Input {
    private final String content;
    private final LineStarts lineStarts;

    @SuppressWarnings("synthetic-access")
    private PredecodedInput(
        String source, String predecodedContent,
        boolean allowNonStandardProductions) {
      super(allowNonStandardProductions);
      this.content = predecodedContent;
      this.lineStarts = new LineStarts(source, content);
    }

    @Override
    public CharSequence content() {
      return content;
    }

    @Override
    public SourcePosition getSourcePosition(int left, int right) {
      return new SourcePosition(lineStarts, left, right);
    }

    @Override
    public SourcePosition getSourcePosition(int index) {
      return new SourcePosition(lineStarts, index, index);
    }
  }


  /** An input that needs baskslash u decoding. */
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
      super(allowNonStandardProductions);
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


  private static final class EventInput extends Input {
    private final TokenAndContentText tokenAndContentText;

    @SuppressWarnings("synthetic-access")
    private EventInput(
        ImmutableList<Event> events, boolean allowNonStandardProductions) {
      super(allowNonStandardProductions);
      this.tokenAndContentText = new TokenAndContentText(events);
    }

    @Override
    public CharSequence content() {
      return tokenAndContentText;
    }

    @Override
    public SourcePosition getSourcePosition(int left, int right) {
      return tokenAndContentText.getSourcePosition(left, right);
    }

    @Override
    public SourcePosition getSourcePosition(int index) {
      return tokenAndContentText.getSourcePosition(index);
    }
  }


  /**
   * A builder for inputs.
   */
  public static final class Builder {
    private String source = "unknown";
    private String code = null;
    private ImmutableList<Event> events = null;
    private boolean allowNonStandardProductions = false;
    private boolean isAlreadyDecoded = false;

    private Builder() {
    }

    /**
     * Specifies the code content.  Not additive.
     * @param codeSource read immediately.
     * @throws IOException on failure to read from codeSource.
     * @throws IllegalStateException if {@link #events(Iterable)} have been
     *     specified.
     */
    public Builder code(CharSource codeSource) throws IOException {
      return code(codeSource.read());
    }

    /**
     * Specifies the code content.  Not additive.
     *
     * @throws IllegalStateException if {@link #events(Iterable)} have been
     *     specified.
     */
    public Builder code(CharSequence codeChars) {
      Preconditions.checkState(
          events == null,
          "At most one of code or events may be specified");
      this.code = codeChars.toString();
      return this;
    }

    /**
     * Specifies the content based on a series of events.
     * Not additive.
     *
     * @param newEvents whose {@linkplain Event#getContent() content} specifies
     *     the tokens in the inputs.
     *     This event stream should include {@link Event#positionMark}s that
     *     can associate source positions with the parsed output.
     */
    public Builder events(Iterable<? extends Event> newEvents) {
      Preconditions.checkState(
          code == null,
          "At most one of code or events may be specified");
      this.events = ImmutableList.copyOf(newEvents);
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
     * Calling with true enables {@link NodeType#isNonStandard() non-standard}
     * productions.
     */
    public Builder allowNonStandardProductions(boolean allow) {
      this.allowNonStandardProductions = allow;
      return this;
    }

    /**
     * True if content has already been decoded - any <tt>\</tt><ttt>u....</tt>
     * sequences have already been replaced with the specified code-point.
     */
    public Builder setFragmentOfAlreadyDecodedInput(boolean alreadyDecoded) {
      this.isAlreadyDecoded = alreadyDecoded;
      return this;
    }

    /**
     * Returns the built input.
     */
    @SuppressWarnings("synthetic-access")
    public Input build() {
      if (events != null) {
        return new EventInput(events, allowNonStandardProductions);
      }
      if (isAlreadyDecoded) {
        return new PredecodedInput(
            source, code != null ? code : "",
            allowNonStandardProductions);
      }
      return new TextInput(
          source, code != null ? code : "", allowNonStandardProductions);
    }
  }
}
