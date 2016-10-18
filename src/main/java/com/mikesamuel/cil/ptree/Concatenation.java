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

final class Concatenation extends PTParSer {
  final ImmutableList<ParSerable> ps;

  static final Concatenation EMPTY = new Concatenation(ImmutableList.of());

  private Concatenation(ImmutableList<ParSerable> ps) {
    this.ps = ps;
  }

  static final ParSerable of(Iterable<? extends ParSerable> els) {
    ImmutableList<ParSerable> elList = flatten(els);
    switch (elList.size()) {
      case 0:
        return EMPTY;
      case 1:
        return elList.get(0);
      default:
        return new Concatenation(elList);
    }
  }

  private static final ImmutableList<ParSerable> flatten(
      Iterable<? extends ParSerable> els) {
    ImmutableList.Builder<ParSerable> b = ImmutableList.builder();
    for (ParSerable p : els) {
      if (p instanceof Concatenation) {
        b.addAll(((Concatenation) p).ps);
      } else {
        if (p instanceof Alternation) {
          if (((Alternation) p).ps.isEmpty()) {
            return ImmutableList.of(p);  // Fail-fast
          }
        }
        b.add(p);
      }
    }
    return b.build();
  }

  @Override
  public Optional<ParseState> parse(
      ParseState start, ParseErrorReceiver err) {
    ParseState state = start;
    for (ParSerable p : ps) {
      ParSer parser = p.getParSer();
      Optional<ParseState> next = parser.parse(state, err);
      if (!next.isPresent()) { return Optional.absent(); }
      state = next.get();
    }
    return Optional.of(state);
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState start, SerialErrorReceiver err) {
    SerialState state = start;
    for (ParSerable p : ps) {
      ParSer serializer = p.getParSer();
      Optional<SerialState> next = serializer.unparse(state, err);
      if (!next.isPresent()) { return Optional.absent(); }
    }
    return Optional.of(state);
  }

  @Override
  public Optional<MatchState> match(
      MatchState start, MatchErrorReceiver r) {
    MatchState state = start;
    for (ParSerable p : ps) {
      ParSer matcher = p.getParSer();
      Optional<MatchState> next = matcher.match(state, r);
      if (!next.isPresent()) { return Optional.absent(); }
      state = next.get();
    }
    return Optional.of(state);
  }

  @Override
  Kind getKind() {
    return Kind.CAT;
  }

  @Override
  public String toString() {
    if (ps.isEmpty()) { return "()"; }

    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for (ParSerable p : ps) {
      if (first) {
        first = false;
      } else {
        sb.append(' ');
      }
      ParSer c = p.getParSer();
      if (c instanceof Alternation || c instanceof Concatenation) {
        sb.append('(').append(c).append(')');
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }
}