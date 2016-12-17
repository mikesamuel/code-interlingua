package com.mikesamuel.cil.ast.traits;

import com.mikesamuel.cil.ast.meta.ExpressionNameResolver
    .DeclarationPositionMarker;

/**
 * An element in a scope where name declarations are only visible at and after
 * the declaration.
 * <p>
 * For example, in
 * <pre>
 * class C {
 *   int i;  // field
 *   {
 *     ++i;  // increment of field
 *     int i = 0;  // local
 *     ++i;  // increment of local
 *   }
 * }
 */
public interface LimitedScopeElement {
  /**
   * Gets the marker to pass use with a resolver to limit the search to this
   * and earlier block elements.
   */
  DeclarationPositionMarker getDeclarationPositionMarker();
  /**
   * Sets the marker to pass use with a resolver to limit the search to this
   * and earlier block elements.
   */
  void setDeclarationPositionMarker(DeclarationPositionMarker newMarker);
}
