package com.mikesamuel.cil.ast.traits;

/**
 * A block-like scope where types can be declared but where forward declarations
 * are not allowed.
 */
public interface TypeScopeNoForward extends TypeScope {
  @Override
  public default boolean allowForwardTypeReferences() {
    return false;
  }
}
