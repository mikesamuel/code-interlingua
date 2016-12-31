package com.mikesamuel.cil.ast.meta;

import java.lang.reflect.Modifier;

/**
 * Describes a class member.
 */
public abstract class MemberInfo {
  /** The member modifiers. */
  public final int modifiers;
  /** The canonical name which includes the declaring class's canonical name. */
  public final Name canonName;

  MemberInfo(int modifiers, Name canonName) {
    this.modifiers = modifiers;
    this.canonName = canonName;
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
