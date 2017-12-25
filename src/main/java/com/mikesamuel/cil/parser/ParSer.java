package com.mikesamuel.cil.parser;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.Trees;
import com.mikesamuel.cil.event.Event;

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

  /** True if the ParSer grammar matches the given input. */
  public boolean fastMatch(String input) {
    Input inp = Input.builder().code(input).source("fastMatch").build();
    ParseState before = new ParseState(inp);
    ParseResult result = parse(
        before, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    if (result.synopsis == ParseResult.Synopsis.SUCCESS) {
      ParseState after = result.next();
      if (after.input.indexAfterIgnorables(after.index)
          == inp.content().length()) {
        return true;
      }
    }
    return false;
  }

  /**
   * True if the given input has no whitespace or commnet tokens at the start
   * or end and the ParSer grammar matches the given input.
   */
  public boolean fastMatch(String input) {
    // TODO: To be consistent with PatternMatch we need to disable
    // \\u decoding.
    Input inp = Input.builder()
        .setFragmentOfAlreadyDecodedInput(true)
        .code(input)
        .source("fastMatch")
        .build();
    if (inp.indexAfterIgnorables(0) != 0) {
      // We explicitly disallow comments and spaces at the front so that we
      // can
      return false;
    }
    ParseState before = new ParseState(inp);
    ParseResult result = parse(
        before, new LeftRecursion(), ParseErrorReceiver.DEV_NULL);
    if (result.synopsis == ParseResult.Synopsis.SUCCESS) {
      ParseState after = result.next();
      if (after.index == inp.content().length()) {
        // We intentionally do not use index after ignorables.
        return true;
      }
    }
    return false;
  }

  /**
   * Given a stream of events that describe a flattened tree, fleshes out the
   * stream of events by inserting {@link Event#token} events and events
   * for {@linkplain NodeVariant#isAnon anon} variants.
   */
  public abstract Optional<SerialState> unparse(
      SerialState serialState, SerialErrorReceiver err);

  /**
   * Given a match state, consumes events to determine whether a flattened
   * parse tree can be parsed/serialized by this ParSer.
   *
   * @return absent if the events at the front of state cannot be handled
   *     by this ParSer.
   */
  public abstract Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err);

  /**
   * Takes a set of ASTs, some of which are complete, and some of which are
   * partial, and figures out how they could be coerced so that they could
   * be unparsed to a string that matches the language matched by this ParSer.
   */
  public abstract ForceFitState forceFit(ForceFitState state);

  /**
   * A string representing the shallow structure of the grammar backing this
   * ParSer.  A shallow structure does not look through non-terminals.
   */
  public abstract void appendShallowStructure(StringBuilder sb);

  @Override
  public final String toString() {
    StringBuilder sb = new StringBuilder();
    appendShallowStructure(sb);
    return sb.toString();
  }
}
