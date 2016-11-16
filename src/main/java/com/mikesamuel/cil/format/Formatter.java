package com.mikesamuel.cil.format;

import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.SourcePosition;

/**
 * Converts a token stream to formatted source code.
 */
public class Formatter<C> {
  /** Used to figure out how to put white-space between tokens. */
  public final TokenBreaker<? super C> tokenBreaker;
  /** Used to figure out how to break tokens into lines and indent the lines. */
  public final Function<? super ImmutableList<String>,
                        ? extends GrossStructure> grossStructurer;
  private SourcePosition sourcePosition;
  private C context;

  /** */
  public Formatter(
      TokenBreaker<? super C> tokenBreaker,
      Function<? super ImmutableList<String>,
               ? extends GrossStructure> grossStructurer) {
    this.tokenBreaker = tokenBreaker;
    this.grossStructurer = grossStructurer;
  }

  /**
   * Specifies a token that occurs after all previously specified tokens in the
   * token stream.
   */
  public void token(String tok) {
    // TODO
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
   * @param columnWidth soft column limit for formatted code.
   *     The column limit might be violated if, e.g. a single token exceeds the
   *     column width, or excessive required indentation would lead to one token
   *     per line.
   */
  public void formatTo(TokenSink sink, int columnWidth) {
    // TODO
  }
}
