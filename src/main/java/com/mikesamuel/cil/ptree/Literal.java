package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Literal extends PTParSer {
  final String text;
  final int ln, co, ix;
  final MatchEvent.TokenMatchEvent event;

  private Literal(String text, int ln, int co, int ix) {
    this.text = text;
    this.ln = ln;
    this.co = co;
    this.ix = ix;
    event = MatchEvent.token(text);
  }

  static PTParSer of(String text, int ln, int co, int ix) {
    if (text.isEmpty()) { return Concatenation.EMPTY; }
    return new Literal(text, ln, co, ix);
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    if (state.startsWith(text)) {
      return Optional.of(state.advance(text.length()).appendOutput(event));
    }
    err.error(state, "Expected `" + text + "`");
    return Optional.absent();
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return Optional.of(state.append(text));
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver err) {
    return state.expectEvent(MatchEvent.content(this.text), err);
  }

  @Override
  Kind getKind() {
    return Kind.LIT;
  }

  @Override
  public String toString() {
    return "\"" + this.text + "\"";  // TODO escape
  }
}