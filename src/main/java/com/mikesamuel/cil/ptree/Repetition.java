package com.mikesamuel.cil.ptree;

import java.util.LinkedHashSet;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.parser.ForceFitState;
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
  public ParseResult parse(
      ParseState start, LeftRecursion lr, ParseErrorReceiver err) {
    return parseRepeatedly(this.p, start, lr, err);
  }

  static ParseResult parseRepeatedly(
      ParSerable p,
      ParseState start, LeftRecursion lr, ParseErrorReceiver err) {
    ParseState state = start;
    ParSer parser = p.getParSer();
    ImmutableSet<NodeType<?, ?>> lrExclusionsTriggered = ImmutableSet.of();
    int writeBack = ParseResult.NO_WRITE_BACK_RESTRICTION;
    while (true) {
      ParseResult result = parser.parse(state, lr, err);
      lrExclusionsTriggered = ParseResult.union(
          lrExclusionsTriggered, result.lrExclusionsTriggered);

      switch (result.synopsis) {
        case FAILURE:
            return ParseResult.success(state, writeBack, lrExclusionsTriggered);

        case SUCCESS:
          ParseState nextState = result.next();
          // Guarantee termination
          if (nextState.index == state.index) {
            return ParseResult.success(state, writeBack, lrExclusionsTriggered);
          }
          Preconditions.checkState(nextState.index > state.index);
          state = nextState;
          writeBack = Math.min(writeBack, result.writeBack);
      }
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState start, SerialErrorReceiver err) {
    SerialState state = start;
    ParSer serializer = p.getParSer();
    while (true) {
      Optional<SerialState> next = serializer.unparse(state, err);
      if (next.isPresent()) {
        SerialState nextState = next.get();
        // Guarantee termination
        if (nextState.index == state.index) {
          break;
        }
        Preconditions.checkState(nextState.index > state.index);
        state = nextState;
      } else {
        break;
      }
    }
    return Optional.of(state);
  }

  @Override
  public Optional<MatchState> match(
      MatchState start, MatchErrorReceiver err) {
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
      } else {
        return Optional.of(state);
      }
    }
  }

  @Override
  public ForceFitState forceFit(ForceFitState start) {
    ForceFitState state = start;
    LinkedHashSet<ForceFitState.PartialFit> fits =
        new LinkedHashSet<>(start.fits);
    while (true) {
      int nFits = fits.size();
      ForceFitState after = p.getParSer().forceFit(state);
      fits.addAll(after.fits);
      if (fits.size() == nFits) { break; }
      state = after;
    }
    return start.withFits(fits);
  }

  @Override
  Kind getKind() {
    return Kind.REP;
  }

  @Override
  public void appendShallowStructure(StringBuilder sb) {
    sb.append("{");
    this.p.getParSer().appendShallowStructure(sb);
    sb.append("}");
  }
}