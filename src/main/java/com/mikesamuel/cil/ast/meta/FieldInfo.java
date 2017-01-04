package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;

/**
 * Describes a {@link com.mikesamuel.cil.ast.NodeType#FieldDeclaration}.
 */
public final class FieldInfo extends MemberInfo {
  private TypeSpecification valueType;

  /** */
  public FieldInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.FIELD);
  }

  /**
   * The type of values that may be stored in the field.
   * Null if the class member type has not yet run.
   */
  public TypeSpecification getValueType() {
    return valueType;
  }

  /** */
  public void setValueType(TypeSpecification newValueType) {
    this.valueType = newValueType;
  }

  @Override
  protected void appendExtraToString(StringBuilder sb) {
    if (valueType != null) {
      sb.append(" : ").append(valueType);
    }
  }
}
