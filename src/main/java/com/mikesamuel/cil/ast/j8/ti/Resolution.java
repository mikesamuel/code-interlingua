package com.mikesamuel.cil.ast.j8.ti;

import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.j8.ti.ResolutionOrder.Builder;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

final class Resolution implements Bound {
  final InferenceVariable var;
  final ReferenceType resolution;

  Resolution(InferenceVariable var, ReferenceType resolution) {
    this.var = var;
    this.resolution = resolution;
  }


  @Override
  public void buildResolutionOrderGraph(Builder b) {
    // We don't need to order dependencies that are already resolved.
  }


  @Override
  public ImmutableSet<InferenceVariable> mentioned() {
    return ImmutableSet.<InferenceVariable>builder()
        .add(var)
        // resolution must be proper
        .build();
  }

  @Override
  public Resolution subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    // The left must remain an inference variable, and the right must
    // already be a proper type.
    return this;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result
        + ((resolution == null) ? 0 : resolution.hashCode());
    result = prime * result + ((var == null) ? 0 : var.hashCode());
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
    Resolution other = (Resolution) obj;
    if (resolution == null) {
      if (other.resolution != null) {
        return false;
      }
    } else if (!resolution.equals(other.resolution)) {
      return false;
    }
    if (var == null) {
      if (other.var != null) {
        return false;
      }
    } else if (!var.equals(other.var)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "<" + var + " = " + resolution + ">";
  }
}
