package com.mikesamuel.cil.ast.mixins;

import javax.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.MemberInfo;

/**
 * A member declaration that declares a single member.
 * <p>
 * Unlike a declaration that might declare more than one like
 * the following field declaration:
 * {@code public int x, y, z;}.
 * </p>
 */
public interface SingleMemberDeclaration<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends MemberDeclaration<B, T, V> {

  /** The sole member info for this declaration or null if unknown. */
  default @Nullable MemberInfo getSoleMemberInfo() {
    ImmutableList<MemberInfo> memberInfo = getMemberInfo();
    if (memberInfo != null) {
      int nMembers = memberInfo.size();
      if (nMembers != 0) {
        // This should be checked on set.
        Preconditions.checkState(nMembers == 1);
      }
      return memberInfo.get(0);
    }
    return null;
  }

  /** Argument check for {@link #setMemberInfo}. */
  static void checkMemberInfo(ImmutableList<MemberInfo> newMemberInfo) {
    Preconditions.checkArgument(
        newMemberInfo == null
        || newMemberInfo.size() < 2);
  }
}
