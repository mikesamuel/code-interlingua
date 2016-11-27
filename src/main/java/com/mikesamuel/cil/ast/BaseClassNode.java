package com.mikesamuel.cil.ast;

import javax.annotation.Nullable;

/**
 * Base class for nodes that are directly involved in a
 * {@code class} declaration.
 */
public abstract class BaseClassNode extends BaseNode {

  BaseClassNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, @Nullable String value) {
    super(variant, children, value);
  }

}
