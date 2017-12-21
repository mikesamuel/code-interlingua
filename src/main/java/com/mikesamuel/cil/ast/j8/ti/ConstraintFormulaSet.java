package com.mikesamuel.cil.ast.j8.ti;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.logging.Logger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;

final class ConstraintFormulaSet {
  final ImmutableSet<ConstraintFormula> formulae;

  ConstraintFormulaSet(Iterable<? extends ConstraintFormula> formulae) {
    this.formulae = ImmutableSet.copyOf(formulae);
  }

  /**
   * Reduction is the process by which a set of constraint formulas is
   * simplified to produce a bound set.
   *
   * @see <a href="https://docs.oracle.com/javase/specs/jls/se8/html/jls-18.html#jls-18.2">18.2</a>
   */
  BoundSet reduce(
      TypePool thetaTypePool, Logger logger, UncheckedConversionCallback ucc) {
    // Each constraint formula is considered in turn.
    // The rules in this section specify how the formula is reduced to
    // one or both of:
    //   * A bound or bound set, which is to be incorporated with the "current"
    //     bound set.  Initially, the current bound set is empty.
    //   * Further constraint formulas, which are to be reduced recursively.

    // Reduction completes when no further constraint formulas remain to be
    // reduced.
    ImmutableList.Builder<ConstraintFormula> reduced = ImmutableList.builder();
    Deque<ConstraintFormula> unreduced = new ArrayDeque<>(
        formulae);
    while (!unreduced.isEmpty()) {
      ConstraintFormula f = unreduced.pollFirst();
      if (f.inSimplestForm()) {
        reduced.add(f);
      } else {
        f.reduce(thetaTypePool, logger, ucc, unreduced);
      }
    }

    boolean isBoundable = true;
    final Set<Bound> bounds = Sets.newLinkedHashSet();
    final Set<InferenceVariable> thrown = Sets.newLinkedHashSet();
    for (ConstraintFormula f : reduced.build()) {
      if (f instanceof ConstConstraintFormula) {
        isBoundable &= ((ConstConstraintFormula) f).boundable;
      } else if (f instanceof Bound) {
        bounds.add((Bound) f);
      } else {
        // TODO might we need to massage something to a capture relation here?
        throw new Error("Cannot incorporate " + f + " into bound set");
      }
    }
    return new BoundSet(bounds, isBoundable, thrown);
  }

  @Override public String toString() {
    return formulae.toString();
  }
}