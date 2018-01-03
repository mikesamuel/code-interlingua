package com.mikesamuel.cil.ast.passes.flatten;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.meta.Name;

/** A name in the flat namespace. */
final class FName implements Comparable<FName> {
  final Name name;

  static final Function<FName, Name> NAME_OF = new Function<FName, Name>() {

    @Override
    public Name apply(FName x) {
      return x.name;
    }

  };

  private FName(Name name) {
    Preconditions.checkState(
        name.type != Name.Type.CLASS || name.parent.type == Name.Type.PACKAGE );
    this.name = Preconditions.checkNotNull(name);
  }

  static FName of(Name name) {
    return new FName(name);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((name == null) ? 0 : name.hashCode());
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
    FName other = (FName) obj;
    if (name == null) {
      if (other.name != null) {
        return false;
      }
    } else if (!name.equals(other.name)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return name.toString();
  }

  @Override
  public int compareTo(FName that) {
    return this.name.compareTo(that.name);
  }
}
