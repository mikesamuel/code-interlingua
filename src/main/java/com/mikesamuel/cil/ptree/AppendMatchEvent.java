package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class AppendMatchEvent extends ParSer {
  final MatchEvent e;

  AppendMatchEvent(MatchEvent e) {
    this.e = e;
  }

  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    return Optional.of(state.appendOutput(e));
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    // Should only occur during parse LR handling.
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<MatchState> match(MatchState state, MatchErrorReceiver err) {
    // Should only occur during parse LR handling.
    throw new UnsupportedOperationException();
  }

  @Override
  public String toString() {
    return "(append " + e + ")";
  }
}
