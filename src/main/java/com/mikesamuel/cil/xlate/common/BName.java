package com.mikesamuel.cil.xlate.common;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.mikesamuel.cil.ast.meta.Name;

/** A name in the flat namespace. */
final class BName implements Comparable<BName> {
  final Name name;

  private BName(Name name) {
    this.name = Preconditions.checkNotNull(name);
  }

  static BName of(Name name) {
    return new BName(name);
  }

   static final Function<Name, BName> OF = new Function<Name, BName>() {

     @Override
     public BName apply(Name x) {
       return BName.of(x);
     }

   };

  BName parent() {
    return new BName(name.parent);
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
    BName other = (BName) obj;
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
  public int compareTo(BName that) {
    return this.name.compareTo(that.name);
  }
}
