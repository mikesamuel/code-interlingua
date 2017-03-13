package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.MemberInfo;

/**
 * A member of a type other than a nested type.
 */
public interface MemberDeclaration extends J8Trait {

  /**
   * The member info for the declared member.
   */
  public MemberInfo getMemberInfo();

  /**
   * Sets the member info for the declared member.
   * @return this
   */
  public MemberDeclaration setMemberInfo(MemberInfo newMemberInfo);
}
