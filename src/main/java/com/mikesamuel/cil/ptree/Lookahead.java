package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.event.DelayedCheckPredicate;
import com.mikesamuel.cil.event.Event;
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
import com.mikesamuel.cil.parser.Unparse;

final class Lookahead extends PTParSer {

  enum Valence {
    NEGATIVE,
    POSITIVE,
    ;

    Valence invert() {
      return this == NEGATIVE ? POSITIVE : NEGATIVE;
    }
  }

  static ParSer of(Valence v, ParSerable ps) {
    Valence valence = Preconditions.checkNotNull(v);
    ParSerable body = Preconditions.checkNotNull(ps);
    if (body instanceof Lookahead) {
      Lookahead la = (Lookahead) body;
      body = la.body;
      valence = la.valence == Valence.NEGATIVE ? valence.invert() : valence;
    }

    if (body == Concatenation.EMPTY
        || (body instanceof Alternation
            && Alternation.getOptionBody((Alternation) body).isPresent())) {
      switch (v) {
        case NEGATIVE:
          return Alternation.NULL_LANGUAGE;
        case POSITIVE:
          return Concatenation.EMPTY;
      }
    } else if (body == Alternation.NULL_LANGUAGE) {
      switch (v) {
        case NEGATIVE:
          return Concatenation.EMPTY;
        case POSITIVE:
          return Alternation.NULL_LANGUAGE;
      }
    }

    return new Lookahead(valence, body);
  }

  final Valence valence;
  final ParSerable body;

  private Lookahead(Valence valence, ParSerable body) {
    this.valence = valence;
    this.body = body;
  }

  @Override
  Kind getKind() {
    return Kind.LA;
  }

  @Override
  public ParseResult parse(
      ParseState state, LeftRecursion lr, ParseErrorReceiver err) {
    ParseErrorReceiver laerr = valence == Valence.POSITIVE
        ? err : ParseErrorReceiver.DEV_NULL;
    ParseResult result = body.getParSer().parse(state, lr, laerr);
    switch (result.synopsis) {
      case FAILURE:
        switch (valence) {
          case NEGATIVE:
            return ParseResult.success(
                state, ParseResult.NO_WRITE_BACK_RESTRICTION,
                result.lrExclusionsTriggered);
          case POSITIVE:
            return result;
        }
        break;
      case SUCCESS:
        switch (valence) {
          case POSITIVE:
            // Don't advance the index or preserve outputs.
            return ParseResult.success(
                state, ParseResult.NO_WRITE_BACK_RESTRICTION,
                result.lrExclusionsTriggered);
          case NEGATIVE:
            err.error(state, "Successfully matched to " + result.next().index);
            return ParseResult.failure(result.lrExclusionsTriggered);
        }
    }
    throw new AssertionError(result.synopsis + ", " + valence);
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState serialState, SerialErrorReceiver err) {
    DoubleCheckPredicate p = new DoubleCheckPredicate();
    return Optional.of(serialState.append(Event.delayedCheck(p)));
  }

  @Override
  public Optional<MatchState> match(MatchState state, MatchErrorReceiver err) {
    return Optional.of(state);
  }

  @Override
  public ForceFitState forceFit(ForceFitState state) {
    return state;
  }

  @Override
  public void appendShallowStructure(StringBuilder sb) {
    sb.append((valence == Valence.POSITIVE ? "=(" : "!("));
    body.getParSer().appendShallowStructure(sb);
    sb.append(")");
  }


  private final class DoubleCheckPredicate implements DelayedCheckPredicate {

    DoubleCheckPredicate() {
      // Uses implicit outer this.
    }

    @Override
    public Optional<String> problem(Unparse.Suffix suffix) {
      class DCErrorReceiver implements ParseErrorReceiver {
        int maxIndex = -1;
        String farthestMessage = "reparsing failed";

        @Override
        public void error(ParseState state, String message) {
          if (state.index > this.maxIndex) {
            this.maxIndex = state.index;
            this.farthestMessage = message;
          }
        }
      }
      DCErrorReceiver dcer = new DCErrorReceiver();
      ParseState ps = suffix.asParseState();
      ParseResult result = getLookahead().parse(
          ps, new LeftRecursion(), dcer);
      switch (result.synopsis) {
        case FAILURE:
          StringBuilder sb = new StringBuilder();
          sb.append(dcer.farthestMessage);
          if (dcer.maxIndex >= 0) {
            sb.append(" near `");
            int limit = ps.input.content().length();
            int start = dcer.maxIndex;
            int end = Math.min(start + 10, limit);
            sb.append(ps.input.content(), start, end);
            if (end < limit) {
              sb.append("...");
            }
            sb.append('`');
          }
          return Optional.of(sb.toString());
        case SUCCESS:
          return Optional.absent();
      }
      throw new AssertionError(result.synopsis);
    }

    private Lookahead getLookahead() {
      return Lookahead.this;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || o.getClass() != this.getClass()) { return false; }
      DoubleCheckPredicate that = (DoubleCheckPredicate) o;
      return this.getLookahead().equals(that.getLookahead());
    }

    @Override
    public int hashCode() {
      return Lookahead.this.hashCode();
    }

    @Override
    public String toString() {
      return Lookahead.this.toString();
    }
  }
}
