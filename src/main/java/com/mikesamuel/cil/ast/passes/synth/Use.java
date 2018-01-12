package com.mikesamuel.cil.ast.passes.synth;

import com.mikesamuel.cil.ast.meta.Name;

final class Use {
  final UseType type;
  final Name used;

  Use(UseType type, Name used) {
    this.type = type;
    this.used = used;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + type.hashCode();
    result = prime * result + used.hashCode();
    return result;
  }
  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Use)) { return false; }
    Use that = (Use) obj;
    return this.type == that.type && this.used.equals(that.used);
  }
  @Override
  public String toString() {
    return "(" + type + " " + used + ")";
  }
}