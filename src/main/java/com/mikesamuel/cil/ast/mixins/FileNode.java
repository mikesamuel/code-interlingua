package com.mikesamuel.cil.ast.mixins;

import com.mikesamuel.cil.ast.BaseNode;
import com.mikesamuel.cil.ast.NodeType;
import com.mikesamuel.cil.ast.NodeVariant;

/**
 * A node that corresponds to an entire file.
 */
public interface FileNode<
    B extends BaseNode<B, T, V>,
    T extends Enum<T> & NodeType<B, T>,
    V extends NodeVariant<B, T>>
  extends ExpressionNameScope<B, T, V>, TypeScope<B, T, V> {
  // Just a common type for roots and pseudo-roots.
}
