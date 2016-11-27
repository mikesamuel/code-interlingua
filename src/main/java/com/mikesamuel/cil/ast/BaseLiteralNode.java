package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base type for leaf nodes that represent simple tokens like Identifiers and
 * value literals.
 */
public abstract class BaseLiteralNode extends BaseNode {

  BaseLiteralNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
