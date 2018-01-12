package com.mikesamuel.cil.ast.passes.synth;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.j8.J8BaseInnerNode;
import com.mikesamuel.cil.ast.j8.J8BaseNode;
import com.mikesamuel.cil.ast.j8.J8NodeVariant;
import com.mikesamuel.cil.ast.meta.MemberInfo;

final class AdjustableMember {
  final MemberInfo mi;
  final J8BaseInnerNode parent;
  final int index;
  final J8NodeVariant wrapperType;
  private int indexAfter;

  AdjustableMember(
      MemberInfo mi, J8BaseInnerNode parent, int index,
      @Nullable J8NodeVariant wrapperType) {
    this.mi = mi;
    this.parent = parent;
    this.index = index;
    this.wrapperType = wrapperType;
    this.indexAfter = index + 1;
  }

  void insertAfter(J8BaseNode after) {
    parent.add(indexAfter, after);
    ++indexAfter;
  }
}