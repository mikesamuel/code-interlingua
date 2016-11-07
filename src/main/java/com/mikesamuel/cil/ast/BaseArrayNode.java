package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that are directly involved in array initialization.
 */
public abstract class BaseArrayNode extends BaseNode {

  BaseArrayNode(
      NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(variant, children, value);
  }

}
