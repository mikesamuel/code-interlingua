package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;

/**
 * A scope for
 * {@linkplain com.mikesamuel.cil.ast.j8.TypeParametersNode type parameters}.
 */
public interface TypeParameterScope<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
extends TypeScope<B, T, V> {
  // marker
}
