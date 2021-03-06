package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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

final class Alternation extends PTParSer {
  final ImmutableList<ParSerable> ps;

  /** Matches no strings. */
  static final Alternation NULL_LANGUAGE = new Alternation(ImmutableList.of());

  private Alternation(ImmutableList<ParSerable> ps) {
    this.ps = ps;
  }

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    return alternate(ps, state, lr, err);
  }

  static ParseResult alternate(
      Iterable<? extends ParSerable> ps, ParseState state,
      LeftRecursion lr, ParseErrorReceiver err) {

    ParseResult failure = ParseResult.failure();

    for (ParSerable p : ps) {
      ParseResult result = p.getParSer().parse(state, lr, err);
      switch (result.synopsis) {
        case FAILURE:
          failure = result;
          break;
        case SUCCESS:
          return result;
      }
    }
    return failure;
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
  public ForceFitState forceFit(ForceFitState state) {
    if (state.fits.isEmpty()) { return state; }
    ImmutableSet.Builder<ForceFitState.PartialFit> b =
        ImmutableSet.builder();
    for (ParSerable p : ps) {
      ParSer fitter = p.getParSer();
      ForceFitState after = fitter.forceFit(state);
      b.addAll(after.fits);
    }
    return state.withFits(b.build());
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
  public void appendShallowStructure(StringBuilder sb) {
    if (ps.isEmpty()) {
      sb.append("[]");
      return;
    }

    Optional<ParSerable> notEmpty = getOptionBody(this);
    if (notEmpty.isPresent()) {
      sb.append("[").append(notEmpty.get()).append("]");
      return;
    }

    boolean first = true;
    for (ParSerable p : ps) {
      if (first) {
        first = false;
      } else {
        sb.append(" | ");
      }
      ParSer c = p.getParSer();
      if (c instanceof Alternation) {
        sb.append('(');
        c.appendShallowStructure(sb);
        sb.append(')');
      } else {
        c.appendShallowStructure(sb);
      }
    }
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
}
