package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

final class Repetition extends PTParSer {
  final ParSerable p;

  private Repetition(ParSerable p) {
    this.p = p;
  }

  static ParSerable of(ParSerable ps) {
    if (ps == Concatenation.EMPTY
        // Kleene* passes with no effect when there are zero passes of the body.
        || ps == Alternation.NULL_LANGUAGE
        || ps instanceof Lookahead) {
      return Concatenation.EMPTY;
    } else if (ps instanceof Repetition) {
      return ps;
    }

    if (ps instanceof Alternation) {
      Alternation alt = (Alternation) ps;
      int n = alt.ps.size();
      if (n != 0 && alt.ps.get(n - 1) == Concatenation.EMPTY) {
        // Factor over option.
        return of(Alternation.of(alt.ps.subList(0, n - 1)));
      }
    }
    return new Repetition(ps);
  }

  @Override
  public Optional<ParseState> parse(
      ParseState start, ParseErrorReceiver err) {
    return Optional.of(parseRepeatedly(this.p, start, err));
  }

  static ParseState parseRepeatedly(
      ParSerable p, ParseState start, ParseErrorReceiver err) {
    boolean matchedOne = false;
    ParseState state = start;
    ParSer parser = p.getParSer();
    while (true) {
      Optional<ParseState> next = parser.parse(state, err);
      if (next.isPresent()) {
        ParseState nextState = next.get();
        // Guarantee termination
        if (nextState.index == state.index) {
          return state;
        }
        Preconditions.checkState(nextState.index > state.index);
        state = nextState;
        matchedOne = true;
      } else if (matchedOne) {
        return state;
      } else {
        // This is Kleene-star, so zero matches are ok.
        return start;
      }
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState start, SerialErrorReceiver err) {
    boolean matchedOne = false;
    SerialState state = start;
    ParSer serializer = p.getParSer();
    while (true) {
      Optional<SerialState> next = serializer.unparse(state, err);
      if (next.isPresent()) {
        SerialState nextState = next.get();
        // Guarantee termination
        if (nextState.index == state.index) {
          return Optional.of(state);
        }
        Preconditions.checkState(nextState.index > state.index);
        state = nextState;
      } else if (matchedOne) {
        return Optional.of(state);
      } else {
        return Optional.absent();
      }
    }
  }

  @Override
  public Optional<MatchState> match(
      MatchState start, MatchErrorReceiver err) {
    boolean matchedOne = false;
    MatchState state = start;
    ParSer matcher = p.getParSer();
    while (true) {
      Optional<MatchState> next = matcher.match(state, err);
      if (next.isPresent()) {
        MatchState nextState = next.get();
        // Guarantee termination
        if (nextState.index == state.index) {
          return Optional.of(state);
        }
        Preconditions.checkState(nextState.index > state.index);
        state = nextState;
      } else if (matchedOne) {
        return Optional.of(state);
      } else {
        return Optional.absent();
      }
    }
  }

  @Override
  Kind getKind() {
    return Kind.REP;
  }

  @Override
  RangeSet<Integer> computeLookahead1() {
    return null;  // Worst case for when no content is matched.
  }

  @Override
  public String toString() {
    return "{" + this.p + "}";
  }
}