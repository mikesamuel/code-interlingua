package com.mikesamuel.cil.ast.mixins;

import javax.annotation.Nullable;

import com.google.common.collect.ImmutableList;
import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.MemberInfo;

/**
 * A member of a type other than a nested type.
 */
public interface MemberDeclaration<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {

  /**
   * The member info for the declared members.
   * <p>
   * This is a list because field declarations like
   * {@code private int x, y;} can include multiple
   * members.
   */
  public @Nullable ImmutableList<MemberInfo> getMemberInfo();

  /**
   * Sets the member info for the declared members.
   * @return this
   */
  public MemberDeclaration<B, T, V> setMemberInfo(
      @Nullable ImmutableList<MemberInfo> newMemberInfo);
}
