package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class AppendSuffixMarkers extends ParSer {
  final ImmutableList<NodeVariant> lrCallChain;

  AppendSuffixMarkers(ImmutableList<NodeVariant> lrCallChain) {
    this.lrCallChain = lrCallChain;
  }

  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    return Optional.of(
        state.appendOutput(MatchEvent.leftRecursionSuffix(lrCallChain)));
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
    StringBuilder sb = new StringBuilder();
    sb.append("(suffix:");
    for (NodeVariant v : lrCallChain) {
      sb.append(' ').append(v.getNodeType()).append('.').append(v);
    }
    return sb.append(')').toString();
  }
}
