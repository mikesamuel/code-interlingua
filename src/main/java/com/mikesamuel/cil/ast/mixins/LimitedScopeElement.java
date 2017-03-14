package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeI;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;
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
public interface LimitedScopeElement<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends NodeI<B, T, V> {
  /**
   * Gets the marker to pass use with a resolver to limit the search to this
   * and earlier block elements.
   */
  DeclarationPositionMarker getDeclarationPositionMarker();
  /**
   * Sets the marker to pass use with a resolver to limit the search to this
   * and earlier block elements.
   * @return this
   */
  LimitedScopeElement<B, T, V> setDeclarationPositionMarker(
      DeclarationPositionMarker newMarker);
}
