package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
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
    int idx = state.indexAfterIgnorables();
    int firstChar = idx < state.input.content.length()
        ? state.input.content.charAt(idx) : -1;

    for (ParSerable p : ps) {
      ParSer parser = p.getParSer();
      if (firstChar >= 0 && parser instanceof PTParSer) {
        RangeSet<Integer> la1 = ((PTParSer) parser).getLookahead1();
        if (la1 != null && !la1.contains(firstChar)) {
          continue;
        }
      }

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

    Optional<ParSerable> notEmpty = getOptionBody(this);
    if (notEmpty.isPresent()) {
      return "[" + notEmpty.get() + "]";
    }

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

  /**
   * The body of the option if this is an option -- if the last element is the
   * empty string.
   */
  static Optional<ParSerable> getOptionBody(Alternation alt) {
    int n = alt.ps.size();
    if (n != 0 && Concatenation.EMPTY == alt.ps.get(n - 1)) {
      return Optional.of(Alternation.of(alt.ps.subList(0, n - 1)));
    }
    return Optional.absent();
  }

  @Override
  RangeSet<Integer> computeLookahead1() {
    RangeSet<Integer> la = TreeRangeSet.create();
    for (ParSerable c : ps) {
      ParSer p = c.getParSer();
      if (!(p instanceof PTParSer)) {
        return null;
      }
      RangeSet<Integer> cla = ((PTParSer) p).getLookahead1();
      if (cla == null) {
        return null;
      }
      la.addAll(cla);
    }
    return ImmutableRangeSet.copyOf(la);
  }
}
