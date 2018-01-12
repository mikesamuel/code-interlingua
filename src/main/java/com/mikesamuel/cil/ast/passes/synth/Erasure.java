package com.mikesamuel.cil.ast.passes.synth;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.meta.CallableInfo;
import com.mikesamuel.cil.ast.meta.MethodDescriptor;
import com.mikesamuel.cil.ast.meta.TypeSpecification;

final class Erasure {
  final ImmutableList<TypeSpecification> erasedFormalTypes;
  final String bareName;

  private Erasure(CallableInfo ci) {
    MethodDescriptor md = ci.getDescriptor();
    this.erasedFormalTypes = md != null ? md.formalTypes : null;
    this.bareName = ci.canonName.identifier;
  }

  static Erasure from(CallableInfo ci) {
    return new Erasure(ci);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + bareName.hashCode();
    result = prime * result
        + ((erasedFormalTypes == null) ? 0 : erasedFormalTypes.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Erasure)) { return false; }
    Erasure that = (Erasure) obj;
    return this.bareName.equals(that.bareName)
        && this.erasedFormalTypes != null
           ? this.erasedFormalTypes.equals(that.erasedFormalTypes)
           : that.erasedFormalTypes == null;
  }

  @Override
  public String toString() {
    return bareName + ":" + erasedFormalTypes;
  }
}