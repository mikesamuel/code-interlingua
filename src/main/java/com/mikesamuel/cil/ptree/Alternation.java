package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Alternation extends PTParSer {
  final ImmutableList<ParSerable> ps;

  /** Matches no strings. */
  static final Alternation NULL_LANGUAGE = new Alternation(ImmutableList.of());

  private Alternation(ImmutableList<ParSerable> ps) {
    this.ps = ps;
  }

  @Override
  public Optional<ParseState> parse(
      ParseState state, ParseErrorReceiver err) {
    for (ParSerable p : ps) {
      ParSer parser = p.getParSer();
      Optional<ParseState> next = parser.parse(state, err);
      if (next.isPresent()) { return next; }
    }
    return Optional.absent();
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    for (ParSerable p : ps) {
      ParSer serializer = p.getParSer();
      Optional<SerialState> next = serializer.unparse(state, err);
      if (next.isPresent()) { return next; }
    }
    return Optional.absent();
  }

  @Override
  public Optional<MatchState> match(
      MatchState state, MatchErrorReceiver r) {
    for (ParSerable p : ps) {
      ParSer matcher = p.getParSer();
      Optional<MatchState> next = matcher.match(state, r);
      if (next.isPresent()) { return next; }
    }
    return Optional.absent();
  }

  @Override
  Kind getKind() {
    return Kind.ALT;
  }

  static ParSerable of(Iterable<? extends ParSerable> els) {
    ImmutableList<ParSerable> elList = flatten(els);
    switch (elList.size()) {
      case 0: return NULL_LANGUAGE;
      case 1: return elList.get(0);
      default:
        return new Alternation(elList);
    }
  }

  private static ImmutableList<ParSerable> flatten(
      Iterable<? extends ParSerable> els) {
    ImmutableList.Builder<ParSerable> b = ImmutableList.builder();
    for (ParSerable p : els) {
      if (p instanceof Alternation) {
        b.addAll(((Alternation) p).ps);
      } else {
        b.add(p);
      }
    }
    return b.build();
  }

  @Override
  public String toString() {
    if (ps.isEmpty()) { return "[]"; }

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (ParSerable p : ps) {
      if (first) {
        first = false;
      } else {
        sb.append(" | ");
      }
      ParSer c = p.getParSer();
      if (c instanceof Alternation) {
        sb.append('(').append(c).append(')');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}
