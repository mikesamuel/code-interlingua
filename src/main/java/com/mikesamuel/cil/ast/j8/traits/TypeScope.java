package com.mikesamuel.cil.ast.j8.traits;

import com.mikesamuel.cil.ast.meta.TypeNameResolver;

/**
 * Trait for nodes that establish a new scope for {@link TypeDeclaration}s.
 */
public interface TypeScope extends J8Trait {
  /**
   * Maps names in the scope to qualified names.
   */
  public TypeNameResolver getTypeNameResolver();

  /**
   * @see #getTypeNameResolver
   * @return this
   */
  public TypeScope setTypeNameResolver(TypeNameResolver newNameResolver);
}
