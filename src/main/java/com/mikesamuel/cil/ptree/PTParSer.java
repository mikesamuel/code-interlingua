package com.mikesamuel.cil.ptree;

import javax.annotation.Nullable;

import com.google.common.collect.RangeSet;
import com.mikesamuel.cil.parser.ParSer;

abstract class PTParSer extends ParSer {
  /** A single UTF-16 code-unit of lookahead. */
  private RangeSet<Integer> lookahead1 = null;
  private boolean computedLookahead1;

  enum Kind {
    ALT,
    CAT,
    REP,
    LIT,
    REX,
    REF,
  }

  abstract Kind getKind();

  final boolean couldBeInLookahead(char ch) {
    RangeSet<Integer> la1 = getLookahead1();
    return la1 == null || la1.contains((int) ch);
  }

  final @Nullable RangeSet<Integer> getLookahead1() {
    if (true) { return null; }  // TODO: Our lookahead stuff is very borken
    if (!computedLookahead1) {
      computedLookahead1 = true;
      lookahead1 = computeLookahead1();
    }
    return lookahead1;
  }

  /**
   * Best effort to compute one code-unit of lookahead.
   *
   * @return null if cannot determine.
   */
  abstract RangeSet<Integer> computeLookahead1();
}
