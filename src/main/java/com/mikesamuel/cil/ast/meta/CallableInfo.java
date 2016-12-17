package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;

/** Describes a method, constructor, or initializer. */
public class CallableInfo extends MemberInfo {
  // TODO: parameter types

  /** */
  public CallableInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.METHOD);
  }
}
