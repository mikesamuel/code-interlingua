package com.mikesamuel.cil.ast.mixins;

import javax.annotation.Nullable;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
import com.mikesamuel.cil.ast.meta.CallableInfo;

/**
 * Declares a callable which corresponds to a JVM method, static method, or
 * special method.
 */
public interface CallableDeclaration<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends ExpressionNameScope<B, T, V>, SingleMemberDeclaration<B, T, V> {

  /**
   * The method {@linkplain com.mikesamuel.cil.ast.meta.Name#variant variant}
   * if known.
   * <p>
   * By convention, until parameter types can be resolved to nominal types,
   * the method variant is based on the methods ordinal position among
   * methods with the same name declared in the same class.
   */
  int getMethodVariant();

  /**
   * @see #getMethodVariant
   * @return this
   */
  CallableDeclaration<B, T, V> setMethodVariant(int newMethodVariant);

  /**
   * Gets the info for the sole declaration.
   */
  default @Nullable CallableInfo getCallableInfo() {
    return (CallableInfo) getSoleMemberInfo();
  }
}
