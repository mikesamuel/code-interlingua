package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;

/**
 * Describes a {@link com.mikesamuel.cil.ast.NodeType#FieldDeclaration}.
 */
public final class FieldInfo extends MemberInfo {
  // TODO: type.

  /** */
  public FieldInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.FIELD);
  }
}
