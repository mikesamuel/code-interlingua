package com.mikesamuel.cil.ast.j8.ti;

import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

final class SimpleBound extends ConstraintFormula implements Bound {
  final SyntheticType left;
  final Operator op;
  final SyntheticType right;

  SimpleBound(SyntheticType left, Operator op, SyntheticType right) {
    this.left = Preconditions.checkNotNull(
        ConstraintFormula.maybeAsInferenceVariable(left));
    this.op = Preconditions.checkNotNull(op);
    this.right = Preconditions.checkNotNull(
        ConstraintFormula.maybeAsInferenceVariable(right));
  }

  @Override
  boolean inSimplestForm() {
    return true;
  }

  @Override
  void reduce(
      TypePool thetaPool, Logger logger, UncheckedConversionCallback ucc,
      Collection<? super ConstraintFormula> out) {
    out.add(this);
  }

  @Override
  public void buildResolutionOrderGraph(ResolutionOrder.Builder b) {
    // Given a bound of one of the following forms, where T is either an
    // inference variable β or a type that mentions β:
    //   * α = T
    //   * α <: T
    //   * T = α
    //   * T <: α
    InferenceVariable a;
    Iterable<InferenceVariable> betas;
    if (left instanceof InferenceVariable) {
      a = (InferenceVariable) left;
      betas = right.mentioned();
    } else if (right instanceof InferenceVariable) {
      a = (InferenceVariable) right;
      betas = left.mentioned();
    } else {
      return;
    }
    // If α appears on the left-hand side of another bound of the form
    // G<..., α, ...> = capture(G<...>), then β depends on the resolution of α.
    // Otherwise, α depends on the resolution of β.
    if (b.unionOfCaptureRelationAlphas.contains(a)) {
      for (InferenceVariable beta : betas) {
        b.mustResolveBeforeOrAtSameTime(a, beta);
        b.boundDependsOn(beta, this);
      }
    } else {
      for (InferenceVariable beta : betas) {
        b.mustResolveBeforeOrAtSameTime(beta, a);
      }
      b.boundInforms(this, a);
    }
  }

  @Override
  public ImmutableSet<InferenceVariable> mentioned() {
    return ImmutableSet.<InferenceVariable>builder()
        .addAll(left.mentioned())
        .addAll(right.mentioned())
        .build();
  }

  @Override
  public SimpleBound subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    SyntheticType newLeft = left.subst(m);
    SyntheticType newRight = right.subst(m);
    return left != newLeft || right != newRight
        ? new SimpleBound(newLeft, op, newRight)
        : this;
  }

  enum Operator {
    /** Corresponds to {@code extends} */
    UPPER("<:"),
    /** Corresponds to {@code super} */
    LT_EQ("<="),
    ;

    final String op;

    Operator(String op) {
      this.op = op;
    }
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((left == null) ? 0 : left.hashCode());
    result = prime * result + ((op == null) ? 0 : op.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SimpleBound)) {
      return false;
    }
    SimpleBound that = (SimpleBound) obj;
    return this.left.equals(that.left)
        && this.op == that.op
        && this.right.equals(that.right);
  }

  @Override
  public String toString() {
    return "<" + left + " " + op.op + " " + right + ">";
  }
}