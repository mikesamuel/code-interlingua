package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.meta.TypeNameResolver;

/**
 * Trait for nodes that establish a new scope for {@link TypeDeclaration}s.
 */
public interface TypeScope {
  /**
   * Maps names in the scope to qualified names.
   */
  public TypeNameResolver getTypeNameResolver();

  /**
   * @see #getTypeNameResolver
   */
  public void setTypeNameResolver(TypeNameResolver newNameResolver);
}
