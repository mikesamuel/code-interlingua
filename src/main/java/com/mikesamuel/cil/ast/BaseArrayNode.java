package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base type for nodes that are directly involved in array initialization.
 */
public abstract class BaseArrayNode extends BaseNode {

  BaseArrayNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
