package com.mikesamuel.cil.ptree;

import java.util.List;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
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

class Concatenation extends PTParSer {
  final ImmutableList<ParSerable> ps;

  static final Concatenation EMPTY = new Concatenation(ImmutableList.of());

  Concatenation(ImmutableList<ParSerable> ps) {
    this.ps = ps;
  }

  static ParSerable of(Iterable<? extends ParSerable> els) {
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

  private static ImmutableList<ParSerable> flatten(
      Iterable<? extends ParSerable> els) {
    List<ParSerable> flat = Lists.newArrayList();
    flattenOnto(flat, els);
    for (ParSerable el : flat) {
      if (el == Alternation.NULL_LANGUAGE) {
        return ImmutableList.of(el);  // Fail-fast
      }
    }
    return ImmutableList.copyOf(flat);
  }

  private static void flattenOnto(
      List<ParSerable> flat, Iterable<? extends ParSerable> els) {
    for (ParSerable p : els) {
      if (p instanceof Concatenation) {
        flattenOnto(flat, ((Concatenation) p).ps);
      } else if (isIdentifierWrapper(p)) {
        // Group ("." Identifier) into a MagicDotIdentifier clause to allow
        // just enough back-tracking to deal with left-recursive name
        // productions.
        int maybeDotIndex = flat.size() - 1;
        while (maybeDotIndex >= 0) {
          ParSerable maybeEmpty = flat.get(maybeDotIndex);
          if (maybeEmpty instanceof Repetition
              || (maybeEmpty instanceof Alternation
                  && (Alternation.getOptionBody((Alternation) maybeEmpty)
                      .isPresent()))) {
            --maybeDotIndex;
          } else {
            break;
          }
        }
        if (maybeDotIndex >= 0) {
          ParSerable maybeDot = flat.get(maybeDotIndex);
          if (maybeDot instanceof Literal
              && ".".equals(((Literal) maybeDot).text)) {
            List<ParSerable> magicBits = flat.subList(
                maybeDotIndex, flat.size());
            ParSerable magic =
                MagicDotIdentifierHandler.of(
                    ImmutableList.<ParSerable>builder()
                    .addAll(magicBits)
                    .add(p)
                    .build());
            magicBits.clear();
            flat.add(magic);
            continue;
          }
        }
        flat.add(p);
      } else {
        flat.add(p);
      }
    }
  }

  @Override
  public ParseResult parse(
      ParseState start, LeftRecursion lr, ParseErrorReceiver err) {
    ParseState state = start;
    ImmutableSet.Builder<NodeType<?, ?>> lrExclusionsTriggered =
        ImmutableSet.builder();
    int writeBack = ParseResult.NO_WRITE_BACK_RESTRICTION;
    for (ParSerable p : ps) {
      ParSer parser = p.getParSer();
      ParseResult result = parser.parse(state, lr, err);
      lrExclusionsTriggered.addAll(result.lrExclusionsTriggered);
      switch (result.synopsis) {
        case FAILURE:
          return ParseResult.failure(lrExclusionsTriggered.build());
        case SUCCESS:
          state = result.next();
          writeBack = Math.min(writeBack, result.writeBack);
          continue;
      }
      throw new AssertionError(result.synopsis);
    }
    return ParseResult.success(
        state, writeBack, lrExclusionsTriggered.build());
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState start, SerialErrorReceiver err) {
    SerialState state = start;
    for (ParSerable p : ps) {
      ParSer serializer = p.getParSer();
      Optional<SerialState> next = serializer.unparse(state, err);
      if (!next.isPresent()) { return Optional.absent(); }
      state = next.get();
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
  public ForceFitState forceFit(ForceFitState start) {
    ForceFitState state = start;
    for (ParSerable p : ps) {
      if (state.fits.isEmpty()) { break; }
      ParSer fitter = p.getParSer();
      state = fitter.forceFit(state);
    }
    return state;
  }

  private static boolean isIdentifierWrapper(ParSerable ps) {
    if (ps instanceof NodeType) {
      return ((NodeType<?, ?>) ps).isIdentifierWrapper();
    } else {
      ParSer p = ps.getParSer();
      return p instanceof Reference &&
          ((Reference) p).getNodeType().isIdentifierWrapper();
    }
  }

  @Override
  Kind getKind() {
    return Kind.CAT;
  }

  @Override
  public void appendShallowStructure(StringBuilder sb) {
    if (ps.isEmpty()) {
      sb.append("()");
      return;
    }

    boolean first = true;
    for (ParSerable p : ps) {
      if (first) {
        first = false;
      } else {
        sb.append(' ');
      }
      ParSer c = p.getParSer();
      boolean parenthesize = c instanceof Concatenation;
      if (c instanceof Alternation) {
        Alternation alt = (Alternation) c;
        parenthesize = alt != Alternation.NULL_LANGUAGE
            && !Alternation.getOptionBody(alt).isPresent();
      }
      if (parenthesize) {
        sb.append('(');
        c.appendShallowStructure(sb);
        sb.append(')');
      } else {
        sb.append(c);
      }
    }
  }
}
