package com.mikesamuel.cil.ast.mixins;

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
   * The member info for the declared member.
   */
  public MemberInfo getMemberInfo();

  /**
   * Sets the member info for the declared member.
   * @return this
   */
  public MemberDeclaration<B, T, V> setMemberInfo(MemberInfo newMemberInfo);
}
