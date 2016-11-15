package com.mikesamuel.cil.parser;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.Trees;

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
   * Given a parse state, computes the parse state after successfully consuming
   * a prefix of the input in this parser's language.
   *
   * <p>
   * {@link Trees#of} can be used to build a tree from the
   * {@linkplain ParseState#output}.
   *
   * @param err receives parse errors.  Errors on one branch may be irrelevant
   *     if a later branch passes.  If the parse as a whole fails, the parse
   *     error that occurs at the rightmost point is a good one to pass on to
   *     the end user.
   * @param lr state that lets productions handle left-recursive invocations.
   * @return failure when there is no string in this parser's language that is
   *     a prefix of state's content.
   */
  public abstract ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err);

  /**
   * Given a stream of events that describe a flattened tree, fleshes out the
   * stream of events by inserting {@link MatchEvent#token} events and events
   * for {@linkplain NodeVariant#isAnon anon} variants.
   */
  public abstract Optional<SerialState> unparse(
      SerialState serialState, SerialErrorReceiver err);

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
