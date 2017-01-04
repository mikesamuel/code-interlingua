package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Modifier;

/**
 * Describes a class member.
 */
public abstract class MemberInfo extends AccessibleInfo {

  MemberInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append('(').append(getClass().getSimpleName());
    String modString = Modifier.toString(modifiers);
    if (!modString.isEmpty()) {
      sb.append(' ').append(modString);
    }
    sb.append(' ').append(canonName);
    appendExtraToString(sb);
    return sb.append(')').toString();
  }

  protected void appendExtraToString(
      @SuppressWarnings("unused") StringBuilder sb) {
    // Overridable
  }
}
