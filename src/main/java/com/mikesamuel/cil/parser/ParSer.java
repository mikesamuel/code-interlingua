package com.mikesamuel.cil.parser;

import com.google.common.base.Optional;

/** Provides parsing and serializing for AST nodes. */
public abstract class ParSer implements ParSerable {

  /**
   * Simply supplies itself.
   * Being able to use ParSers as ParSerables allows us to avoid class
   * initialization loops where enum values are ParSers that need to delegate
   * to one another.
   */
  @Override
  public final ParSer getParSer() {
    return this;
  }

  /**
   * Given a parse state, computes the parse state after successfully matching
   * tokens related to the .
   *
   * @param err receives parse errors.  Errors on one branch may be irrelevant
   *     if a later branch passes.  If the parse as a whole fails, the parse
   *     error that occurs at the rightmost point is a good one to pass on to
   *     the end user.
   * @return absent when there is no string in this parser's language that is
   *     a prefix of state's content.
   */
  public abstract Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err);

  /**
   * Given a serializer state, consumes events and emits output necessary to
   * serialize a flattened parse tree.
   *
   * @return absent if the events at the front of state cannot be serialized
   *     by this serializer.
   */
  public abstract Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err);

  /**
   * Given a match state, consumes events to determine whether a flattened can
   * be parsed/serialized by this ParSer.
   *
   * @return absent if the events at the front of state cannot be handled
   *     by this ParSer.
   */
  public abstract Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err);
}
