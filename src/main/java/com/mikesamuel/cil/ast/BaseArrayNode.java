package com.mikesamuel.cil.ast;

/**
 * Base type for nodes that are directly involved in array initialization.
 */
public abstract class BaseArrayNode extends BaseNode {

  BaseArrayNode(
      Iterable<? extends NodeVariant> anonVariants, NodeVariant variant,
      Iterable<? extends BaseNode> children, String value) {
    super(anonVariants, variant, children, value);
  }

}
