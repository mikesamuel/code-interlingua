package com.mikesamuel.cil.ptree;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.ast.MatchEvent;
import com.mikesamuel.cil.parser.MatchErrorReceiver;
import com.mikesamuel.cil.parser.MatchState;
import com.mikesamuel.cil.parser.ParSer;
import com.mikesamuel.cil.parser.ParSerable;
import com.mikesamuel.cil.parser.ParseErrorReceiver;
import com.mikesamuel.cil.parser.ParseState;
import com.mikesamuel.cil.parser.SerialErrorReceiver;
import com.mikesamuel.cil.parser.SerialState;

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
  RangeSet<Integer> computeLookahead1() {
    return null;
  }

  static final MatchEvent LOOKAHEAD_START = new MatchEvent() {

    @Override
    public int nCharsConsumed() {
      return 0;
    }

    @Override
    public final String toString() {
      return "lookahead";
    }
  };

  @Override
  public Optional<ParseState> parse(ParseState state, ParseErrorReceiver err) {
    Optional<ParseState> afterBody = body.getParSer().parse(
        state.appendOutput(LOOKAHEAD_START), err);
    if (afterBody.isPresent() == (valence == Valence.POSITIVE)) {
      // Don't leave the lookahead start or anything else.
      return Optional.of(state);
    } else {
      return Optional.absent();
    }
  }

  @Override
  public Optional<SerialState> unparse(
      SerialState state, SerialErrorReceiver err) {
    return Optional.of(state);
  }

  @Override
  public Optional<MatchState> match(MatchState state, MatchErrorReceiver err) {
    return Optional.of(state);
  }

  @Override
  public String toString() {
    return (valence == Valence.POSITIVE ? "=(" : "!(") + body + ")";
  }
}
