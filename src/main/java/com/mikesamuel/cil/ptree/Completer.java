package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.mikesamuel.cil.parser.LeftRecursion;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseResult;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Completer extends ParSer {
  final ParSerable p;

  Completer(ParSerable p) {
    this.p = p;
  }

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    ParseResult result = p.getParSer().parse(state, lr, err);
    switch (result.synopsis) {
      case FAILURE:
        return result;
      case SUCCESS:
        ParseState nextState = result.next();
        if (nextState.isEmpty()) {
          return result;
        }
        CharSequence unparsed =
            nextState.input.content.subSequence(nextState.index);
        if (unparsed.length() > 10) {
          unparsed = unparsed.subSequence(0, 10) + "...";
        }
        err.error(nextState, "Unparsed input `" + unparsed + "`");
        return ParseResult.failure();
    }
    throw new AssertionError(result.synopsis);
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver r) {
    Optional<SerialState> next = p.getParSer().unparse(state, r);
    if (next.isPresent()) {
      if (next.get().isEmpty()) {
        return next;
      }
      r.error(state, "Unmatched input");
    }
    return Optional.absent();
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver r) {
    Optional<MatchState> next = p.getParSer().match(state, r);
    if (next.isPresent()) {
      if (next.get().isEmpty()) {
        return next;
      }
      r.error(state, "Unmatched input");
    }
    return Optional.absent();
  }

  @Override
  public String toString() {
    return "(complete " + this.p + ")";
  }
}