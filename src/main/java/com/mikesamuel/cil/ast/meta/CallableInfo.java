package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

/** Describes a method, constructor, or initializer. */
public final class CallableInfo extends MemberInfo {
  private String descriptor;
  private TypeSpecification returnType;
  private ImmutableList<TypeSpecification> formalTypes;
  private boolean isVariadic;

  /** */
  public CallableInfo(int modifiers, Name canonName) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.METHOD);
  }

  /**
   * The method descriptor.
   *
   * @return null if not set.  Usually set by the class member pass.
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
  public TypeSpecification getReturnType() {
    return returnType;
  }

  /** */
  public void setReturnType(TypeSpecification returnType) {
    this.returnType = returnType;
  }

  /**
   * The method's parameter types.  If {@link #isVariadic()} then the last
   * parameter type will be an array type.
   *
   * @return null if not set.  Usually set by the descriptor pass.
   */
  public ImmutableList<TypeSpecification> getFormalTypes() {
    return formalTypes;
  }

  /** @see #getFormalTypes() */
  public void setFormalTypes(
      Iterable<? extends TypeSpecification> newFormalTypes) {
    this.formalTypes = ImmutableList.copyOf(newFormalTypes);
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

  @Override
  protected void appendExtraToString(StringBuilder sb) {
    if (descriptor != null) {
      sb.append(" @ ").append(descriptor);
    }
    if (returnType != null) {
      sb.append(" : ").append(returnType);
    }
  }

}
