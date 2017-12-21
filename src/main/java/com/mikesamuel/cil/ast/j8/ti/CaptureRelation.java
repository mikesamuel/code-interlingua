package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

/**
 * A multivariate relationship between inference variables and bounds.
 * <p>
 * G&lt;&alpha;1, ..., &alpha;n&gt; = capture(G&lt;A1, ..., An&gt;):
 * The variables &lt;1, ..., &gt;n represent the result of capture conversion
 * applied to G&lt;A1, ..., An&gt;
 * (where A1, ..., An may be types or wildcards and may mention inference
 * variables).
 */
final class CaptureRelation implements Bound {

  final ImmutableList<InferenceVariable> alphas;
  final ImmutableList<SyntheticType> right;

  CaptureRelation(
      Iterable<? extends InferenceVariable> alphas,
      Iterable<? extends SyntheticType> right) {
    this.alphas = ImmutableList.copyOf(alphas);
    this.right = ImmutableList.copyOf(right);
    Preconditions.checkArgument(this.alphas.size() == this.right.size());
  }

  @Override
  public String toString() {
    int n = alphas.size();

    StringBuilder sb = new StringBuilder();
    sb.append("G<");
    for (int i = 0; i < n; ++i) {
      if (i != 0) { sb.append(", "); }
      sb.append(alphas.get(i));
    }
    sb.append("> = capture(G<");
    for (int i = 0; i < n; ++i) {
      if (i != 0) { sb.append(", "); }
      sb.append(right.get(i));
    }
    sb.append(">)");
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((alphas == null) ? 0 : alphas.hashCode());
    result = prime * result + ((right == null) ? 0 : right.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    CaptureRelation other = (CaptureRelation) obj;
    if (alphas == null) {
      if (other.alphas != null) {
        return false;
      }
    } else if (!alphas.equals(other.alphas)) {
      return false;
    }
    if (right == null) {
      if (other.right != null) {
        return false;
      }
    } else if (!right.equals(other.right)) {
      return false;
    }
    return true;
  }

  @Override
  public void buildResolutionOrderGraph(ResolutionOrder.Builder b) {
    // An inference variable α appearing on the left-hand side of a bound of
    // the form G<..., α, ...> = capture(G<...>) depends on the resolution of
    // every other inference variable mentioned in this bound (on both sides
    // of the = sign).
    throw new Error("TODO");
  }

  @Override
  public ImmutableSet<InferenceVariable> mentioned() {
    ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
    b.addAll(this.alphas);
    for (SyntheticType t : this.right) {
      b.addAll(t.mentioned());
    }
    return b.build();
  }

  @Override
  public Bound
      subst(Map<? super InferenceVariable, ? extends ReferenceType> m) {
    return new CaptureRelation(
        alphas,
        Iterables.transform(
            right,
            new Function<SyntheticType, SyntheticType>() {
              @Override
              public SyntheticType apply(SyntheticType t) {
                return t.subst(m);
              }
            }));
  }
}