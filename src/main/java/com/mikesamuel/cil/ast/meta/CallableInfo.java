package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** Describes a method, constructor, or initializer. */
public final class CallableInfo extends MemberInfo {
  private String descriptor;
  private StaticType returnType;
  private ImmutableList<StaticType> parameterTypes;
  private boolean isVariadic;

  /** */
  public CallableInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.METHOD);
  }

  /**
   * The method descriptor.
   *
   * @return null if not set.  Usually set by the descriptor pass.
   */
  public String getDescriptor() {
    return descriptor;
  }

  /** */
  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  /**
   * The method's return type.  {@code void} for special methods.
   *
   * @return null if not set.  Usually set by the descriptor pass.
   */
  public StaticType getReturnType() {
    return returnType;
  }

  /** */
  public void setReturnType(StaticType returnType) {
    this.returnType = returnType;
  }

  /**
   * The method's parameter types.  If {@link #isVariadic()} then the last
   * parameter type will be an array type.
   *
   * @return null if not set.  Usually set by the descriptor pass.
   */
  public ImmutableList<StaticType> getParameterTypes() {
    return parameterTypes;
  }

  /** */
  public void setParameterTypes(ImmutableList<StaticType> parameterTypes) {
    this.parameterTypes = parameterTypes;
  }

  /**
   * True iff the method's last formal parameter is a {@code ...} parameter.
   */
  public boolean isVariadic() {
    return isVariadic;
  }

  /** */
  public void setVariadic(boolean isVariadic) {
    this.isVariadic = isVariadic;
  }
}
