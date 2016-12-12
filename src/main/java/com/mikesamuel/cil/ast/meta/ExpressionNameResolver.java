package com.mikesamuel.cil.ast.meta;

import com.google.common.base.Optional;

/**
 * Resolves expression names to locals, or fields.
 */
public interface ExpressionNameResolver {
  /**
   * The resolution for the given identifier if any.
   */
  public Optional<Name> resolveName(String ident);
}
