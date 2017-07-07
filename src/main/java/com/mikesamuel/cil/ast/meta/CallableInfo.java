package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/** Describes a method, constructor, or initializer. */
public final class CallableInfo extends MemberInfo {
  /** Type parameters scoped to the named callable. */
  public final ImmutableList<Name> typeParameters;
  private MethodDescriptor descriptor;
  private TypeSpecification returnType;
  private ImmutableList<TypeSpecification> formalTypes;
  private boolean isVariadic;
  private boolean isSynthetic;
  private boolean isBridge;
  /**
   * True for class and instance initializers but not for constructors
   * or methods.
   */
  public final boolean isInitializer;

  /** */
  public CallableInfo(
      int modifiers, Name canonName, Iterable<? extends Name> typeParameters,
      boolean isInitializer) {
    super(modifiers, canonName);
    Preconditions.checkArgument(canonName.type == Name.Type.METHOD);
    Preconditions.checkArgument(
        !isInitializer
        || Name.isSpecialMethodIdentifier(canonName.identifier));
    for (Name typeParameter : typeParameters) {
      Preconditions.checkArgument(
          typeParameter.type == Name.Type.TYPE_PARAMETER, typeParameter);
    }
    this.typeParameters = ImmutableList.copyOf(typeParameters);
    this.isInitializer = isInitializer;
  }

  /**
   * The JVM method descriptor.
   *
   * @return null if not set.  Usually set by the class member pass.
   */
  public MethodDescriptor getDescriptor() {
    return descriptor;
  }

  /** @see #getDescriptor() */
  public void setDescriptor(MethodDescriptor newDescriptor) {
    this.descriptor = newDescriptor;
  }

  /**
   * The method's return type.  {@code void} for special methods.
   *
   * @return null if not set.  Usually set by the class member pass.
   */
  public TypeSpecification getReturnType() {
    return returnType;
  }

  /** @see #getReturnType() */
  public void setReturnType(TypeSpecification returnType) {
    this.returnType = returnType;
  }

  /**
   * The method's parameter types.  If {@link #isVariadic()} then the last
   * parameter type will be an array type.
   *
   * @return null if not set.  Usually set by the class member pass.
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

  /** @see #isVariadic() */
  public void setVariadic(boolean isVariadic) {
    this.isVariadic = isVariadic;
  }

  /**
   * True iff the method was synthesized to close the gap between the Java
   * language view of method and member accessibility and the JVM view of the
   * same.
   */
  public boolean isSynthetic() {
    return isSynthetic;
  }

  /** @see #isSynthetic() */
  public void setSynthetic(boolean isSynthetic) {
    this.isSynthetic = isSynthetic;
  }

  /**
   * True iff the method was synthesized to override a generic method from
   * a super-type so that calls to the virtual super-type method will reach
   * a method declared in the sub-type that semantically overrides but has a
   * different descriptor because it uses the post-substitution type of a
   * type parameter.
   */
  public boolean isBridge() {
    return isBridge;
  }

  /** @see #isSynthetic() */
  public void setIsBridge(boolean isBridge) {
    this.isBridge = isBridge;
  }

  @Override
  protected void appendExtraToString(StringBuilder sb) {
    if (!typeParameters.isEmpty()) {
      String sep = "<";
      for (Name typeParameter : typeParameters) {
        sb.append(sep);
        sep = ", ";
        sb.append(
            canonName.equals(typeParameter.parent)
            ? typeParameter.identifier : typeParameter);
      }
      sb.append('>');
    }
    if (descriptor != null) {
      sb.append(" @ ").append(descriptor);
    }
    if (returnType != null) {
      sb.append(" : ").append(returnType);
    }
  }

  @Override
  public MemberInfo map(MetadataBridge b) {
    if (b == MetadataBridge.Bridges.IDENTITY) { return this; }
    CallableInfo bridged = new CallableInfo(
        modifiers, b.bridgeDeclaredExpressionName(canonName),
        ImmutableList.copyOf(Lists.transform(
            typeParameters,
            new Function<Name, Name>() {

              @Override
              public Name apply(Name p) {
                return b
                    .bridgeTypeSpecification(
                        TypeSpecification.unparameterized(p))
                    .rawName;
              }

            })),
        isInitializer);
    if (descriptor != null) {
      bridged.setDescriptor(b.bridgeMethodDescriptor(descriptor));
    }
    if (returnType != null) {
      bridged.setReturnType(b.bridgeTypeSpecification(returnType));
    }
    if (formalTypes != null) {
      bridged.setFormalTypes(
          Lists.transform(
              formalTypes,
              new Function<TypeSpecification, TypeSpecification>() {

                @Override
                public TypeSpecification apply(TypeSpecification ft) {
                  return b.bridgeTypeSpecification(ft);
                }

              }));
    }
    bridged.setVariadic(isVariadic);
    bridged.setSynthetic(isSynthetic);
    bridged.setIsBridge(isBridge);

    return bridged;
  }
}
