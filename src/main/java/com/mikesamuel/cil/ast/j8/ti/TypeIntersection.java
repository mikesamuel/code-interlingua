package com.mikesamuel.cil.ast.j8.ti;

import java.util.Collection;
import java.util.Map;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mikesamuel.cil.ast.meta.StaticType.TypePool.ReferenceType;

final class TypeIntersection extends SyntheticType {
  final ImmutableList<SyntheticType> members;

  static SyntheticType of(Collection<? extends SyntheticType> members) {
    int n = members.size();
    if (n == 1) {
      return members.iterator().next();
    }
    return new TypeIntersection(members);
  }

  private TypeIntersection(Iterable<? extends SyntheticType> members) {
    ImmutableList.Builder<SyntheticType> b = ImmutableList.builder();
    for (SyntheticType m : members) {
      if (m instanceof TypeIntersection) {
        b.addAll(((TypeIntersection) m).members);
      } else {
        b.add(m);
      }
    }
    this.members = b.build();
  }

  @Override
  ImmutableSet<InferenceVariable> mentioned() {
    ImmutableSet.Builder<InferenceVariable> b = ImmutableSet.builder();
    for (SyntheticType member : members) {
      b.addAll(member.mentioned());
    }
    return b.build();
  }

  @Override
  public SyntheticType subst(
      Map<? super InferenceVariable, ? extends ReferenceType> m) {
    boolean changed = false;
    ImmutableSet.Builder<SyntheticType> b = ImmutableSet.builder();
    for (SyntheticType member : members) {
      SyntheticType newMember = member.subst(m);
      changed |= newMember != member;
      b.add(newMember);
    }
    return changed ? of(b.build()) : this;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof TypeIntersection)) { return false; }
    TypeIntersection that = (TypeIntersection) o;
    return this.members.equals(that.members);
  }

  @Override
  public int hashCode() {
    return members.hashCode() ^ 0x08aa7fe4;
  }
}