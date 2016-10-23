package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Completer extends ParSer {
  final ParSerable p;

  Completer(ParSerable p) {
    this.p = p;
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    Optional<ParseState> next = p.getParSer().parse(state, err);
    if (next.isPresent()) {
      // Test empty except for ignorable tokens.
      ParseState nextState = next.get().advance(0, true);
      if (nextState.isEmpty()) {
        return Optional.of(nextState);
      }
      err.error(state, "Unparsed input");
    }
    return Optional.absent();
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return p.getParSer().unparse(state, err);
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