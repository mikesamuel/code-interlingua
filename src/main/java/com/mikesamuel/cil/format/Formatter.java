package com.mikesamuel.cil.format;

import java.util.List;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Converts a token stream to formatted source code.
 */
public class Formatter<C> {
  /** Used to figure out how to break tokens into lines and indent the lines. */
  public final Function<? super ImmutableList<DecoratedToken<C>>,
                        ? extends GrossStructure> grossStructurer;
  private final ImmutableList.Builder<DecoratedToken<C>> tokens =
      ImmutableList.builder();
  private SourcePosition sourcePosition;
  private C context;

  /** */
  public Formatter(
      Function<? super ImmutableList<DecoratedToken<C>>,
               ? extends GrossStructure> grossStructurer) {
    this.grossStructurer = grossStructurer;
  }

  /**
   * Specifies a token that occurs after all previously specified tokens in the
   * token stream.
   */
  public void token(String tok) {
    tokens.add(new DecoratedToken<>(tok, this.sourcePosition, this.context));
  }

  /**
   * Sets a source position that will apply to tokens specified after this call
   * and before the next call to this method.
   */
  public void sourcePosition(SourcePosition newSourcePosition) {
    this.sourcePosition = newSourcePosition;
  }

  /**
   * Sets a token context that will apply to tokens specified after this call
   * and before the next call to this method.
   */
  public void context(@Nullable C newContext) {
    this.context = newContext;
  }

  /**
   * Appends all previously specified tokens to the given sink with appropriate
   * formatting commands.
   *
   * @param softColumnLimit soft column limit for formatted code.
   *     The column limit might be violated if, e.g. a single token exceeds the
   *     column width, or excessive required indentation would lead to one token
   *     per line.
   */
  public FormattedSource format(int softColumnLimit) {
    StringBuilderTokenSink sink = new StringBuilderTokenSink();
    ImmutableList<DecoratedToken<C>> tokenList = tokens.build();
    GrossStructure root = grossStructurer.apply(tokenList);
    root.appendTokens(sink, softColumnLimit);
    String code = sink.getCode();

    ImmutableList.Builder<PositionMapping.Entry> entries =
        ImmutableList.builder();
    List<Integer> tokenIndices = sink.getTokenIndices();
    Preconditions.checkState(tokenIndices.size() == tokenList.size());
    for (int i = 0, n = tokenIndices.size(); i < n; ++i) {
      int tokIdx = tokenIndices.get(i);
      DecoratedToken<C> tok = tokenList.get(i);
      Preconditions.checkState(
          code.regionMatches(tokIdx, tok.content, 0, tok.content.length()),
          tok.content);

      entries.add(new PositionMapping.Entry(
          tok.pos, tokIdx, tokIdx + tok.content.length()));
    }
    PositionMapping positionMapping = new PositionMapping(entries.build());

    return new FormattedSource(code, positionMapping);
  }

  /**
   * A non-whitespace lexical token with position and context information.
   */
  public static final class DecoratedToken<C> {
    /** The text of a non-whitespace token. */
    public final String content;
    /** Source position used to generate the position mapping. */
    public final SourcePosition pos;
    /** Context hint that may be used by a token breaker. */
    public final @Nullable C context;

    DecoratedToken(String content, SourcePosition pos, @Nullable C context) {
      this.content = content;
      this.pos = pos;
      this.context = context;
    }
  }
}
