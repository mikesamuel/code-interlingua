package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that are directly involved in array initialization.
 */
public abstract class BaseArrayNode extends BaseNode {

  BaseArrayNode(
      NodeType type, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(type, variant, children, value);
  }

}
